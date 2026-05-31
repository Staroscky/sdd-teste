package com.example.extrato.service;

import com.example.extrato.client.FuturosClient;
import com.example.extrato.client.RecentesClient;
import com.example.extrato.dto.request.Aba;
import com.example.extrato.dto.request.EntradaSaida;
import com.example.extrato.dto.request.ExtratoFiltrosRequest;
import com.example.extrato.dto.request.Periodo;
import com.example.extrato.dto.request.TipoLancamento;
import com.example.extrato.dto.response.ExtratoFiltrosResponse;
import com.example.extrato.dto.upstream.FuturosDataUpstream;
import com.example.extrato.dto.upstream.FuturosUpstreamResponse;
import com.example.extrato.dto.upstream.PaginacaoUpstreamDto;
import com.example.extrato.dto.upstream.RecentesUpstreamResponse;
import com.example.extrato.exception.UpstreamException;
import com.example.extrato.mapper.FiltrosMapper;
import com.example.extrato.mapper.FuturosMapper;
import com.example.extrato.mapper.RecentesMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtratoFiltrosServiceTest {

    @Mock private RecentesClient recentesClient;
    @Mock private FuturosClient futurosClient;

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

    @BeforeEach
    void setUp() {
        service = new ExtratoFiltrosService(
                recentesClient, futurosClient,
                new RecentesMapper(), new FuturosMapper(), new FiltrosMapper());
    }

    private ExtratoFiltrosRequest request(Aba aba) {
        return new ExtratoFiltrosRequest(
                Periodo.TRINTA_DIAS, null, null,
                EntradaSaida.ENTRADA_SAIDA, TipoLancamento.D, aba, 1);
    }

    @Test
    void semAba_deveRetornarDuasAbas() {
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(RECENTES_VAZIO);
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(null));

        assertThat(response.data().ordemAbas()).containsExactly("RECENTES", "FUTUROS");
        assertThat(response.data().abas()).containsKeys("RECENTES", "FUTUROS");
    }

    @Test
    void semAba_paginacaoDeveEstarDentroDeCartaAba_naoNaRaiz() {
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
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(RECENTES_VAZIO);
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(null));

        assertThat(response.paginacao()).isNull();
        assertThat(response.data().abas().get("RECENTES").paginacao()).isNull();
        assertThat(response.data().abas().get("FUTUROS").paginacao()).isNull();
    }

    @Test
    void abaRecentes_deveChamarApenasRecentesERetornarUmaAba() {
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(RECENTES_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(Aba.RECENTES));

        assertThat(response.data().ordemAbas()).containsExactly("RECENTES");
        assertThat(response.data().abas()).containsOnlyKeys("RECENTES");
        verify(futurosClient, never()).buscar(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void abaRecentes_paginacaoDeveEstarNaRaiz_naoNaAba() {
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
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(Aba.FUTUROS));

        assertThat(response.data().ordemAbas()).containsExactly("FUTUROS");
        assertThat(response.data().abas()).containsOnlyKeys("FUTUROS");
        verify(recentesClient, never()).buscar(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void tamanhoPaginaSempreDeveSerDezIndependenteDoUpstream() {
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(RECENTES_COM_PAGINACAO);

        ExtratoFiltrosResponse response = service.buscar(request(Aba.RECENTES));

        assertThat(response.paginacao().tamanhoPagina()).isEqualTo(10);
    }

    @Test
    void falhaUpstream_deveLancarUpstreamException() {
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(mock(FeignException.class));
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(FUTUROS_VAZIO);

        assertThatThrownBy(() -> service.buscar(request(null)))
                .isInstanceOf(UpstreamException.class);
    }
}
