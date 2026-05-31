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
import com.example.extrato.util.CurrencyFormatter;
import feign.FeignException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

@Service
public class ExtratoFiltrosService {

    private static final int TAMANHO_PAGINA = 10;
    private static final String ABA_RECENTES = Aba.RECENTES.name();
    private static final String ABA_FUTUROS = Aba.FUTUROS.name();

    private final RecentesClient recentesClient;
    private final FuturosClient futurosClient;
    private final RecentesMapper recentesMapper;
    private final FuturosMapper futurosMapper;
    private final FiltrosMapper filtrosMapper;
    private final CurrencyFormatter currencyFormatter;

    public ExtratoFiltrosService(RecentesClient recentesClient, FuturosClient futurosClient,
                                  RecentesMapper recentesMapper, FuturosMapper futurosMapper,
                                  FiltrosMapper filtrosMapper, CurrencyFormatter currencyFormatter) {
        this.recentesClient = recentesClient;
        this.futurosClient = futurosClient;
        this.recentesMapper = recentesMapper;
        this.futurosMapper = futurosMapper;
        this.filtrosMapper = filtrosMapper;
        this.currencyFormatter = currencyFormatter;
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
        UpstreamParams params = UpstreamParams.from(request);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var recentesFuture = CompletableFuture.supplyAsync(
                    () -> recentesClient.buscar(params.periodo(), params.entradaSaida(),
                            params.lancamento(), params.pagina(), TAMANHO_PAGINA), executor);
            var futurosFuture = CompletableFuture.supplyAsync(
                    () -> futurosClient.buscar(params.periodo(), params.entradaSaida(),
                            params.lancamento(), params.pagina(), TAMANHO_PAGINA), executor);

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
                    List.of(ABA_RECENTES, ABA_FUTUROS),
                    Map.of(ABA_RECENTES, abaRecentes, ABA_FUTUROS, abaFuturos));
            return new ExtratoFiltrosResponse(data, null);
        }
    }

    private ExtratoFiltrosResponse buscarAbaEspecifica(ExtratoFiltrosRequest request,
                                                        List<FiltroResponse> filtros) {
        UpstreamParams params = UpstreamParams.from(request);

        try {
            if (request.aba() == Aba.RECENTES) {
                RecentesUpstreamResponse recentes =
                        recentesClient.buscar(params.periodo(), params.entradaSaida(),
                                params.lancamento(), params.pagina(), TAMANHO_PAGINA);
                AbaResponse aba = buildAbaRecentes(filtros, recentes, null);
                PaginacaoResponse paginacao = buildPaginacao(recentes.paginacao());
                return new ExtratoFiltrosResponse(
                        new ExtratoFiltrosData(List.of(ABA_RECENTES), Map.of(ABA_RECENTES, aba)),
                        paginacao);
            } else {
                FuturosUpstreamResponse futuros =
                        futurosClient.buscar(params.periodo(), params.entradaSaida(),
                                params.lancamento(), params.pagina(), TAMANHO_PAGINA);
                AbaResponse aba = buildAbaFuturos(filtros, futuros, null);
                PaginacaoUpstreamDto upstreamPag = futuros.data() != null ? futuros.data().paginacao() : null;
                return new ExtratoFiltrosResponse(
                        new ExtratoFiltrosData(List.of(ABA_FUTUROS), Map.of(ABA_FUTUROS, aba)),
                        buildPaginacao(upstreamPag));
            }
        } catch (FeignException e) {
            throw new UpstreamException("Falha na chamada upstream", e);
        }
    }

    private void validarParametros(ExtratoFiltrosRequest request) {
        if (request.pagina() < 1) {
            throw new IllegalArgumentException("O parametro pagina deve ser >= 1.");
        }
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
        String zero = currencyFormatter.format(BigDecimal.ZERO);
        return new CabecalhoResponse(zero, zero, zero);
    }

    private record UpstreamParams(String periodo, String entradaSaida, String lancamento, int pagina) {
        static UpstreamParams from(ExtratoFiltrosRequest r) {
            return new UpstreamParams(r.periodo().id, r.entradaSaida().name(),
                    r.lancamento().name(), r.pagina());
        }
    }
}
