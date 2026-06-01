# Implementation Plan: Resiliência com Circuit Breaker e Timeouts

**Branch**: `003-resiliencia-circuit-breaker` | **Date**: 2026-05-31 | **Spec**: [spec.md](spec.md)

---

## Summary

Adicionar Resilience4j ao extrato-bff para proteger as chamadas aos upstreams "recentes" e "futuros" com circuit breakers independentes e timeouts configuráveis por upstream. Quando um circuit breaker estiver OPEN, a aba afetada é retornada vazia e o campo `erro` é populado no envelope de resposta para que o front-end exiba a tela de indisponibilidade. Métricas dos circuit breakers são expostas via Spring Boot Actuator/Micrometer.

---

## Technical Context

**Language/Version**: Java 21 (virtual threads habilitados)

**Primary Dependencies**:
- Spring Boot 3.3.5 (MVC, não Reactor — violação pré-existente da Constitution)
- Spring Cloud 2023.0.3 (OpenFeign + Circuit Breaker BOM)
- OkHttp (transporte Feign)
- Resilience4j via `spring-cloud-starter-circuitbreaker-resilience4j` *(nova — requer aprovação)*
- Spring Boot Actuator *(nova — requer aprovação)*

**Storage**: N/A

**Testing**: JUnit 5 + WireMock (via `spring-cloud-contract-wiremock`)

**Target Platform**: JVM / container Docker

**Project Type**: Web service (BFF)

**Performance Goals**: Latência de fallback < 5ms (rejeição imediata pelo circuit breaker em estado OPEN)

**Constraints**: Resposta sempre `200 OK`; campo `erro` presente apenas quando necessário

**Scale/Scope**: 2 upstreams, 1 endpoint principal

---

## Constitution Check

| Princípio | Status | Observação |
|-----------|--------|------------|
| I. Reactive, Non-Blocking | ⚠️ Violação pré-existente | Projeto usa MVC + virtual threads; esta feature não aprofunda a violação |
| II. Contract-First Design | 🔴 Requer aprovação | Campo `erro` adicionado ao `ExtratoFiltrosResponse`; nova classe `ErroResponse` |
| III. Test Discipline | ✅ | Unit + integration tests para todos os cenários de fallback |
| IV. Structured Error Handling | ✅ | Erro mapeado para `ErroResponse` com código e mensagem definidos |
| V. Observability | ✅ | Métricas e health dos circuit breakers via Actuator |
| Decisions — New Dependencies | 🔴 Requer aprovação | `spring-cloud-starter-circuitbreaker-resilience4j` + `spring-boot-starter-actuator` |

**Aprovações necessárias antes de implementar:**
1. Campo `erro` em `ExtratoFiltrosResponse` + nova classe `ErroResponse`
2. Dependências: `spring-cloud-starter-circuitbreaker-resilience4j` e `spring-boot-starter-actuator`

---

## Project Structure

### Documentation (this feature)

```text
specs/003-resiliencia-circuit-breaker/
├── plan.md              ← este arquivo
├── spec.md
├── research.md
├── data-model.md
├── contracts/
│   └── bff-response.md
├── checklists/
│   └── requirements.md
└── tasks.md             ← gerado por /speckit-tasks
```

### Source Code

```text
src/main/java/com/example/extrato/
├── config/
│   ├── FeignConfig.java              ← modificado: beans OkHttpClient por upstream
│   ├── RecentesFeignConfig.java      ← novo: config Feign para upstream recentes
│   ├── FuturosFeignConfig.java       ← novo: config Feign para upstream futuros
│   └── UpstreamProperties.java       ← novo: @ConfigurationProperties por upstream
├── client/
│   ├── RecentesClient.java           ← modificado: referencia RecentesFeignConfig
│   └── FuturosClient.java            ← modificado: referencia FuturosFeignConfig
├── service/
│   └── ExtratoFiltrosService.java    ← modificado: circuit breaker wrapping + fallback
└── dto/response/
    ├── ExtratoFiltrosResponse.java   ← modificado: campo erro adicionado
    └── ErroResponse.java             ← novo

src/main/resources/
└── application.yml                   ← modificado: timeouts por upstream + resilience4j config

src/test/java/com/example/extrato/
├── service/
│   └── ExtratoFiltrosServiceTest.java ← modificado: cenários de fallback
└── integration/
    └── ExtratoFiltrosIntegrationTest.java ← modificado: cenários com upstream down
```

---

## Implementation Steps

### Step 1: Aprovações e dependências

1. Obter aprovação de contrato e dependências (ver Constitution Check)
2. Adicionar ao `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.cloud</groupId>
       <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   ```

### Step 2: Configuração (application.yml)

Adicionar timeouts por upstream e instâncias do circuit breaker:

```yaml
upstream:
  recentes:
    url: ${UPSTREAM_RECENTES_URL:http://localhost:9001}
    timeout:
      connect: ${UPSTREAM_RECENTES_CONNECT_TIMEOUT:3000}
      read: ${UPSTREAM_RECENTES_READ_TIMEOUT:5000}
  futuros:
    url: ${UPSTREAM_FUTUROS_URL:http://localhost:9002}
    timeout:
      connect: ${UPSTREAM_FUTUROS_CONNECT_TIMEOUT:3000}
      read: ${UPSTREAM_FUTUROS_READ_TIMEOUT:5000}

resilience4j:
  circuitbreaker:
    instances:
      recentes:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        registerHealthIndicator: true
      futuros:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        registerHealthIndicator: true

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,circuitbreakers
  health:
    circuitbreakers:
      enabled: true
```

### Step 3: UpstreamProperties

```java
@ConfigurationProperties(prefix = "upstream")
public record UpstreamProperties(UpstreamConfig recentes, UpstreamConfig futuros) {
    public record UpstreamConfig(String url, TimeoutConfig timeout) {}
    public record TimeoutConfig(int connect, int read) {}
}
```

### Step 4: FeignConfig — OkHttpClient por upstream

Substituir o único `OkHttpClient` bean por dois beans nomeados, cada um com os timeouts do seu upstream.

### Step 5: RecentesFeignConfig / FuturosFeignConfig

Cada classe de configuração Feign referencia o `OkHttpClient` bean correspondente via `@Qualifier`.

### Step 6: Contrato — ErroResponse + ExtratoFiltrosResponse

- Criar `ErroResponse` em `dto.response`
- Adicionar campo `ErroResponse erro` ao `ExtratoFiltrosResponse`

### Step 7: ExtratoFiltrosService — circuit breaker + fallback

Decorar cada chamada ao Feign client com `CircuitBreakerFactory`. Capturar `CallNotPermittedException` para retornar `AbaResponse` de fallback (filtros=[], dados=[], cabeçalho zerado). Montar `ErroResponse` conforme o cenário (parcial vs. total).

Lógica de fallback:
```
buscarAmbasAbas:
  - recentesFuture com CB "recentes"; futurosFuture com CB "futuros"
  - Se apenas um falha → aba OK normal + aba falha vazia + erro PARCIALMENTE_INDISPONIVEL
  - Se ambos falham → ambas vazias + erro INDISPONIVEL

buscarAbaEspecifica:
  - chamada única com CB do upstream correspondente
  - Se falha → aba vazia + erro INDISPONIVEL
```

### Step 8: Testes

- **Unit**: `ExtratoFiltrosServiceTest` — mockar `CircuitBreaker` em estado OPEN para cada cenário
- **Integration**: `ExtratoFiltrosIntegrationTest` — WireMock retornando 500/timeout para simular falha de upstream, verificar estrutura de resposta com campo `erro`

---

## Complexity Tracking

| Item | Justificativa |
|------|--------------|
| 2 novas dependências no `pom.xml` | Resilience4j e Actuator são dependências de infraestrutura sem alternativa equivalente já presente |
| Mudança de contrato (`erro` em `ExtratoFiltrosResponse`) | Necessário para comunicar ao front-end o estado de indisponibilidade sem quebrar consumidores existentes |
