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

/**
 * Testes de recuperação automática do circuit breaker via transição OPEN → HALF_OPEN → CLOSED.
 * Usa waitDurationInOpenState=2s para não depender de longos sleeps nos testes.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "upstream.recentes.url=http://localhost:${wiremock.server.port}",
                "upstream.futuros.url=http://localhost:${wiremock.server.port}",
                "resilience4j.circuitbreaker.instances.recentes.sliding-window-size=2",
                "resilience4j.circuitbreaker.instances.recentes.minimum-number-of-calls=1",
                "resilience4j.circuitbreaker.instances.recentes.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.recentes.wait-duration-in-open-state=2s",
                "resilience4j.circuitbreaker.instances.recentes.permitted-number-of-calls-in-half-open-state=1",
                "resilience4j.circuitbreaker.instances.futuros.sliding-window-size=2",
                "resilience4j.circuitbreaker.instances.futuros.minimum-number-of-calls=1",
                "resilience4j.circuitbreaker.instances.futuros.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.futuros.wait-duration-in-open-state=2s",
                "resilience4j.circuitbreaker.instances.futuros.permitted-number-of-calls-in-half-open-state=1"
        }
)
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
class CircuitBreakerRecoveryIT {

    @Autowired
    private MockMvc mockMvc;

    private static final String RECENTES_BODY = """
            {
              "data": [
                {
                  "tipo": "lancamentos",
                  "acao": "saida",
                  "impacto": "Joao Pedro",
                  "valor": 50.00,
                  "lancamento": "D",
                  "categoria": { "id": "uuid-1", "nome": "Alimentacao" }
                }
              ],
              "paginacao": null
            }
            """;

    private static final String FUTUROS_BODY = """
            { "data": { "lancamentos_futuros": [], "paginacao": null } }
            """;

    @BeforeEach
    void resetWireMock() {
        WireMock.reset();
    }

    @Test
    void aposRecuperacaoDoUpstream_circuitBreakerDeveRetornarAoClosed() throws Exception {
        // 1. Força abertura do CB com uma falha
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/recentes"))
                .willReturn(WireMock.aResponse().withStatus(500)));
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/futuros"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FUTUROS_BODY)));

        mockMvc.perform(get("/api/v1/extratos-filtros")
                        .param("periodo", "7_DIAS")
                        .param("entradaSaida", "ENTRADA_SAIDA")
                        .param("lancamento", "D")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erro.codigo").value("UPSTREAM_PARCIALMENTE_INDISPONIVEL"));

        // 2. Aguarda transição OPEN → HALF_OPEN (waitDurationInOpenState=2s)
        Thread.sleep(2500);

        // 3. Upstream voltou — configura resposta 200
        WireMock.reset();
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/recentes"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RECENTES_BODY)));
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/futuros"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FUTUROS_BODY)));

        // 4. Chamada de teste em HALF_OPEN — CB deve retornar ao CLOSED e resposta volta normal
        mockMvc.perform(get("/api/v1/extratos-filtros")
                        .param("periodo", "7_DIAS")
                        .param("entradaSaida", "ENTRADA_SAIDA")
                        .param("lancamento", "D")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erro").doesNotExist())
                .andExpect(jsonPath("$.data.abas.RECENTES.dados[0].valor").value("R$ 50,00"));
    }

    @Test
    void halfOpen_seUpstreamAindaFalha_circuitoDeveReabrirComErro() throws Exception {
        // 1. Abre o CB
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/recentes"))
                .willReturn(WireMock.aResponse().withStatus(500)));
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/futuros"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FUTUROS_BODY)));

        mockMvc.perform(get("/api/v1/extratos-filtros")
                        .param("periodo", "7_DIAS")
                        .param("entradaSaida", "ENTRADA_SAIDA")
                        .param("lancamento", "D")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erro").exists());

        // 2. Aguarda transição para HALF_OPEN
        Thread.sleep(2500);

        // 3. Upstream ainda falha (permanece 500)
        // 4. Chamada em HALF_OPEN com falha → CB reabre → fallback retorna erro
        mockMvc.perform(get("/api/v1/extratos-filtros")
                        .param("periodo", "7_DIAS")
                        .param("entradaSaida", "ENTRADA_SAIDA")
                        .param("lancamento", "D")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.abas.RECENTES.dados").isEmpty())
                .andExpect(jsonPath("$.erro.codigo").value("UPSTREAM_PARCIALMENTE_INDISPONIVEL"));
    }
}
