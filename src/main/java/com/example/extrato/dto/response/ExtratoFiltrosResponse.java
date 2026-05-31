package com.example.extrato.dto.response;

public record ExtratoFiltrosResponse(
        ExtratoFiltrosData data,
        PaginacaoResponse paginacao
) {}
