package com.example.extrato.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpcaoFiltroResponse(
        String id,
        String titulo,
        Boolean selecionado,
        Map<String, String> metadados
) {}
