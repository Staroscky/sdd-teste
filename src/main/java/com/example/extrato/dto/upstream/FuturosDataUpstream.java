package com.example.extrato.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FuturosDataUpstream(
        @JsonProperty("lancamentos_futuros") List<LancamentoFuturoUpstream> lancamentosFuturos,
        PaginacaoUpstreamDto paginacao
) {}
