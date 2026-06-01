package com.example.extrato.dto.response;

import java.util.Map;

public record AcaoResponse(String tipo, Map<String, String> metadados) {}
