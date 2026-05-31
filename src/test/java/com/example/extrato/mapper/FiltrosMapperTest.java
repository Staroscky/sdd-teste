package com.example.extrato.mapper;

import com.example.extrato.dto.request.EntradaSaida;
import com.example.extrato.dto.request.ExtratoFiltrosRequest;
import com.example.extrato.dto.request.Periodo;
import com.example.extrato.dto.request.TipoLancamento;
import com.example.extrato.dto.response.FiltroResponse;
import com.example.extrato.dto.response.OpcaoFiltroResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FiltrosMapperTest {

    private final FiltrosMapper mapper = new FiltrosMapper();

    private ExtratoFiltrosRequest request(Periodo periodo, EntradaSaida entradaSaida) {
        return new ExtratoFiltrosRequest(periodo, null, null, entradaSaida, TipoLancamento.D, null, 1);
    }

    @Test
    void tituloPeriodoDeveSerDaOpcaoSelecionada() {
        List<FiltroResponse> filtros = mapper.mapear(request(Periodo.SETE_DIAS, EntradaSaida.ENTRADA_SAIDA));

        FiltroResponse filtroPeriodo = filtros.stream()
                .filter(f -> f.id().equals("periodo"))
                .findFirst().orElseThrow();

        assertThat(filtroPeriodo.titulo()).isEqualTo("7 dias");
    }

    @Test
    void tituloEntradaSaidaDeveSerDaOpcaoSelecionada() {
        List<FiltroResponse> filtros = mapper.mapear(request(Periodo.TRINTA_DIAS, EntradaSaida.ENTRADA_SAIDA));

        FiltroResponse filtroEntradaSaida = filtros.stream()
                .filter(f -> f.id().equals("entradas_saidas"))
                .findFirst().orElseThrow();

        assertThat(filtroEntradaSaida.titulo()).isEqualTo("Entradas e Saidas");
    }

    @Test
    void apenasUmaOpcaoDeveEstarSelecionadaPorFiltro() {
        List<FiltroResponse> filtros = mapper.mapear(request(Periodo.QUINZE_DIAS, EntradaSaida.ENTRADA));

        for (FiltroResponse filtro : filtros) {
            long selecionadas = filtro.opcoes().stream()
                    .filter(OpcaoFiltroResponse::selecionado)
                    .count();
            assertThat(selecionadas)
                    .as("Filtro '%s' deve ter exatamente 1 opcao selecionada", filtro.id())
                    .isEqualTo(1);
        }
    }

    @Test
    void opcaoSelecionadaDeveTerMesmoTituloDoFiltroPai() {
        List<FiltroResponse> filtros = mapper.mapear(request(Periodo.SESSENTA_DIAS, EntradaSaida.SAIDA));

        for (FiltroResponse filtro : filtros) {
            String tituloOpcaoSelecionada = filtro.opcoes().stream()
                    .filter(OpcaoFiltroResponse::selecionado)
                    .map(OpcaoFiltroResponse::titulo)
                    .findFirst().orElseThrow();
            assertThat(filtro.titulo())
                    .as("titulo do filtro '%s' deve igualar o titulo da opcao selecionada", filtro.id())
                    .isEqualTo(tituloOpcaoSelecionada);
        }
    }

    @Test
    void opcoesPeriodoDevemUsarIdDeApi() {
        List<FiltroResponse> filtros = mapper.mapear(request(Periodo.TRINTA_DIAS, EntradaSaida.ENTRADA_SAIDA));

        FiltroResponse filtroPeriodo = filtros.stream()
                .filter(f -> f.id().equals("periodo"))
                .findFirst().orElseThrow();

        assertThat(filtroPeriodo.opcoes())
                .extracting(OpcaoFiltroResponse::id)
                .containsExactly("7_DIAS", "15_DIAS", "30_DIAS", "60_DIAS", "PERSONALIZADO");
    }

    @Test
    void periodoTrintaDiasDeveEstarSelecionadoPorPadrao() {
        List<FiltroResponse> filtros = mapper.mapear(request(Periodo.TRINTA_DIAS, EntradaSaida.ENTRADA_SAIDA));

        FiltroResponse filtroPeriodo = filtros.stream()
                .filter(f -> f.id().equals("periodo"))
                .findFirst().orElseThrow();

        OpcaoFiltroResponse opcaoSelecionada = filtroPeriodo.opcoes().stream()
                .filter(OpcaoFiltroResponse::selecionado)
                .findFirst().orElseThrow();

        assertThat(opcaoSelecionada.id()).isEqualTo("30_DIAS");
        assertThat(filtroPeriodo.titulo()).isEqualTo("30 dias");
    }

    @Test
    void personalizado_deveConterMetadadosComDatas() {
        List<FiltroResponse> filtros = mapper.mapear(request(Periodo.PERSONALIZADO, EntradaSaida.ENTRADA_SAIDA));

        FiltroResponse filtroPeriodo = filtros.stream()
                .filter(f -> f.id().equals("periodo"))
                .findFirst().orElseThrow();

        OpcaoFiltroResponse opcaoPersonalizado = filtroPeriodo.opcoes().stream()
                .filter(o -> o.id().equals("PERSONALIZADO"))
                .findFirst().orElseThrow();

        assertThat(opcaoPersonalizado.metadados()).isNotNull();
        assertThat(opcaoPersonalizado.metadados()).containsKeys("dataMinima", "dataMaxima");
    }

    @Test
    void outrasOpcoesPeriodoNaoDevemTerMetadados() {
        List<FiltroResponse> filtros = mapper.mapear(request(Periodo.SETE_DIAS, EntradaSaida.ENTRADA_SAIDA));

        FiltroResponse filtroPeriodo = filtros.stream()
                .filter(f -> f.id().equals("periodo"))
                .findFirst().orElseThrow();

        filtros.stream()
                .filter(f -> f.id().equals("periodo"))
                .flatMap(f -> f.opcoes().stream())
                .filter(o -> !o.id().equals("PERSONALIZADO"))
                .forEach(o -> assertThat(o.metadados()).isNull());
    }
}
