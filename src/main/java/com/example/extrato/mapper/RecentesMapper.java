package com.example.extrato.mapper;

import com.example.extrato.dto.response.CategoriaResponse;
import com.example.extrato.dto.response.LancamentoResponse;
import com.example.extrato.dto.upstream.LancamentoRecenteUpstream;
import com.example.extrato.util.CurrencyFormatter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecentesMapper {

    private final CurrencyFormatter currencyFormatter;

    public RecentesMapper(CurrencyFormatter currencyFormatter) {
        this.currencyFormatter = currencyFormatter;
    }

    public List<LancamentoResponse> toResponseList(List<LancamentoRecenteUpstream> lancamentos) {
        return lancamentos.stream().map(this::toResponse).toList();
    }

    private LancamentoResponse toResponse(LancamentoRecenteUpstream lancamento) {
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
