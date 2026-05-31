package com.example.extrato.dto.upstream;

import java.math.BigDecimal;

public record LancamentoFuturoUpstream(
        String tipo,
        String acao,
        String impacto,
        BigDecimal valor,
        String lancamento,
        CategoriaUpstream categoria
) {}
