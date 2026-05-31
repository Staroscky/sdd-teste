package com.example.extrato.dto.response;

public record PaginacaoResponse(
        int paginaAtual,
        int tamanhoPagina,
        Integer totalItens,
        Integer totalPaginas
) {}
