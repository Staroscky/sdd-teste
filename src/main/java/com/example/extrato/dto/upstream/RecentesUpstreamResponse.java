package com.example.extrato.dto.upstream;

import java.util.List;

public record RecentesUpstreamResponse(
        List<LancamentoRecenteUpstream> data,
        Integer totalItens,
        Integer totalPaginas
) {}
