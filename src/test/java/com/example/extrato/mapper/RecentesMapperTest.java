package com.example.extrato.mapper;

import com.example.extrato.dto.response.LancamentoResponse;
import com.example.extrato.dto.upstream.CategoriaUpstream;
import com.example.extrato.dto.upstream.LancamentoRecenteUpstream;
import com.example.extrato.util.CurrencyFormatter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentesMapperTest {

    private final RecentesMapper mapper = new RecentesMapper(new CurrencyFormatter());

    @Test
    void deveMapearLancamentoSaidaParaResponse() {
        var lancamento = new LancamentoRecenteUpstream(
                "lancamentos", "saida", "Joao Pedro",
                new BigDecimal("100.00"), "2026-05-25",
                new CategoriaUpstream("uuid-1", "Transferencia")
        );

        List<LancamentoResponse> result = mapper.toResponseList(List.of(lancamento));

        assertThat(result).hasSize(1);
        LancamentoResponse r = result.getFirst();
        assertThat(r.acao().tipo()).isEqualTo("DEEPLINK");
        assertThat(r.acao().metadados()).containsEntry("params", "a definir");
        assertThat(r.data().titulo()).isEqualTo("2026-05-25");
        assertThat(r.data().estilo()).isEqualTo("NEUTRO");
        assertThat(r.tipo().titulo()).isEqualTo("Saida");
        assertThat(r.tipo().icone().token()).isEqualTo("ids_transferencia");
        assertThat(r.tipo().icone().estilo()).isEqualTo("NEUTRO");
        assertThat(r.valor().titulo()).isEqualTo("- R$ 100,00");
        assertThat(r.valor().estilo()).isEqualTo("NEGATIVO");
    }

    @Test
    void deveMapearLancamentoEntradaParaResponse() {
        var lancamento = new LancamentoRecenteUpstream(
                "lancamentos", "entrada", "Joao Pedro",
                new BigDecimal("100.00"), "2026-05-25",
                new CategoriaUpstream("uuid-1", "Transferencia")
        );

        LancamentoResponse r = mapper.toResponseList(List.of(lancamento)).getFirst();

        assertThat(r.tipo().titulo()).isEqualTo("Entrada");
        assertThat(r.valor().titulo()).isEqualTo("R$ 100,00");
        assertThat(r.valor().estilo()).isEqualTo("POSITIVO");
    }

    @Test
    void deveRetornarListaVaziaQuandoInputVazio() {
        assertThat(mapper.toResponseList(List.of())).isEmpty();
    }

    @Test
    void deveFormatarValorSaidaComSinalNegativo() {
        var lancamento = new LancamentoRecenteUpstream(
                "lancamentos", "saida", "Nome",
                new BigDecimal("1500.50"), "2026-05-25",
                new CategoriaUpstream("id", "Cat")
        );
        assertThat(mapper.toResponseList(List.of(lancamento)).getFirst().valor().titulo())
                .isEqualTo("- R$ 1.500,50");
    }

    @Test
    void deveUsarNomeDaCategoriaNoTokenDoIcone() {
        var lancamento = new LancamentoRecenteUpstream(
                "pix", "saida", "Nome",
                new BigDecimal("50.00"), "2026-05-25",
                new CategoriaUpstream("id", "Pix")
        );
        assertThat(mapper.toResponseList(List.of(lancamento)).getFirst().tipo().icone().token())
                .isEqualTo("ids_pix");
    }
}
