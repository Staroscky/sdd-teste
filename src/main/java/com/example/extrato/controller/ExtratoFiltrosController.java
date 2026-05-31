package com.example.extrato.controller;

import com.example.extrato.dto.request.Aba;
import com.example.extrato.dto.request.EntradaSaida;
import com.example.extrato.dto.request.ExtratoFiltrosRequest;
import com.example.extrato.dto.request.Periodo;
import com.example.extrato.dto.request.TipoLancamento;
import com.example.extrato.dto.response.ExtratoFiltrosResponse;
import com.example.extrato.service.ExtratoFiltrosService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class ExtratoFiltrosController {

    private final ExtratoFiltrosService service;

    public ExtratoFiltrosController(ExtratoFiltrosService service) {
        this.service = service;
    }

    @GetMapping("/extratos-filtros")
    public ResponseEntity<ExtratoFiltrosResponse> buscar(
            @RequestParam(defaultValue = "30_DIAS") String periodo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            @RequestParam(defaultValue = "ENTRADA_SAIDA") EntradaSaida entradaSaida,
            @RequestParam(defaultValue = "D") TipoLancamento lancamento,
            @RequestParam(required = false) Aba aba,
            @RequestParam(defaultValue = "1") int pagina
    ) {
        var request = new ExtratoFiltrosRequest(
                Periodo.fromId(periodo), dataInicial, dataFinal, entradaSaida, lancamento, aba, pagina
        );
        return ResponseEntity.ok(service.buscar(request));
    }
}
