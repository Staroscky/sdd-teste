package com.example.extrato.dto.response;

public record ErroResponse(String codigo, String mensagem) {

    public static final String UPSTREAM_INDISPONIVEL = "UPSTREAM_INDISPONIVEL";
    public static final String UPSTREAM_PARCIALMENTE_INDISPONIVEL = "UPSTREAM_PARCIALMENTE_INDISPONIVEL";

    public static ErroResponse indisponivel() {
        return new ErroResponse(
                UPSTREAM_INDISPONIVEL,
                "O serviço de extrato está temporariamente indisponível. Tente novamente em instantes."
        );
    }

    public static ErroResponse parcialmenteIndisponivel() {
        return new ErroResponse(
                UPSTREAM_PARCIALMENTE_INDISPONIVEL,
                "Alguns dados do extrato estão temporariamente indisponíveis."
        );
    }
}
