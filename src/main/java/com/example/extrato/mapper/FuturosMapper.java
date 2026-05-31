package com.example.extrato.mapper;

import com.example.extrato.dto.response.CategoriaResponse;
import com.example.extrato.dto.response.LancamentoResponse;
import com.example.extrato.dto.upstream.LancamentoFuturoUpstream;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@Component
public class FuturosMapper {

    private static final Locale PT_BR = new Locale("pt", "BR");

    public List<LancamentoResponse> toResponseList(List<LancamentoFuturoUpstream> lancamentos) {
        return lancamentos.stream().map(this::toResponse).toList();
    }

    private LancamentoResponse toResponse(LancamentoFuturoUpstream lancamento) {
        return new LancamentoResponse(
                lancamento.tipo(),
                lancamento.acao(),
                lancamento.impacto(),
                formatarBRL(lancamento.valor()),
                lancamento.lancamento(),
                new CategoriaResponse(lancamento.categoria().id(), lancamento.categoria().nome()),
                lancamento.acao()
        );
    }

    private String formatarBRL(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(valor)
                .replace(' ', ' ');
    }
}
