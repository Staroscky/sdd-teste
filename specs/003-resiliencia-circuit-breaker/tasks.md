# Tasks: Resiliência com Circuit Breaker e Timeouts

**Input**: Design documents from `/specs/003-resiliencia-circuit-breaker/`

**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | contracts/ ✅

**Organization**: Tasks agrupadas por user story para entrega incremental e validação independente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos distintos, sem dependência de tarefas incompletas)
- **[Story]**: User story correspondente (US1–US4)

---

## Phase 1: Setup (Aprovações e Dependências)

**Purpose**: Obter aprovações de contrato e adicionar dependências ao projeto antes de qualquer implementação.

**⚠️ BLOQUEADOR**: Nenhuma implementação pode começar sem as aprovações da Constitution.

- [x] T001 Obter aprovação do time para adição das dependências `spring-cloud-starter-circuitbreaker-resilience4j` e `spring-boot-starter-actuator` ao `pom.xml` (ver `contracts/bff-response.md` — seção Aprovação Requerida)
- [x] T002 Obter aprovação do time para mudança de contrato: campo `erro: ErroResponse` em `ExtratoFiltrosResponse` e nova classe `ErroResponse` (ver `contracts/bff-response.md`)
- [x] T003 Adicionar dependência `spring-cloud-starter-circuitbreaker-resilience4j` ao `pom.xml` (após aprovação T001)
- [x] T004 Adicionar dependência `spring-boot-starter-actuator` ao `pom.xml` (após aprovação T001)

**Checkpoint**: Dependências resolvidas — build compila com Resilience4j e Actuator disponíveis.

---

## Phase 2: Foundational (Pré-requisitos bloqueadores)

**Purpose**: Infraestrutura de configuração e contrato base que TODAS as user stories dependem.

**⚠️ CRÍTICO**: Fases 3–6 não podem começar até esta fase estar completa.

- [x] T005 Criar record `UpstreamProperties` com bind `@ConfigurationProperties(prefix = "upstream")` em `src/main/java/com/example/extrato/config/UpstreamProperties.java` — inclui `UpstreamConfig(url, TimeoutConfig)` e `TimeoutConfig(connect, read)`
- [x] T006 [P] Criar record `ErroResponse(String codigo, String mensagem)` em `src/main/java/com/example/extrato/dto/response/ErroResponse.java`
- [x] T007 [P] Atualizar record `ExtratoFiltrosResponse` em `src/main/java/com/example/extrato/dto/response/ExtratoFiltrosResponse.java` — adicionar campo `ErroResponse erro` com `@JsonInclude(NON_NULL)` (após aprovação T002)
- [x] T008 Substituir `FeignConfig` em `src/main/java/com/example/extrato/config/FeignConfig.java` — criar dois `OkHttpClient` beans nomeados (`recentesOkHttpClient`, `futurosOkHttpClient`) lendo timeouts de `UpstreamProperties`; cada bean inclui `LogbookInterceptor` (depende de T005)
- [x] T009 [P] Criar `RecentesFeignConfig` em `src/main/java/com/example/extrato/config/RecentesFeignConfig.java` — bean `feign.Client` usando `@Qualifier("recentesOkHttpClient")` (depende de T008)
- [x] T010 [P] Criar `FuturosFeignConfig` em `src/main/java/com/example/extrato/config/FuturosFeignConfig.java` — bean `feign.Client` usando `@Qualifier("futurosOkHttpClient")` (depende de T008)
- [x] T011 Atualizar `RecentesClient` em `src/main/java/com/example/extrato/client/RecentesClient.java` — adicionar `configuration = RecentesFeignConfig.class` na anotação `@FeignClient` (depende de T009)
- [x] T012 [P] Atualizar `FuturosClient` em `src/main/java/com/example/extrato/client/FuturosClient.java` — adicionar `configuration = FuturosFeignConfig.class` na anotação `@FeignClient` (depende de T010)
- [x] T013 Atualizar `src/main/resources/application.yml` — adicionar `upstream.recentes.timeout.*`, `upstream.futuros.timeout.*`, bloco `resilience4j.circuitbreaker.instances.recentes`, bloco `resilience4j.circuitbreaker.instances.futuros` e bloco `management.endpoints` conforme `data-model.md`

**Checkpoint**: Aplicação sobe sem erros, timeouts por upstream funcionam, dois `OkHttpClient` beans distintos existem, `ErroResponse` e contrato atualizado compilam.

---

## Phase 3: User Story 1 — Proteção contra falha de upstream (Priority: P1) 🎯 MVP

**Goal**: Quando um ou ambos os circuit breakers estão OPEN, a resposta retorna `200 OK` com a aba afetada vazia e campo `erro` preenchido — impedindo propagação de erro 500 ao consumidor.

**Independent Test**: Derrubar o WireMock do upstream "recentes" (ou configurá-lo para retornar 500 repetidamente até atingir o threshold), então chamar o endpoint e verificar: `200 OK`, aba RECENTES com `filtros=[]` e `dados=[]`, campo `erro.codigo = "UPSTREAM_INDISPONIVEL"` presente.

### Implementação — User Story 1

- [x] T014 [US1] Injetar `CircuitBreakerFactory` em `ExtratoFiltrosService` em `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java`
- [x] T015 [US1] Implementar método privado `buildAbaFallback(List<FiltroResponse> filtros)` em `ExtratoFiltrosService` — retorna `AbaResponse` com `filtros=[]`, `cabecalho` zerado, `dados=[]`, `paginacao=null`
- [x] T016 [US1] Decorar chamada a `recentesClient.buscar(...)` em `buscarAmbasAbas` com circuit breaker "recentes" via `CircuitBreakerFactory` — capturar `CallNotPermittedException` e retornar `buildAbaFallback(filtros)` em `ExtratoFiltrosService`
- [x] T017 [US1] Decorar chamada a `futurosClient.buscar(...)` em `buscarAmbasAbas` com circuit breaker "futuros" via `CircuitBreakerFactory` — capturar `CallNotPermittedException` e retornar `buildAbaFallback(filtros)` em `ExtratoFiltrosService`
- [x] T018 [US1] Implementar lógica de composição do `ErroResponse` em `buscarAmbasAbas`: se uma aba falhou → `UPSTREAM_PARCIALMENTE_INDISPONIVEL`; se ambas falharam → `UPSTREAM_INDISPONIVEL`; se nenhuma falhou → `erro=null` em `ExtratoFiltrosService`
- [x] T019 [US1] Decorar chamada a `recentesClient.buscar(...)` em `buscarAbaEspecifica` com circuit breaker "recentes" — capturar `CallNotPermittedException`, retornar `buildAbaFallback` + `erro = UPSTREAM_INDISPONIVEL` em `ExtratoFiltrosService`
- [x] T020 [US1] Decorar chamada a `futurosClient.buscar(...)` em `buscarAbaEspecifica` com circuit breaker "futuros" — capturar `CallNotPermittedException`, retornar `buildAbaFallback` + `erro = UPSTREAM_INDISPONIVEL` em `ExtratoFiltrosService`

### Testes — User Story 1

- [x] T021 [P] [US1] Adicionar unit tests em `ExtratoFiltrosServiceTest` para cenário: CB "recentes" OPEN + "futuros" OK → resposta com aba RECENTES vazia, aba FUTUROS com dados, `erro.codigo = UPSTREAM_PARCIALMENTE_INDISPONIVEL` em `src/test/java/com/example/extrato/service/ExtratoFiltrosServiceTest.java`
- [x] T022 [P] [US1] Adicionar unit tests em `ExtratoFiltrosServiceTest` para cenário: ambos CBs OPEN → ambas abas vazias, `erro.codigo = UPSTREAM_INDISPONIVEL` em `src/test/java/com/example/extrato/service/ExtratoFiltrosServiceTest.java`
- [x] T023 [P] [US1] Adicionar unit tests em `ExtratoFiltrosServiceTest` para cenário: aba única solicitada, CB OPEN → aba vazia, `erro.codigo = UPSTREAM_INDISPONIVEL` em `src/test/java/com/example/extrato/service/ExtratoFiltrosServiceTest.java`
- [x] T024 [P] [US1] Adicionar integration test em `ExtratoFiltrosIntegrationTest` — WireMock retorna 500 para "recentes" repetidamente até abrir CB, verificar estrutura JSON de resposta com `erro` preenchido em `src/test/java/com/example/extrato/integration/ExtratoFiltrosIntegrationTest.java`

**Checkpoint**: US1 completa. Fallback funciona para todos os cenários de falha. Testes passam.

---

## Phase 4: User Story 2 — Timeouts configuráveis por upstream (Priority: P2)

**Goal**: Cada upstream possui timeout de conexão e leitura independentes configuráveis via `application.yml`, substituindo os valores hardcoded atuais.

**Independent Test**: Configurar timeout de leitura de "recentes" para 1 segundo, fazer WireMock responder com delay de 2 segundos, verificar que a chamada é interrompida e tratada como falha (não fica pendente indefinidamente).

### Implementação — User Story 2

- [x] T025 [US2] Habilitar `@EnableConfigurationProperties(UpstreamProperties.class)` na classe principal ou em `FeignConfig` em `src/main/java/com/example/extrato/ExtratoBffApplication.java` (ou `src/main/java/com/example/extrato/config/FeignConfig.java`) — verificar que `UpstreamProperties` injeta corretamente os valores do `application.yml` (depende de T005, T008)
- [x] T026 [US2] Remover valores hardcoded de timeout de `FeignConfig` e validar que os beans `recentesOkHttpClient` e `futurosOkHttpClient` leem exclusivamente de `UpstreamProperties` em `src/main/java/com/example/extrato/config/FeignConfig.java`

### Testes — User Story 2

- [x] T027 [P] [US2] Adicionar integration test em `ExtratoFiltrosIntegrationTest` — WireMock com delay acima do timeout configurado para "recentes", verificar que a chamada é interrompida e o fallback é acionado em `src/test/java/com/example/extrato/integration/ExtratoFiltrosIntegrationTest.java`
- [x] T028 [P] [US2] Adicionar teste de configuração em `src/test/java/com/example/extrato/config/UpstreamPropertiesTest.java` — verificar bind correto dos campos `connect` e `read` por upstream a partir de propriedades de teste

**Checkpoint**: US2 completa. Timeouts independentes por upstream funcionam e são lidos do `application.yml`.

---

## Phase 5: User Story 3 — Observabilidade dos circuit breakers (Priority: P3)

**Goal**: Estado e métricas de cada circuit breaker acessíveis via endpoint de monitoramento sem acesso a logs.

**Independent Test**: Com a aplicação rodando, chamar `GET /actuator/health` e verificar presença de `components.circuitBreakers.details.recentes.status` e `components.circuitBreakers.details.futuros.status`. Chamar `GET /actuator/metrics/resilience4j.circuitbreaker.state` e verificar métricas por instância.

### Implementação — User Story 3

- [x] T029 [US3] Validar em `src/main/resources/application.yml` que `management.endpoints.web.exposure.include` contém `health`, `metrics` e `circuitbreakers`, e que `management.health.circuitbreakers.enabled=true` está presente (configurado em T013 — verificar e ajustar se necessário)
- [x] T030 [US3] Verificar que `registerHealthIndicator: true` está configurado para ambas as instâncias do circuit breaker em `application.yml` e que o Actuator expõe o estado corretamente ao subir a aplicação

### Testes — User Story 3

- [x] T031 [P] [US3] Adicionar integration test em `src/test/java/com/example/extrato/integration/ActuatorCircuitBreakerIT.java` — verificar que `GET /actuator/health` retorna estado dos circuit breakers "recentes" e "futuros" com status `UP` quando ambos CLOSED
- [x] T032 [P] [US3] Adicionar integration test em `ActuatorCircuitBreakerIT` — após forçar abertura do CB "recentes" via falhas WireMock, verificar que `/actuator/health` reflete status `DOWN` ou `OUT_OF_SERVICE` para o componente afetado em `src/test/java/com/example/extrato/integration/ActuatorCircuitBreakerIT.java`

**Checkpoint**: US3 completa. Métricas e health dos circuit breakers visíveis via Actuator.

---

## Phase 6: User Story 4 — Recuperação automática via HALF_OPEN (Priority: P2)

**Goal**: Após o período de espera configurado, o circuit breaker transita automaticamente para HALF_OPEN e retorna ao estado CLOSED quando o upstream se recupera — sem intervenção manual.

**Independent Test**: Forçar abertura do CB "recentes", aguardar o `waitDurationInOpenState` (30s no padrão, reduzir para 2s nos testes), restaurar o WireMock de "recentes" para retornar 200, fazer uma chamada de teste e verificar que o CB retorna ao estado CLOSED e a resposta volta com dados normais e sem campo `erro`.

### Implementação — User Story 4

> Esta user story é coberta inteiramente pela configuração do Resilience4j definida na Phase 2 (T013). O comportamento HALF_OPEN é automático quando `waitDurationInOpenState` e `permittedNumberOfCallsInHalfOpenState` estão configurados.

- [x] T033 [US4] Validar em `application.yml` que `waitDurationInOpenState`, `permittedNumberOfCallsInHalfOpenState` e `minimumNumberOfCalls` estão definidos para ambas as instâncias em `src/main/resources/application.yml` (verificação e documentação — sem código novo)

### Testes — User Story 4

- [x] T034 [US4] Adicionar integration test em `src/test/java/com/example/extrato/integration/CircuitBreakerRecoveryIT.java` — configurar `waitDurationInOpenState=2s` no test profile, forçar abertura do CB, aguardar transição para HALF_OPEN, simular sucesso no WireMock e verificar retorno ao estado CLOSED com resposta normal (sem campo `erro`)
- [x] T035 [US4] Adicionar integration test em `CircuitBreakerRecoveryIT` — cenário HALF_OPEN com falha: CB retorna ao estado OPEN e fallback é reativado em `src/test/java/com/example/extrato/integration/CircuitBreakerRecoveryIT.java`

**Checkpoint**: US4 completa. Recuperação automática validada por testes.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Limpeza, logging, e validação final de conformidade com a Constitution.

- [x] T036 [P] Adicionar log estruturado em `ExtratoFiltrosService` para eventos de abertura de circuit breaker — incluir `upstream` (recentes/futuros) e `correlationId` no MDC em `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java`
- [x] T037 [P] Remover import de `feign.FeignException` de `ExtratoFiltrosService` caso não seja mais necessário após o circuit breaker absorver as falhas em `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java`
- [x] T038 [P] Verificar que `ExtratoFiltrosServiceTest` existente (cenários de sucesso) continua passando após as modificações — sem regressões em `src/test/java/com/example/extrato/service/ExtratoFiltrosServiceTest.java`
- [x] T039 [P] Verificar que `ExtratoFiltrosIntegrationTest` existente continua passando — sem regressões em `src/test/java/com/example/extrato/integration/ExtratoFiltrosIntegrationTest.java`
- [x] T040 Atualizar checklist de aprovação em `specs/003-resiliencia-circuit-breaker/contracts/bff-response.md` marcando os itens aprovados após implementação

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Sem dependências — iniciar imediatamente
- **Phase 2 (Foundational)**: Depende de Phase 1 — bloqueia todas as outras
- **Phase 3 (US1)**: Depende de Phase 2 — MVP principal
- **Phase 4 (US2)**: Depende de Phase 2 (T005, T008) — pode rodar em paralelo com US1 após fundação
- **Phase 5 (US3)**: Depende de Phase 2 (T013) — pode rodar em paralelo após fundação
- **Phase 6 (US4)**: Depende de Phase 2 (T013) — pode rodar em paralelo após fundação
- **Phase 7 (Polish)**: Depende de todas as user stories desejadas estarem completas

### User Story Dependencies

| User Story | Depende de | Pode rodar em paralelo com |
|------------|-----------|---------------------------|
| US1 (P1) | Phase 2 completa | US2, US3, US4 |
| US2 (P2) | T005, T008 | US1, US3, US4 |
| US3 (P3) | T013 | US1, US2, US4 |
| US4 (P2) | T013 | US1, US2, US3 |

### Sequência dentro de cada User Story

```
Implementação → Testes unitários → Testes de integração → Checkpoint
```

---

## Parallel Execution Examples

### Phase 2 — Paralelizável após T005 e T008

```
T005 → T008 → [T009 || T010] → [T011 || T012]
              [T006 || T007] (independente)
T013 (independente de T006–T012)
```

### User Story 1 — Após Phase 2 completa

```
T014 → [T015 || T016 || T017] → T018 → [T019 || T020]
                                         ↓
                               [T021 || T022 || T023 || T024]
```

### User Stories 2, 3 e 4 — Em paralelo com US1 (equipes distintas)

```
Developer A: US1 (T014–T024)
Developer B: US2 (T025–T028)
Developer C: US3 + US4 (T029–T035)
```

---

## Implementation Strategy

### MVP First (User Story 1 — Proteção imediata)

1. Completar Phase 1: Aprovações + dependências
2. Completar Phase 2: Fundação (config, contrato, OkHttp por upstream)
3. Completar Phase 3: US1 — circuit breaker + fallback
4. **PARAR E VALIDAR**: derrubar um upstream, verificar resposta com `erro` preenchido
5. Deploy — proteção contra cascata de erros já está ativa

### Incremental Delivery

1. Phase 1 + 2 → Fundação pronta
2. Phase 3 (US1) → Fallback e circuit breaker ativos → **Deploy MVP**
3. Phase 4 (US2) → Timeouts por upstream configuráveis → Deploy
4. Phase 5 (US3) → Observabilidade Actuator → Deploy
5. Phase 6 (US4) → Recuperação automática validada → Deploy
6. Phase 7 → Polish e regressão → Release final

---

## Notes

- `[P]` = arquivos distintos, sem dependência de tarefas incompletas — podem rodar em paralelo
- `[USn]` = user story de rastreabilidade
- Approvals (T001, T002) são pré-requisitos humanos, não técnicos — obtenha antes de codar
- Circuit breaker testa falhas reais (5xx, timeout, `CallNotPermittedException`) — não mockar o CB diretamente nos testes de integração, usar WireMock para simular falha upstream
- Para testes de recuperação (US4), use `application-test.yml` com `waitDurationInOpenState=2s` para evitar espera de 30s nos testes
