package com.example.extrato.service;

import com.example.extrato.client.FuturosClient;
import com.example.extrato.client.RecentesClient;
import com.example.extrato.dto.request.Aba;
import com.example.extrato.dto.request.EntradaSaida;
import com.example.extrato.dto.request.ExtratoFiltrosRequest;
import com.example.extrato.dto.request.Periodo;
import com.example.extrato.dto.request.TipoLancamento;
import com.example.extrato.dto.response.ErroResponse;
import com.example.extrato.dto.response.ExtratoFiltrosResponse;
import com.example.extrato.dto.upstream.FuturosDataUpstream;
import com.example.extrato.dto.upstream.FuturosUpstreamResponse;
import com.example.extrato.dto.upstream.PaginacaoUpstreamDto;
import com.example.extrato.dto.upstream.RecentesUpstreamResponse;
import com.example.extrato.mapper.FiltrosMapper;
import com.example.extrato.mapper.FuturosMapper;
import com.example.extrato.mapper.RecentesMapper;
import com.example.extrato.util.CurrencyFormatter;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtratoFiltrosServiceTest {

    @Mock private RecentesClient recentesClient;
    @Mock private FuturosClient futurosClient;
    @Mock private CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    private ExtratoFiltrosService service;

    private static final PaginacaoUpstreamDto PAGINACAO_UPSTREAM =
            new PaginacaoUpstreamDto(1, 5, 47);
    private static final RecentesUpstreamResponse RECENTES_VAZIO =
            new RecentesUpstreamResponse(List.of(), null);
    private static final RecentesUpstreamResponse RECENTES_COM_PAGINACAO =
            new RecentesUpstreamResponse(List.of(), PAGINACAO_UPSTREAM);
    private static final FuturosUpstreamResponse FUTUROS_VAZIO =
            new FuturosUpstreamResponse(new FuturosDataUpstream(List.of(), null));
    private static final FuturosUpstreamResponse FUTUROS_COM_PAGINACAO =
            new FuturosUpstreamResponse(new FuturosDataUpstream(List.of(), PAGINACAO_UPSTREAM));

    /** Circuit breaker que delega direto ao supplier (circuito CLOSED) */
    @SuppressWarnings("unchecked")
    private static final CircuitBreaker CB_FECHADO = (supplier, fallback) -> supplier.get();

    /** Circuit breaker que chama o fallback com uma exceção (circuito OPEN) */
    @SuppressWarnings("unchecked")
    private static final CircuitBreaker CB_ABERTO =
            (supplier, fallback) -> fallback.apply(new RuntimeException("circuit breaker aberto"));

    @BeforeEach
    void setUp() {
        CurrencyFormatter currencyFormatter = new CurrencyFormatter();
        service = new ExtratoFiltrosService(
                recentesClient, futurosClient,
                new RecentesMapper(currencyFormatter), new FuturosMapper(currencyFormatter),
                new FiltrosMapper(), currencyFormatter, circuitBreakerFactory);
    }

    private ExtratoFiltrosRequest request(Aba aba) {
        return new ExtratoFiltrosRequest(
                Periodo.TRINTA_DIAS, null, null,
                EntradaSaida.ENTRADA_SAIDA, TipoLancamento.D, aba, 1);
    }

    // ─── Cenários normais (circuit breakers CLOSED) ──────────────────────────

    @Test
    void semAba_deveRetornarDuasAbas() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_FECHADO);
        when(circuitBreakerFactory.create(eq("futuros"))).thenReturn(CB_FECHADO);
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(RECENTES_VAZIO);
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(null));

        assertThat(response.data().ordemAbas()).containsExactly("RECENTES", "FUTUROS");
        assertThat(response.data().abas()).containsKeys("RECENTES", "FUTUROS");
        assertThat(response.erro()).isNull();
    }

    @Test
    void semAba_paginacaoDeveEstarDentroDeCartaAba_naoNaRaiz() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_FECHADO);
        when(circuitBreakerFactory.create(eq("futuros"))).thenReturn(CB_FECHADO);
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(RECENTES_COM_PAGINACAO);
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_COM_PAGINACAO);

        ExtratoFiltrosResponse response = service.buscar(request(null));

        assertThat(response.paginacao()).isNull();
        assertThat(response.data().abas().get("RECENTES").paginacao()).isNotNull();
        assertThat(response.data().abas().get("RECENTES").paginacao().paginaAtual()).isEqualTo(1);
        assertThat(response.data().abas().get("RECENTES").paginacao().totalRegistros()).isEqualTo(47);
        assertThat(response.data().abas().get("RECENTES").paginacao().totalPaginas()).isEqualTo(5);
        assertThat(response.data().abas().get("RECENTES").paginacao().tamanhoPagina()).isEqualTo(10);
        assertThat(response.data().abas().get("FUTUROS").paginacao()).isNotNull();
    }

    @Test
    void semAba_upstreamSemPaginacao_paginacaoDeveSerNull() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_FECHADO);
        when(circuitBreakerFactory.create(eq("futuros"))).thenReturn(CB_FECHADO);
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(RECENTES_VAZIO);
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(null));

        assertThat(response.paginacao()).isNull();
        assertThat(response.data().abas().get("RECENTES").paginacao()).isNull();
        assertThat(response.data().abas().get("FUTUROS").paginacao()).isNull();
    }

    @Test
    void abaRecentes_deveChamarApenasRecentesERetornarUmaAba() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_FECHADO);
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(RECENTES_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(Aba.RECENTES));

        assertThat(response.data().ordemAbas()).containsExactly("RECENTES");
        assertThat(response.data().abas()).containsOnlyKeys("RECENTES");
        assertThat(response.erro()).isNull();
        verify(futurosClient, never()).buscar(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void abaRecentes_paginacaoDeveEstarNaRaiz_naoNaAba() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_FECHADO);
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(RECENTES_COM_PAGINACAO);

        ExtratoFiltrosResponse response = service.buscar(request(Aba.RECENTES));

        assertThat(response.paginacao()).isNotNull();
        assertThat(response.paginacao().paginaAtual()).isEqualTo(1);
        assertThat(response.paginacao().totalRegistros()).isEqualTo(47);
        assertThat(response.paginacao().tamanhoPagina()).isEqualTo(10);
        assertThat(response.data().abas().get("RECENTES").paginacao()).isNull();
    }

    @Test
    void abaFuturos_deveChamarApenasFuturosERetornarUmaAba() {
        when(circuitBreakerFactory.create(eq("futuros"))).thenReturn(CB_FECHADO);
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(Aba.FUTUROS));

        assertThat(response.data().ordemAbas()).containsExactly("FUTUROS");
        assertThat(response.data().abas()).containsOnlyKeys("FUTUROS");
        assertThat(response.erro()).isNull();
        verify(recentesClient, never()).buscar(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void tamanhoPaginaSempreDeveSerDezIndependenteDoUpstream() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_FECHADO);
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(RECENTES_COM_PAGINACAO);

        ExtratoFiltrosResponse response = service.buscar(request(Aba.RECENTES));

        assertThat(response.paginacao().tamanhoPagina()).isEqualTo(10);
    }

    // ─── Cenários de circuit breaker OPEN (T021–T023) ────────────────────────

    @Test
    void semAba_circuitBreakerRecentesAberto_deveRetornarAbaRecentesVaziaComErro() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_ABERTO);
        when(circuitBreakerFactory.create(eq("futuros"))).thenReturn(CB_FECHADO);
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(null));

        assertThat(response.data().ordemAbas()).containsExactly("RECENTES", "FUTUROS");
        assertThat(response.data().abas().get("RECENTES").dados()).isEmpty();
        assertThat(response.data().abas().get("RECENTES").filtros()).isEmpty();
        assertThat(response.data().abas().get("FUTUROS").dados()).isNotNull();
        assertThat(response.erro()).isNotNull();
        assertThat(response.erro().codigo()).isEqualTo(ErroResponse.UPSTREAM_PARCIALMENTE_INDISPONIVEL);
        verify(recentesClient, never()).buscar(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void semAba_ambosCircuitBreakersAbertos_deveRetornarTodasAbasVaziasComErro() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_ABERTO);
        when(circuitBreakerFactory.create(eq("futuros"))).thenReturn(CB_ABERTO);

        ExtratoFiltrosResponse response = service.buscar(request(null));

        assertThat(response.data().abas().get("RECENTES").dados()).isEmpty();
        assertThat(response.data().abas().get("RECENTES").filtros()).isEmpty();
        assertThat(response.data().abas().get("FUTUROS").dados()).isEmpty();
        assertThat(response.data().abas().get("FUTUROS").filtros()).isEmpty();
        assertThat(response.erro()).isNotNull();
        assertThat(response.erro().codigo()).isEqualTo(ErroResponse.UPSTREAM_INDISPONIVEL);
        verify(recentesClient, never()).buscar(any(), any(), any(), anyInt(), anyInt());
        verify(futurosClient, never()).buscar(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void abaUnica_circuitBreakerAberto_deveRetornarAbaVaziaComErroIndisponivel() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_ABERTO);

        ExtratoFiltrosResponse response = service.buscar(request(Aba.RECENTES));

        assertThat(response.data().abas().get("RECENTES").dados()).isEmpty();
        assertThat(response.data().abas().get("RECENTES").filtros()).isEmpty();
        assertThat(response.erro()).isNotNull();
        assertThat(response.erro().codigo()).isEqualTo(ErroResponse.UPSTREAM_INDISPONIVEL);
        verify(recentesClient, never()).buscar(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void falhaUpstream_feignException_deveRetornarFallbackComErro() {
        when(circuitBreakerFactory.create(eq("recentes"))).thenReturn(CB_FECHADO);
        when(circuitBreakerFactory.create(eq("futuros"))).thenReturn(CB_FECHADO);
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(mock(FeignException.class));
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(null));

        assertThat(response.data().abas().get("RECENTES").dados()).isEmpty();
        assertThat(response.erro()).isNotNull();
        assertThat(response.erro().codigo()).isEqualTo(ErroResponse.UPSTREAM_PARCIALMENTE_INDISPONIVEL);
    }
}
