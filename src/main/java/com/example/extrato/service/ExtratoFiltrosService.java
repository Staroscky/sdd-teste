package com.example.extrato.service;

import com.example.extrato.client.FuturosClient;
import com.example.extrato.client.RecentesClient;
import com.example.extrato.dto.request.Aba;
import com.example.extrato.dto.request.ExtratoFiltrosRequest;
import com.example.extrato.dto.request.Periodo;
import com.example.extrato.dto.response.AbaResponse;
import com.example.extrato.dto.response.CabecalhoResponse;
import com.example.extrato.dto.response.ExtratoFiltrosData;
import com.example.extrato.dto.response.ExtratoFiltrosResponse;
import com.example.extrato.dto.response.FiltroResponse;
import com.example.extrato.dto.response.LancamentoResponse;
import com.example.extrato.dto.response.PaginacaoResponse;
import com.example.extrato.dto.upstream.FuturosUpstreamResponse;
import com.example.extrato.dto.upstream.PaginacaoUpstreamDto;
import com.example.extrato.dto.upstream.RecentesUpstreamResponse;
import com.example.extrato.exception.UpstreamException;
import com.example.extrato.mapper.FiltrosMapper;
import com.example.extrato.mapper.FuturosMapper;
import com.example.extrato.mapper.RecentesMapper;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

@Service
public class ExtratoFiltrosService {

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final int TAMANHO_PAGINA = 10;

    private final RecentesClient recentesClient;
    private final FuturosClient futurosClient;
    private final RecentesMapper recentesMapper;
    private final FuturosMapper futurosMapper;
    private final FiltrosMapper filtrosMapper;

    public ExtratoFiltrosService(RecentesClient recentesClient, FuturosClient futurosClient,
                                  RecentesMapper recentesMapper, FuturosMapper futurosMapper,
                                  FiltrosMapper filtrosMapper) {
        this.recentesClient = recentesClient;
        this.futurosClient = futurosClient;
        this.recentesMapper = recentesMapper;
        this.futurosMapper = futurosMapper;
        this.filtrosMapper = filtrosMapper;
    }

    public ExtratoFiltrosResponse buscar(ExtratoFiltrosRequest request) {
        validarParametros(request);
        List<FiltroResponse> filtros = filtrosMapper.mapear(request);

        if (request.aba() == null) {
            return buscarAmbasAbas(request, filtros);
        }
        return buscarAbaEspecifica(request, filtros);
    }

    private ExtratoFiltrosResponse buscarAmbasAbas(ExtratoFiltrosRequest request,
                                                    List<FiltroResponse> filtros) {
        String periodo = request.periodo().id;
        String entradaSaida = request.entradaSaida().name();
        String lancamento = request.lancamento().name();
        int pagina = request.pagina();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var recentesFuture = CompletableFuture.supplyAsync(
                    () -> recentesClient.buscar(periodo, entradaSaida, lancamento, pagina, TAMANHO_PAGINA),
                    executor);
            var futurosFuture = CompletableFuture.supplyAsync(
                    () -> futurosClient.buscar(periodo, entradaSaida, lancamento, pagina, TAMANHO_PAGINA),
                    executor);

            try {
                CompletableFuture.allOf(recentesFuture, futurosFuture).join();
            } catch (CompletionException e) {
                throw new UpstreamException("Falha na chamada upstream", e.getCause());
            }

            RecentesUpstreamResponse recentes = recentesFuture.join();
            FuturosUpstreamResponse futuros = futurosFuture.join();

            AbaResponse abaRecentes = buildAbaRecentes(filtros, recentes,
                    buildPaginacao(recentes.paginacao()));
            AbaResponse abaFuturos = buildAbaFuturos(filtros, futuros,
                    buildPaginacao(futuros.data() != null ? futuros.data().paginacao() : null));

            ExtratoFiltrosData data = new ExtratoFiltrosData(
                    List.of("RECENTES", "FUTUROS"),
                    Map.of("RECENTES", abaRecentes, "FUTUROS", abaFuturos));
            return new ExtratoFiltrosResponse(data, null);
        }
    }

    private ExtratoFiltrosResponse buscarAbaEspecifica(ExtratoFiltrosRequest request,
                                                        List<FiltroResponse> filtros) {
        String periodo = request.periodo().id;
        String entradaSaida = request.entradaSaida().name();
        String lancamento = request.lancamento().name();
        int pagina = request.pagina();

        try {
            if (request.aba() == Aba.RECENTES) {
                RecentesUpstreamResponse recentes =
                        recentesClient.buscar(periodo, entradaSaida, lancamento, pagina, TAMANHO_PAGINA);
                AbaResponse aba = buildAbaRecentes(filtros, recentes, null);
                PaginacaoResponse paginacao = buildPaginacao(recentes.paginacao());
                return new ExtratoFiltrosResponse(
                        new ExtratoFiltrosData(List.of("RECENTES"), Map.of("RECENTES", aba)),
                        paginacao);
            } else {
                FuturosUpstreamResponse futuros =
                        futurosClient.buscar(periodo, entradaSaida, lancamento, pagina, TAMANHO_PAGINA);
                AbaResponse aba = buildAbaFuturos(filtros, futuros, null);
                PaginacaoUpstreamDto upstreamPag = futuros.data() != null ? futuros.data().paginacao() : null;
                return new ExtratoFiltrosResponse(
                        new ExtratoFiltrosData(List.of("FUTUROS"), Map.of("FUTUROS", aba)),
                        buildPaginacao(upstreamPag));
            }
        } catch (FeignException e) {
            throw new UpstreamException("Falha na chamada upstream", e);
        }
    }

    private void validarParametros(ExtratoFiltrosRequest request) {
        if (request.periodo() == Periodo.PERSONALIZADO
                && (request.dataInicial() == null || request.dataFinal() == null)) {
            throw new IllegalArgumentException(
                    "Os campos data_inicial e data_final sao obrigatorios quando periodo=PERSONALIZADO.");
        }
    }

    private AbaResponse buildAbaRecentes(List<FiltroResponse> filtros,
                                          RecentesUpstreamResponse recentes,
                                          PaginacaoResponse paginacao) {
        List<LancamentoResponse> dados = recentesMapper.toResponseList(
                recentes.data() != null ? recentes.data() : List.of());
        return new AbaResponse(filtros, calcularCabecalho(dados), dados, paginacao);
    }

    private AbaResponse buildAbaFuturos(List<FiltroResponse> filtros,
                                         FuturosUpstreamResponse futuros,
                                         PaginacaoResponse paginacao) {
        List<LancamentoResponse> dados = futurosMapper.toResponseList(
                futuros.data() != null && futuros.data().lancamentosFuturos() != null
                        ? futuros.data().lancamentosFuturos() : List.of());
        return new AbaResponse(filtros, calcularCabecalho(dados), dados, paginacao);
    }

    private PaginacaoResponse buildPaginacao(PaginacaoUpstreamDto upstream) {
        if (upstream == null) return null;
        return new PaginacaoResponse(upstream.pagina(), TAMANHO_PAGINA,
                upstream.totalRegistros(), upstream.totalPaginas());
    }

    private CabecalhoResponse calcularCabecalho(List<LancamentoResponse> lancamentos) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(PT_BR);
        BigDecimal zero = BigDecimal.ZERO;
        return new CabecalhoResponse(fmt.format(zero), fmt.format(zero), fmt.format(zero));
    }
}
