package com.example.extrato.mapper;

import com.example.extrato.dto.response.AcaoResponse;
import com.example.extrato.dto.response.CelulaResponse;
import com.example.extrato.dto.response.IconeResponse;
import com.example.extrato.dto.response.LancamentoResponse;
import com.example.extrato.dto.response.TipoCelulaResponse;
import com.example.extrato.dto.upstream.LancamentoRecenteUpstream;
import com.example.extrato.util.CurrencyFormatter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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
        boolean isSaida = "saida".equalsIgnoreCase(lancamento.acao());
        String valorTitulo = (isSaida ? "- " : "") + currencyFormatter.format(lancamento.valor());
        String iconeToken = "ids_" + lancamento.categoria().nome().toLowerCase();

        return new LancamentoResponse(
                new AcaoResponse("DEEPLINK", Map.of("params", "a definir")),
                new CelulaResponse(lancamento.lancamento(), "NEUTRO"),
                new TipoCelulaResponse(capitalize(lancamento.acao()), new IconeResponse(iconeToken, "NEUTRO")),
                new CelulaResponse(valorTitulo, isSaida ? "NEGATIVO" : "POSITIVO")
        );
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
