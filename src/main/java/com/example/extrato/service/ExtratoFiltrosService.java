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
            CompletableFuture<RecentesUpstreamResponse> recentesFuture =
                    CompletableFuture.supplyAsync(
                            () -> recentesClient.buscar(periodo, entradaSaida, lancamento, pagina, TAMANHO_PAGINA),
                            executor);
            CompletableFuture<FuturosUpstreamResponse> futurosFuture =
                    CompletableFuture.supplyAsync(
                            () -> futurosClient.buscar(periodo, entradaSaida, lancamento, pagina, TAMANHO_PAGINA),
                            executor);

            try {
                CompletableFuture.allOf(recentesFuture, futurosFuture).join();
            } catch (CompletionException e) {
                throw new UpstreamException("Falha na chamada upstream", e.getCause());
            }

            RecentesUpstreamResponse recentes = recentesFuture.join();
            FuturosUpstreamResponse futuros = futurosFuture.join();

            List<String> ordemAbas = List.of("RECENTES", "FUTUROS");
            Map<String, AbaResponse> abas = Map.of(
                    "RECENTES", buildAbaRecentes(filtros, recentes),
                    "FUTUROS", buildAbaFuturos(filtros, futuros)
            );
            PaginacaoResponse paginacao = buildPaginacao(pagina, recentes.totalItens(), recentes.totalPaginas());
            return new ExtratoFiltrosResponse(new ExtratoFiltrosData(ordemAbas, abas), paginacao);
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
                Map<String, AbaResponse> abas = Map.of("RECENTES", buildAbaRecentes(filtros, recentes));
                PaginacaoResponse paginacao = buildPaginacao(pagina, recentes.totalItens(), recentes.totalPaginas());
                return new ExtratoFiltrosResponse(new ExtratoFiltrosData(List.of("RECENTES"), abas), paginacao);
            } else {
                FuturosUpstreamResponse futuros =
                        futurosClient.buscar(periodo, entradaSaida, lancamento, pagina, TAMANHO_PAGINA);
                Map<String, AbaResponse> abas = Map.of("FUTUROS", buildAbaFuturos(filtros, futuros));
                Integer totalItens = futuros.data() != null ? futuros.data().totalItens() : null;
                Integer totalPaginas = futuros.data() != null ? futuros.data().totalPaginas() : null;
                PaginacaoResponse paginacao = buildPaginacao(pagina, totalItens, totalPaginas);
                return new ExtratoFiltrosResponse(new ExtratoFiltrosData(List.of("FUTUROS"), abas), paginacao);
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

    private AbaResponse buildAbaRecentes(List<FiltroResponse> filtros, RecentesUpstreamResponse recentes) {
        List<LancamentoResponse> dados = recentesMapper.toResponseList(
                recentes.data() != null ? recentes.data() : List.of());
        return new AbaResponse(filtros, calcularCabecalho(dados), dados);
    }

    private AbaResponse buildAbaFuturos(List<FiltroResponse> filtros, FuturosUpstreamResponse futuros) {
        List<LancamentoResponse> dados = futurosMapper.toResponseList(
                futuros.data() != null && futuros.data().lancamentosFuturos() != null
                        ? futuros.data().lancamentosFuturos() : List.of());
        return new AbaResponse(filtros, calcularCabecalho(dados), dados);
    }

    private PaginacaoResponse buildPaginacao(int pagina, Integer totalItens, Integer totalPaginas) {
        return new PaginacaoResponse(pagina, TAMANHO_PAGINA, totalItens, totalPaginas);
    }

    private CabecalhoResponse calcularCabecalho(List<LancamentoResponse> lancamentos) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(PT_BR);
        BigDecimal zero = BigDecimal.ZERO;
        return new CabecalhoResponse(fmt.format(zero), fmt.format(zero), fmt.format(zero));
    }
}
