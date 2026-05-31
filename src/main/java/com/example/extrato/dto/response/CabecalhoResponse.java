package com.example.extrato.dto.response;

public record CabecalhoResponse(
        String totalEntradas,
        String totalSaidas,
        String saldo
) {}
