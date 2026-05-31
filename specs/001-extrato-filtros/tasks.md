---
description: "Task list for Extrato com Filtros"
---

# Tasks: Extrato com Filtros

**Input**: Design documents from `specs/001-extrato-filtros/`

**Prerequisites**: plan.md ✅ | spec.md ✅ | data-model.md ✅ | contracts/ ✅

**Organization**: Tarefas agrupadas em fases; User Story 1 (P1) é o único story do escopo.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Pode rodar em paralelo (arquivos diferentes, sem dependência incompleta)
- **[Story]**: A qual user story pertence (US1)
- Paths relativos à raiz do repositório

---

## Phase 1: Setup

**Purpose**: Inicialização do projeto Maven e configuração base.

- [x] T001 Criar `pom.xml` com Spring Boot 3.3.x, `spring-cloud-starter-openfeign`, `feign-okhttp`, `logbook-spring-boot-starter`, `logbook-feign`, JUnit 5, Mockito, AssertJ e `wiremock-spring-boot` (scope test)
- [x] T002 Criar `src/main/resources/application.yml` com `spring.threads.virtual.enabled=true`, `feign.client.config.default.connectTimeout: 3000`, `feign.client.config.default.readTimeout: 5000`, `feign.client.config.default.loggerLevel: FULL`, URLs das upstream e `logbook.obfuscate.headers: [Authorization]`
- [x] T003 [P] Criar `src/main/resources/logback-spring.xml` com appender JSON estruturado

**Checkpoint**: Projeto compila com `mvn compile` antes de prosseguir.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Infraestrutura compartilhada que TODOS os componentes dependem.

**⚠️ CRÍTICO**: Nenhum trabalho de implementação pode começar antes desta fase.

- [x] T004 [P] Criar enums `Periodo`, `EntradaSaida`, `TipoLancamento` e record `ExtratoFiltrosRequest` em `src/main/java/com/example/extrato/dto/request/`
- [x] T005 [P] Criar records upstream: `CategoriaUpstream`, `LancamentoRecenteUpstream`, `RecentesUpstreamResponse`, `LancamentoFuturoUpstream`, `FuturosDataUpstream`, `FuturosUpstreamResponse` em `src/main/java/com/example/extrato/dto/upstream/`
- [x] T006 [P] Criar records de resposta BFF: `CategoriaResponse`, `LancamentoResponse`, `FiltroResponse`, `CabecalhoResponse`, `AbaResponse`, `ExtratoFiltrosResponse` em `src/main/java/com/example/extrato/dto/response/`
- [x] T007 [P] Criar interface Feign `RecentesClient` em `src/main/java/com/example/extrato/client/RecentesClient.java`
- [x] T008 [P] Criar interface Feign `FuturosClient` em `src/main/java/com/example/extrato/client/FuturosClient.java`
- [x] T009 Criar `FeignConfig` (bean `okhttp3.OkHttpClient`) em `src/main/java/com/example/extrato/config/FeignConfig.java`
- [x] T010 Criar `LogbookConfig` (registrar `LogbookFeignLogger` como logger do Feign) em `src/main/java/com/example/extrato/config/LogbookConfig.java`
- [x] T011 [P] Criar record `ErrorResponse(String codigo, String mensagem)` em `src/main/java/com/example/extrato/exception/ErrorResponse.java`
- [x] T012 [P] Criar `UpstreamException` em `src/main/java/com/example/extrato/exception/UpstreamException.java`
- [x] T013 Criar `GlobalExceptionHandler` (`@ControllerAdvice`) mapeando `UpstreamException` → 502, validação de parâmetros → 400, `Exception` genérica → 500 — nenhum handler expõe stack trace em `src/main/java/com/example/extrato/exception/GlobalExceptionHandler.java`

**Checkpoint**: `mvn compile` limpo; todos os DTOs, clients e exception handler existem.

---

## Phase 3: User Story 1 — Consultar extrato filtrado (Priority: P1) 🎯 MVP

**Goal**: Frontend chama `GET /api/v1/extratos-filtros`, BFF agrega os dois upstream em
paralelo e retorna contrato formatado com abas RECENTES e FUTUROS.

**Independent Test**: Subir aplicação com WireMock simulando as duas upstream e enviar
`GET /api/v1/extratos-filtros?periodo=7_DIAS&entradaSaida=ENTRADA_SAIDA&lancamento=D`.
Verificar HTTP 200 com `ordemAbas`, `abas.RECENTES.dados` e `abas.FUTUROS.dados`
formatados em BRL.

### Tests for User Story 1 ⚠️ — escrever ANTES, devem FALHAR antes da implementação

- [x] T014 [P] [US1] Escrever `RecentesMapperTest` cobrindo formatação BRL, mapeamento de campos e lista vazia em `src/test/java/com/example/extrato/mapper/RecentesMapperTest.java`
- [x] T015 [P] [US1] Escrever `FuturosMapperTest` cobrindo formatação BRL, mapeamento de `lancamentos_futuros` e lista vazia em `src/test/java/com/example/extrato/mapper/FuturosMapperTest.java`
- [x] T016 [P] [US1] Escrever `ExtratoFiltrosServiceTest` cobrindo chamadas paralelas (mock dos clients), montagem de `ExtratoFiltrosResponse` e falha de upstream → `UpstreamException` em `src/test/java/com/example/extrato/service/ExtratoFiltrosServiceTest.java`
- [x] T017 [US1] Escrever `ExtratoFiltrosIntegrationTest` com WireMock stub para `/recentes` e `/futuros`; validar HTTP 200 com contrato completo, HTTP 400 para `PERSONALIZADO` sem datas e HTTP 502 quando upstream retorna 500 em `src/test/java/com/example/extrato/integration/ExtratoFiltrosIntegrationTest.java`

### Implementation for User Story 1

- [x] T018 [P] [US1] Implementar `RecentesMapper` (lista de `LancamentoRecenteUpstream` → lista de `LancamentoResponse`, formatar `valor` em BRL com `NumberFormat.getCurrencyInstance(new Locale("pt","BR"))`) em `src/main/java/com/example/extrato/mapper/RecentesMapper.java`
- [x] T019 [P] [US1] Implementar `FuturosMapper` (lista de `LancamentoFuturoUpstream` → lista de `LancamentoResponse`, mesma formatação BRL) em `src/main/java/com/example/extrato/mapper/FuturosMapper.java`
- [x] T020 [US1] Implementar `ExtratoFiltrosService` com `CompletableFuture.allOf` + `Executors.newVirtualThreadPerTaskExecutor()` para chamar `RecentesClient` e `FuturosClient` em paralelo; montar `ExtratoFiltrosResponse` com `ordemAbas` e `abas`; capturar `FeignException` e relançar como `UpstreamException` em `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java`
- [x] T021 [US1] Implementar `ExtratoFiltrosController` (`@RestController`, `@GetMapping("/api/v1/extratos-filtros")`, `@Valid` nos params, delega para `ExtratoFiltrosService`) em `src/main/java/com/example/extrato/controller/ExtratoFiltrosController.java`

**Checkpoint**: `mvn test` verde; aplicação responde corretamente ao cenário de teste independente descrito acima.

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Verificações finais e qualidade transversal.

- [ ] T022 [P] Executar `mvn verify` e confirmar que todos os testes (unitários + integração WireMock) passam
- [x] T023 [P] Inspecionar manualmente todas as respostas de erro (400, 502, 500) e confirmar ausência de stack trace, nome de classe Java ou mensagem de exceção interna
- [ ] T024 [P] Subir aplicação localmente e verificar que o `correlationId` aparece em todos os logs de uma mesma requisição (inbound + chamadas upstream outbound via Logbook)
- [x] T025 Revisar `pom.xml` final e confirmar que nenhuma dependência foi adicionada além das listadas no Constitution Check do `plan.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sem dependências — iniciar imediatamente.
- **Foundational (Phase 2)**: Depende de Phase 1 completa — BLOQUEIA tudo.
- **User Story 1 (Phase 3)**: Depende de Phase 2 completa.
  - Testes (T014–T017): escrever em paralelo antes de qualquer implementação.
  - Mappers (T018–T019): em paralelo após testes escritos.
  - Service (T020): após mappers completos.
  - Controller (T021): após service completo.
- **Polish (Phase N)**: Depende de Phase 3 completa.

### Within User Story 1

1. Escrever testes (T014–T016 em paralelo, T017 após)
2. Confirmar que testes FALHAM
3. Implementar mappers (T018–T019 em paralelo)
4. Implementar service (T020)
5. Implementar controller (T021)
6. Confirmar que testes PASSAM

### Parallel Opportunities

```bash
# Phase 2 — rodar em paralelo:
Task: T004 — request DTOs
Task: T005 — upstream DTOs
Task: T006 — response DTOs
Task: T007 — RecentesClient
Task: T008 — FuturosClient
Task: T011 — ErrorResponse
Task: T012 — UpstreamException

# Phase 3 — testes em paralelo:
Task: T014 — RecentesMapperTest
Task: T015 — FuturosMapperTest
Task: T016 — ExtratoFiltrosServiceTest

# Phase 3 — mappers em paralelo:
Task: T018 — RecentesMapper
Task: T019 — FuturosMapper
```

---

## Implementation Strategy

### MVP (User Story 1 única)

1. Completar Phase 1: Setup
2. Completar Phase 2: Foundational (**crítico — bloqueia tudo**)
3. Escrever testes de US1 — confirmar falha
4. Implementar mappers, service, controller
5. **PARAR E VALIDAR**: `mvn verify` verde + teste manual do endpoint
6. Completar Phase N: Polish

---

## Notes

- `[P]` = arquivos diferentes, sem dependência incompleta — podem rodar em paralelo
- `[US1]` = pertence ao User Story 1 (único story do escopo atual)
- Testes de integração usam WireMock — não mockar o Feign client diretamente
- Cada aba (RECENTES / FUTUROS) deve ser independentemente testável via WireMock stub
- `FuturosDataUpstream` existe para mapear o campo `data.lancamentos_futuros` da upstream de futuros via `@JsonProperty("lancamentos_futuros")`
- Valores monetários: usar `NumberFormat.getCurrencyInstance(new Locale("pt", "BR"))` — não `String.format`
