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
    void deveMapearLancamentoRecenteParaResponse() {
        var lancamento = new LancamentoRecenteUpstream(
                "lancamentos", "saida", "Joao Pedro",
                new BigDecimal("100.00"), "D",
                new CategoriaUpstream("uuid-1", "Transferencia")
        );

        List<LancamentoResponse> result = mapper.toResponseList(List.of(lancamento));

        assertThat(result).hasSize(1);
        LancamentoResponse r = result.getFirst();
        assertThat(r.tipo()).isEqualTo("lancamentos");
        assertThat(r.acao()).isEqualTo("saida");
        assertThat(r.impacto()).isEqualTo("Joao Pedro");
        assertThat(r.valor()).isEqualTo("R$ 100,00");
        assertThat(r.lancamento()).isEqualTo("D");
        assertThat(r.estilo()).isEqualTo("saida");
        assertThat(r.categoria().id()).isEqualTo("uuid-1");
        assertThat(r.categoria().nome()).isEqualTo("Transferencia");
    }

    @Test
    void deveRetornarListaVaziaQuandoInputVazio() {
        List<LancamentoResponse> result = mapper.toResponseList(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void deveFormatarValorEmBRL() {
        var lancamento = new LancamentoRecenteUpstream(
                "lancamentos", "entrada", "Nome",
                new BigDecimal("1500.50"), "C",
                new CategoriaUpstream("id", "Cat")
        );
        List<LancamentoResponse> result = mapper.toResponseList(List.of(lancamento));
        assertThat(result.getFirst().valor()).isEqualTo("R$ 1.500,50");
    }
}
