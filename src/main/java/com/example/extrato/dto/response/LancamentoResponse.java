package com.example.extrato.dto.response;

public record LancamentoResponse(
        String tipo,
        String acao,
        String impacto,
        String valor,
        String lancamento,
        CategoriaResponse categoria,
        String estilo
) {}
