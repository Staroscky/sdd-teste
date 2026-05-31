package com.example.extrato.mapper;

import com.example.extrato.dto.request.EntradaSaida;
import com.example.extrato.dto.request.ExtratoFiltrosRequest;
import com.example.extrato.dto.request.Periodo;
import com.example.extrato.dto.response.FiltroResponse;
import com.example.extrato.dto.response.OpcaoFiltroResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class FiltrosMapper {

    public List<FiltroResponse> mapear(ExtratoFiltrosRequest request) {
        return List.of(
                buildFiltroPeriodo(request.periodo()),
                buildFiltroEntradaSaida(request.entradaSaida())
        );
    }

    private FiltroResponse buildFiltroPeriodo(Periodo selecionado) {
        String placeholder = "Periodo";
        List<OpcaoFiltroResponse> opcoes = Arrays.stream(Periodo.values())
                .map(p -> new OpcaoFiltroResponse(p.id, tituloPeriodo(p), p == selecionado, metadadosPeriodo(p)))
                .toList();
        return new FiltroResponse("periodo", resolverTituloSelecionado(opcoes, placeholder), placeholder, opcoes);
    }

    private FiltroResponse buildFiltroEntradaSaida(EntradaSaida selecionado) {
        String placeholder = "Tipo";
        List<OpcaoFiltroResponse> opcoes = Arrays.stream(EntradaSaida.values())
                .map(es -> new OpcaoFiltroResponse(es.name(), tituloEntradaSaida(es), es == selecionado, null))
                .toList();
        return new FiltroResponse("entradas_saidas", resolverTituloSelecionado(opcoes, placeholder), placeholder, opcoes);
    }

    private String resolverTituloSelecionado(List<OpcaoFiltroResponse> opcoes, String placeholder) {
        return opcoes.stream()
                .filter(OpcaoFiltroResponse::selecionado)
                .map(OpcaoFiltroResponse::titulo)
                .findFirst()
                .orElse(placeholder);
    }

    private String tituloPeriodo(Periodo periodo) {
        return switch (periodo) {
            case SETE_DIAS -> "7 dias";
            case QUINZE_DIAS -> "15 dias";
            case TRINTA_DIAS -> "30 dias";
            case SESSENTA_DIAS -> "60 dias";
            case PERSONALIZADO -> "Personalizado";
        };
    }

    private Map<String, String> metadadosPeriodo(Periodo periodo) {
        if (periodo != Periodo.PERSONALIZADO) return null;
        return Map.of(
                "dataMinima", LocalDate.now().minusYears(2).toString(),
                "dataMaxima", LocalDate.now().toString()
        );
    }

    private String tituloEntradaSaida(EntradaSaida entradaSaida) {
        return switch (entradaSaida) {
            case ENTRADA -> "Entradas";
            case SAIDA -> "Saidas";
            case ENTRADA_SAIDA -> "Entradas e Saidas";
        };
    }
}
