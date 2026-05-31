package com.example.extrato.client;

import com.example.extrato.dto.upstream.RecentesUpstreamResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "recentes-client", url = "${upstream.recentes.url}")
public interface RecentesClient {

    @GetMapping("/recentes")
    RecentesUpstreamResponse buscar(
            @RequestParam("periodo") String periodo,
            @RequestParam("entradaSaida") String entradaSaida,
            @RequestParam("lancamento") String lancamento,
            @RequestParam("pagina") int pagina,
            @RequestParam("tamanhoPagina") int tamanhoPagina
    );
}
