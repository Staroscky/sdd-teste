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
                "upstream.futuros.url=http://localhost:${wiremock.server.port}"
        }
)
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
class ExtratoFiltrosIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetWireMock() {
        WireMock.reset();
    }

    private static final String RECENTES_BODY = """
            {
              "data": [
                {
                  "tipo": "lancamentos",
                  "acao": "saida",
                  "impacto": "Joao Pedro",
                  "valor": 100.00,
                  "lancamento": "D",
                  "categoria": { "id": "uuid-1", "nome": "Transferencia" }
                }
              ]
            }
            """;

    private static final String FUTUROS_BODY = """
            {
              "data": {
                "lancamentos_futuros": [
                  {
                    "tipo": "lancamentos",
                    "acao": "entrada",
                    "impacto": "Joao Pedro",
                    "valor": 100.00,
                    "lancamento": "C",
                    "categoria": { "id": "uuid-2", "nome": "Transferencia" }
                  }
                ]
              }
            }
            """;

    @Test
    void deveRetornarExtratoComDuasAbas() throws Exception {
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/recentes"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(RECENTES_BODY)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/futuros"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FUTUROS_BODY)));

        mockMvc.perform(get("/api/v1/extratos-filtros")
                        .param("periodo", "SETE_DIAS")
                        .param("entradaSaida", "ENTRADA_SAIDA")
                        .param("lancamento", "D")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordemAbas[0]").value("RECENTES"))
                .andExpect(jsonPath("$.ordemAbas[1]").value("FUTUROS"))
                .andExpect(jsonPath("$.abas.RECENTES.dados[0].valor").value("R$ 100,00"))
                .andExpect(jsonPath("$.abas.FUTUROS.dados[0].valor").value("R$ 100,00"));
    }

    @Test
    void deveRetornar400QuandoPeriodoPersonalizadoSemDatas() throws Exception {
        mockMvc.perform(get("/api/v1/extratos-filtros")
                        .param("periodo", "PERSONALIZADO")
                        .param("entradaSaida", "ENTRADA_SAIDA")
                        .param("lancamento", "D")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void deveRetornar502QuandoRecentesFalha() throws Exception {
        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/recentes"))
                .willReturn(WireMock.aResponse().withStatus(500)));

        WireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/futuros"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FUTUROS_BODY)));

        mockMvc.perform(get("/api/v1/extratos-filtros")
                        .param("periodo", "SETE_DIAS")
                        .param("entradaSaida", "ENTRADA_SAIDA")
                        .param("lancamento", "D")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("UPSTREAM_INDISPONIVEL"))
                .andExpect(jsonPath("$.mensagem").exists());
    }
}
