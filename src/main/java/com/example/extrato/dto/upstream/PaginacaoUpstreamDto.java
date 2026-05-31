package com.example.extrato.dto.upstream;

public record PaginacaoUpstreamDto(
        Integer pagina,
        Integer totalPaginas,
        Integer totalRegistros
) {}
