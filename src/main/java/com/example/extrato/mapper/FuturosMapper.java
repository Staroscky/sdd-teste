package com.example.extrato.mapper;

import com.example.extrato.dto.response.CategoriaResponse;
import com.example.extrato.dto.response.LancamentoResponse;
import com.example.extrato.dto.upstream.LancamentoFuturoUpstream;
import com.example.extrato.util.CurrencyFormatter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FuturosMapper {

    private final CurrencyFormatter currencyFormatter;

    public FuturosMapper(CurrencyFormatter currencyFormatter) {
        this.currencyFormatter = currencyFormatter;
    }

    public List<LancamentoResponse> toResponseList(List<LancamentoFuturoUpstream> lancamentos) {
        return lancamentos.stream().map(this::toResponse).toList();
    }

    private LancamentoResponse toResponse(LancamentoFuturoUpstream lancamento) {
        return new LancamentoResponse(
                lancamento.tipo(),
                lancamento.acao(),
                lancamento.impacto(),
                currencyFormatter.format(lancamento.valor()),
                lancamento.lancamento(),
                new CategoriaResponse(lancamento.categoria().id(), lancamento.categoria().nome()),
                lancamento.acao()
        );
    }
}
