package com.example.extrato.client;

import com.example.extrato.config.FuturosFeignConfig;
import com.example.extrato.dto.upstream.FuturosUpstreamResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "futuros-client", url = "${upstream.futuros.url}", configuration = FuturosFeignConfig.class)
public interface FuturosClient {

    @GetMapping("/futuros")
    FuturosUpstreamResponse buscar(
            @RequestParam("periodo") String periodo,
            @RequestParam("entradaSaida") String entradaSaida,
            @RequestParam("lancamento") String lancamento,
            @RequestParam("pagina") int pagina,
            @RequestParam("tamanhoPagina") int tamanhoPagina
    );
}
