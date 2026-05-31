package com.example.extrato.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AbaResponse(
        List<FiltroResponse> filtros,
        CabecalhoResponse cabecalho,
        List<LancamentoResponse> dados,
        PaginacaoResponse paginacao
) {}
