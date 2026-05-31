package com.example.extrato.dto.response;

import java.util.List;

public record AbaResponse(
        List<FiltroResponse> filtros,
        CabecalhoResponse cabecalho,
        List<LancamentoResponse> dados
) {}
