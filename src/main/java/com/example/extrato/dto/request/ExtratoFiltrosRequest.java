package com.example.extrato.dto.request;

import java.time.LocalDate;

public record ExtratoFiltrosRequest(
        Periodo periodo,
        LocalDate dataInicial,
        LocalDate dataFinal,
        EntradaSaida entradaSaida,
        TipoLancamento lancamento,
        Aba aba,
        int pagina
) {}
