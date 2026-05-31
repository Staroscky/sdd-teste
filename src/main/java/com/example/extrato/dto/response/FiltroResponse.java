package com.example.extrato.dto.response;

import java.util.List;

public record FiltroResponse(
        String id,
        String titulo,
        String placeholder,
        List<OpcaoFiltroResponse> opcoes
) {}
