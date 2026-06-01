package com.example.extrato.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "upstream.recentes.url=http://localhost:${wiremock.server.port}",
                "upstream.futuros.url=http://localhost:${wiremock.server.port}",
                "resilience4j.circuitbreaker.instances.recentes.sliding-window-size=2",
                "resilience4j.circuitbreaker.instances.recentes.minimum-number-of-calls=1",
                "resilience4j.circuitbreaker.instances.recentes.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.recentes.wait-duration-in-open-state=2s",
                "resilience4j.circuitbreaker.instances.futuros.sliding-window-size=2",
                "resilience4j.circuitbreaker.instances.futuros.minimum-number-of-calls=1",
                "resilience4j.circuitbreaker.instances.futuros.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.futuros.wait-duration-in-open-state=2s",
                "management.endpoints.web.exposure.include=health,metrics,circuitbreakers",
                "management.health.circuitbreakers.enabled=true"
        }
)
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
class ActuatorCircuitBreakerIT {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetWireMock() {
        WireMock.reset();
    }

    @Test
    void healthDeveExporEstadoDosCircuitBreakers() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.circuitBreakers").exists())
                .andExpect(jsonPath("$.components.circuitBreakers.details.recentes").exists())
                .andExpect(jsonPath("$.components.circuitBreakers.details.futuros").exists());
    }

    @Test
    void healthDeveReflectirEstadoUPQuandoCircuitosClosed() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.circuitBreakers.details.recentes.details.state")
                        .value("CLOSED"))
                .andExpect(jsonPath("$.components.circuitBreakers.details.futuros.details.state")
                        .value("CLOSED"));
    }

    @Test
    void metricsDeveExporMetricasDoCircuitBreaker() throws Exception {
        mockMvc.perform(get("/actuator/metrics/resilience4j.circuitbreaker.state")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("resilience4j.circuitbreaker.state"))
                .andExpect(jsonPath("$.measurements").exists());
    }

    @Test
    void aposAberturaDoCB_healthDeveIndicarEstadoAberto() throws Exception {
        // Força falha para abrir o CB de recentes
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/recentes"))
                .willReturn(WireMock.aResponse().withStatus(500)));
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/futuros"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"lancamentos_futuros\":[],\"paginacao\":null}}")));

        mockMvc.perform(get("/api/v1/extratos-filtros")
                        .param("periodo", "7_DIAS")
                        .param("entradaSaida", "ENTRADA_SAIDA")
                        .param("lancamento", "D")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Verifica que o health reflete o estado OPEN
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.components.circuitBreakers.details.recentes.details.state")
                        .value("OPEN"));
    }
}
