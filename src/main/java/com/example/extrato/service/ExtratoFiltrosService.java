package com.example.extrato.service;

import com.example.extrato.client.FuturosClient;
import com.example.extrato.client.RecentesClient;
import com.example.extrato.dto.request.Aba;
import com.example.extrato.dto.request.ExtratoFiltrosRequest;
import com.example.extrato.dto.request.Periodo;
import com.example.extrato.dto.response.AbaResponse;
import com.example.extrato.dto.response.CabecalhoResponse;
import com.example.extrato.dto.response.ErroResponse;
import com.example.extrato.dto.response.ExtratoFiltrosData;
import com.example.extrato.dto.response.ExtratoFiltrosResponse;
import com.example.extrato.dto.response.FiltroResponse;
import com.example.extrato.dto.response.LancamentoResponse;
import com.example.extrato.dto.response.PaginacaoResponse;
import com.example.extrato.dto.upstream.FuturosUpstreamResponse;
import com.example.extrato.dto.upstream.PaginacaoUpstreamDto;
import com.example.extrato.dto.upstream.RecentesUpstreamResponse;
import com.example.extrato.mapper.FiltrosMapper;
import com.example.extrato.mapper.FuturosMapper;
import com.example.extrato.mapper.RecentesMapper;
import com.example.extrato.util.CurrencyFormatter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

@Service
public class ExtratoFiltrosService {

    private static final Logger log = LoggerFactory.getLogger(ExtratoFiltrosService.class);

    private static final int TAMANHO_PAGINA = 10;
    private static final String ABA_RECENTES = Aba.RECENTES.name();
    private static final String ABA_FUTUROS = Aba.FUTUROS.name();

    private final RecentesClient recentesClient;
    private final FuturosClient futurosClient;
    private final RecentesMapper recentesMapper;
    private final FuturosMapper futurosMapper;
    private final FiltrosMapper filtrosMapper;
    private final CurrencyFormatter currencyFormatter;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public ExtratoFiltrosService(RecentesClient recentesClient, FuturosClient futurosClient,
                                  RecentesMapper recentesMapper, FuturosMapper futurosMapper,
                                  FiltrosMapper filtrosMapper, CurrencyFormatter currencyFormatter,
                                  CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.recentesClient = recentesClient;
        this.futurosClient = futurosClient;
        this.recentesMapper = recentesMapper;
        this.futurosMapper = futurosMapper;
        this.filtrosMapper = filtrosMapper;
        this.currencyFormatter = currencyFormatter;
        this.circuitBreakerFactory = circuitBreakerFactory;
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
                    () -> buscarRecentesComCircuitBreaker(params, filtros), executor);
            var futurosFuture = CompletableFuture.supplyAsync(
                    () -> buscarFuturosComCircuitBreaker(params, filtros), executor);

            try {
                CompletableFuture.allOf(recentesFuture, futurosFuture).join();
            } catch (CompletionException e) {
                throw new RuntimeException("Falha inesperada na chamada upstream", e.getCause());
            }

            AbaResult recentesResult = recentesFuture.join();
            AbaResult futurosResult = futurosFuture.join();

            ErroResponse erro = resolverErroAmbasAbas(recentesResult.falhou(), futurosResult.falhou());

            ExtratoFiltrosData data = new ExtratoFiltrosData(
                    List.of(ABA_RECENTES, ABA_FUTUROS),
                    Map.of(ABA_RECENTES, recentesResult.aba(), ABA_FUTUROS, futurosResult.aba()));
            return new ExtratoFiltrosResponse(data, null, erro);
        }
    }

    private ExtratoFiltrosResponse buscarAbaEspecifica(ExtratoFiltrosRequest request,
                                                        List<FiltroResponse> filtros) {
        UpstreamParams params = UpstreamParams.from(request);

        if (request.aba() == Aba.RECENTES) {
            AbaResult result = buscarRecentesComCircuitBreaker(params, filtros);
            PaginacaoResponse paginacao = result.falhou() ? null
                    : buildPaginacao(result.recentesUpstream() != null ? result.recentesUpstream().paginacao() : null);
            ErroResponse erro = result.falhou() ? ErroResponse.indisponivel() : null;
            return new ExtratoFiltrosResponse(
                    new ExtratoFiltrosData(List.of(ABA_RECENTES), Map.of(ABA_RECENTES, result.aba())),
                    paginacao, erro);
        } else {
            AbaResult result = buscarFuturosComCircuitBreaker(params, filtros);
            PaginacaoUpstreamDto upstreamPag = (!result.falhou() && result.futurosUpstream() != null
                    && result.futurosUpstream().data() != null)
                    ? result.futurosUpstream().data().paginacao() : null;
            ErroResponse erro = result.falhou() ? ErroResponse.indisponivel() : null;
            return new ExtratoFiltrosResponse(
                    new ExtratoFiltrosData(List.of(ABA_FUTUROS), Map.of(ABA_FUTUROS, result.aba())),
                    buildPaginacao(upstreamPag), erro);
        }
    }

    private AbaResult buscarRecentesComCircuitBreaker(UpstreamParams params, List<FiltroResponse> filtros) {
        try {
            RecentesUpstreamResponse upstream = circuitBreakerFactory.create("recentes").run(
                    () -> recentesClient.buscar(params.periodo(), params.entradaSaida(),
                            params.lancamento(), params.pagina(), TAMANHO_PAGINA),
                    throwable -> { throw new CircuitAbertaException(throwable); }
            );
            AbaResponse aba = buildAbaRecentes(filtros, upstream, buildPaginacao(upstream.paginacao()));
            return AbaResult.sucesso(aba, upstream, null);
        } catch (CircuitAbertaException | CallNotPermittedException e) {
            log.warn("Circuit breaker aberto para upstream recentes: {}", e.getMessage());
            return AbaResult.fallback(buildAbaFallback(filtros));
        }
    }

    private AbaResult buscarFuturosComCircuitBreaker(UpstreamParams params, List<FiltroResponse> filtros) {
        try {
            FuturosUpstreamResponse upstream = circuitBreakerFactory.create("futuros").run(
                    () -> futurosClient.buscar(params.periodo(), params.entradaSaida(),
                            params.lancamento(), params.pagina(), TAMANHO_PAGINA),
                    throwable -> { throw new CircuitAbertaException(throwable); }
            );
            PaginacaoUpstreamDto pag = upstream.data() != null ? upstream.data().paginacao() : null;
            AbaResponse aba = buildAbaFuturos(filtros, upstream, buildPaginacao(pag));
            return AbaResult.sucesso(aba, null, upstream);
        } catch (CircuitAbertaException | CallNotPermittedException e) {
            log.warn("Circuit breaker aberto para upstream futuros: {}", e.getMessage());
            return AbaResult.fallback(buildAbaFallback(filtros));
        }
    }

    private AbaResponse buildAbaFallback(List<FiltroResponse> filtros) {
        String zero = currencyFormatter.format(BigDecimal.ZERO);
        return new AbaResponse(List.of(), new CabecalhoResponse(zero, zero, zero), List.of(), null);
    }

    private ErroResponse resolverErroAmbasAbas(boolean recentesFalhou, boolean futurosFalhou) {
        if (recentesFalhou && futurosFalhou) return ErroResponse.indisponivel();
        if (recentesFalhou || futurosFalhou) return ErroResponse.parcialmenteIndisponivel();
        return null;
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

    private record AbaResult(AbaResponse aba, boolean falhou,
                              RecentesUpstreamResponse recentesUpstream,
                              FuturosUpstreamResponse futurosUpstream) {
        static AbaResult sucesso(AbaResponse aba, RecentesUpstreamResponse r, FuturosUpstreamResponse f) {
            return new AbaResult(aba, false, r, f);
        }
        static AbaResult fallback(AbaResponse aba) {
            return new AbaResult(aba, true, null, null);
        }
    }

    private static class CircuitAbertaException extends RuntimeException {
        CircuitAbertaException(Throwable cause) {
            super(cause != null ? cause.getMessage() : "circuit breaker aberto", cause);
        }
    }
}
