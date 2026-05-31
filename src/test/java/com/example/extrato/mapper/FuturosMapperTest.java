package com.example.extrato.mapper;

import com.example.extrato.dto.response.LancamentoResponse;
import com.example.extrato.dto.upstream.CategoriaUpstream;
import com.example.extrato.dto.upstream.LancamentoFuturoUpstream;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FuturosMapperTest {

    private final FuturosMapper mapper = new FuturosMapper();

    @Test
    void deveMapearLancamentoFuturoParaResponse() {
        var lancamento = new LancamentoFuturoUpstream(
                "lancamentos", "entrada", "Joao Pedro",
                new BigDecimal("100.00"), "C",
                new CategoriaUpstream("uuid-2", "Transferencia")
        );

        List<LancamentoResponse> result = mapper.toResponseList(List.of(lancamento));

        assertThat(result).hasSize(1);
        LancamentoResponse r = result.getFirst();
        assertThat(r.tipo()).isEqualTo("lancamentos");
        assertThat(r.acao()).isEqualTo("entrada");
        assertThat(r.valor()).isEqualTo("R$ 100,00");
        assertThat(r.lancamento()).isEqualTo("C");
        assertThat(r.estilo()).isEqualTo("entrada");
        assertThat(r.categoria().id()).isEqualTo("uuid-2");
    }

    @Test
    void deveRetornarListaVaziaQuandoInputVazio() {
        List<LancamentoResponse> result = mapper.toResponseList(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void deveFormatarValorEmBRL() {
        var lancamento = new LancamentoFuturoUpstream(
                "lancamentos", "saida", "Nome",
                new BigDecimal("2000.00"), "D",
                new CategoriaUpstream("id", "Cat")
        );
        List<LancamentoResponse> result = mapper.toResponseList(List.of(lancamento));
        assertThat(result.getFirst().valor()).isEqualTo("R$ 2.000,00");
    }
}
