package com.example.extrato.mapper;

import com.example.extrato.dto.response.LancamentoResponse;
import com.example.extrato.dto.upstream.CategoriaUpstream;
import com.example.extrato.dto.upstream.LancamentoFuturoUpstream;
import com.example.extrato.util.CurrencyFormatter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FuturosMapperTest {

    private final FuturosMapper mapper = new FuturosMapper(new CurrencyFormatter());

    @Test
    void deveMapearLancamentoEntradaParaResponse() {
        var lancamento = new LancamentoFuturoUpstream(
                "lancamentos", "entrada", "Joao Pedro",
                new BigDecimal("100.00"), "2026-06-01",
                new CategoriaUpstream("uuid-2", "Transferencia")
        );

        List<LancamentoResponse> result = mapper.toResponseList(List.of(lancamento));

        assertThat(result).hasSize(1);
        LancamentoResponse r = result.getFirst();
        assertThat(r.acao().tipo()).isEqualTo("DEEPLINK");
        assertThat(r.data().titulo()).isEqualTo("2026-06-01");
        assertThat(r.data().estilo()).isEqualTo("NEUTRO");
        assertThat(r.tipo().titulo()).isEqualTo("Entrada");
        assertThat(r.tipo().icone().token()).isEqualTo("ids_transferencia");
        assertThat(r.valor().titulo()).isEqualTo("R$ 100,00");
        assertThat(r.valor().estilo()).isEqualTo("POSITIVO");
    }

    @Test
    void deveMapearLancamentoSaidaComSinalNegativo() {
        var lancamento = new LancamentoFuturoUpstream(
                "lancamentos", "saida", "Nome",
                new BigDecimal("2000.00"), "2026-06-01",
                new CategoriaUpstream("id", "Cat")
        );
        LancamentoResponse r = mapper.toResponseList(List.of(lancamento)).getFirst();
        assertThat(r.valor().titulo()).isEqualTo("- R$ 2.000,00");
        assertThat(r.valor().estilo()).isEqualTo("NEGATIVO");
    }

    @Test
    void deveRetornarListaVaziaQuandoInputVazio() {
        assertThat(mapper.toResponseList(List.of())).isEmpty();
    }
}
