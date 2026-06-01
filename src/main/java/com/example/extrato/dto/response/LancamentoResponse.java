package com.example.extrato.dto.response;

public record LancamentoResponse(
        AcaoResponse acao,
        CelulaResponse data,
        TipoCelulaResponse tipo,
        CelulaResponse valor
) {}
