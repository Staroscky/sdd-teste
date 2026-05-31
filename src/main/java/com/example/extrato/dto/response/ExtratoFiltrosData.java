package com.example.extrato.dto.response;

import java.util.List;
import java.util.Map;

public record ExtratoFiltrosData(
        List<String> ordemAbas,
        Map<String, AbaResponse> abas
) {}
