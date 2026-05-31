package com.example.extrato.dto.response;

public record PaginacaoResponse(
        Integer paginaAtual,
        int tamanhoPagina,
        Integer totalRegistros,
        Integer totalPaginas
) {}
