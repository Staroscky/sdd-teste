package com.example.extrato.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtratoFiltrosResponse(
        ExtratoFiltrosData data,
        PaginacaoResponse paginacao
) {}
