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

    private static final RecentesUpstreamResponse RECENTES_VAZIO =
            new RecentesUpstreamResponse(List.of(), null, null);
    private static final FuturosUpstreamResponse FUTUROS_VAZIO =
            new FuturosUpstreamResponse(new FuturosDataUpstream(List.of(), null, null));

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
        assertThat(response.paginacao().paginaAtual()).isEqualTo(1);
        assertThat(response.paginacao().tamanhoPagina()).isEqualTo(10);
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
    void abaFuturos_deveChamarApenasFuturosERetornarUmaAba() {
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        ExtratoFiltrosResponse response = service.buscar(request(Aba.FUTUROS));

        assertThat(response.data().ordemAbas()).containsExactly("FUTUROS");
        assertThat(response.data().abas()).containsOnlyKeys("FUTUROS");
        verify(recentesClient, never()).buscar(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void paginacaoDeveConterPaginaAtualETamanhoPagina() {
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(RECENTES_VAZIO);
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        var requestPagina3 = new ExtratoFiltrosRequest(
                Periodo.SETE_DIAS, null, null,
                EntradaSaida.ENTRADA_SAIDA, TipoLancamento.D, null, 3);

        ExtratoFiltrosResponse response = service.buscar(requestPagina3);

        assertThat(response.paginacao().paginaAtual()).isEqualTo(3);
        assertThat(response.paginacao().tamanhoPagina()).isEqualTo(10);
        assertThat(response.paginacao().totalItens()).isNull();
        assertThat(response.paginacao().totalPaginas()).isNull();
    }

    @Test
    void falhaUpstream_deveLancarUpstreamException() {
        when(recentesClient.buscar(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(mock(FeignException.class));
        when(futurosClient.buscar(any(), any(), any(), anyInt(), anyInt())).thenReturn(FUTUROS_VAZIO);

        assertThatThrownBy(() -> service.buscar(request(null)))
                .isInstanceOf(UpstreamException.class);
    }
}
