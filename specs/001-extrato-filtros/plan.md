# Implementation Plan: Extrato com Filtros

**Branch**: `001-extrato-filtros` | **Date**: 2026-05-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-extrato-filtros/spec.md`

## Summary

BFF stateless que expõe `GET /api/v1/extratos-filtros`, agrega em paralelo dois endpoints
upstream (recentes e futuros), transforma os dados em um contrato pronto para renderização
no frontend (valores em BRL, datas em pt-BR, estilos resolvidos) e garante que nenhum
detalhe interno de erro (stack trace) vaze para o cliente.

## Technical Context

**Language/Version**: Java 21 (LTS) com virtual threads habilitadas
(`spring.threads.virtual.enabled=true`)

**Primary Dependencies**:
- Spring Boot 3.3.x (mínimo 3.2 para suporte nativo a virtual threads no Tomcat)
- Spring Cloud OpenFeign — cliente HTTP declarativo, síncrono/bloqueante, coerente com
  virtual threads
- feign-okhttp — substituir `HttpURLConnection` padrão do Feign para melhor integração
  com virtual threads
- Zalando Logbook (`logbook-spring-boot-starter` + `logbook-feign`) — logging estruturado
  de requests/responses inbound e outbound
- SLF4J + Logback

**Storage**: N/A — BFF stateless, sem persistência.

**Testing**:
- JUnit 5 + Mockito — testes unitários (mappers e services)
- WireMock — testes de integração das chamadas upstream
- AssertJ — assertions

**Target Platform**: JVM / servidor Linux (container)

**Project Type**: Web service (Spring Boot REST API — BFF)

**Performance Goals**: Resposta < 2 s quando ambas as upstream respondem dentro do timeout.

**Constraints**:
- Sem banco de dados.
- Sem autenticação no escopo atual.
- Sem cache no escopo atual.
- Não usar WebClient/Reactor — usar OpenFeign síncrono com virtual threads.
- Paralelismo via `CompletableFuture.allOf` + `Executors.newVirtualThreadPerTaskExecutor()`.

**Scale/Scope**: Single endpoint; escala horizontal via instâncias stateless.

## Constitution Check

*GATE: Must pass before implementation. Re-check after Phase 1 design.*

| Princípio | Status | Observação |
|-----------|--------|------------|
| I. Reactive, Non-Blocking Architecture | ✅ | Virtual threads + Feign síncrono — sem blocking no carrier thread. |
| II. Contract-First Design | ✅ | Contrato de resposta definido em `contracts/bff-response.md` antes da implementação. |
| III. Test Discipline | ✅ | Unitários (JUnit5+Mockito) para mappers/services; integração (WireMock) para rotas. |
| IV. Structured Error Handling | ✅ | `@ControllerAdvice` mapeia exceções para envelope estruturado sem stack trace. |
| V. Observability | ✅ | Logbook gerencia correlationId e logging estruturado inbound/outbound. |
| Code Patterns | ✅ | DTOs como Java records; sem campos public; sem lógica em construtores. |
| Aprovação explícita | ✅ | Todas as dependências novas listadas abaixo. |

**Dependências novas declaradas para aprovação** (Princípio VII da constituição):
- `spring-cloud-starter-openfeign`
- `feign-okhttp`
- `logbook-spring-boot-starter`
- `logbook-feign`
- `wiremock-spring-boot` (escopo test)

## Project Structure

### Documentation (this feature)

```text
specs/001-extrato-filtros/
├── plan.md
├── data-model.md
├── contracts/
│   └── bff-response.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/example/extrato/
├── controller/
│   └── ExtratoFiltrosController.java
├── service/
│   └── ExtratoFiltrosService.java
├── mapper/
│   ├── RecentesMapper.java
│   └── FuturosMapper.java
├── client/
│   ├── RecentesClient.java
│   └── FuturosClient.java
├── dto/
│   ├── request/
│   │   └── ExtratoFiltrosRequest.java
│   ├── response/
│   │   ├── ExtratoFiltrosResponse.java
│   │   ├── AbaResponse.java
│   │   ├── FiltroResponse.java
│   │   ├── CabecalhoResponse.java
│   │   └── LancamentoResponse.java
│   └── upstream/
│       ├── RecentesUpstreamResponse.java
│       ├── LancamentoRecenteUpstream.java
│       ├── FuturosUpstreamResponse.java
│       ├── LancamentoFuturoUpstream.java
│       └── CategoriaUpstream.java
├── config/
│   ├── FeignConfig.java
│   ├── LogbookConfig.java
│   └── ObjectMapperConfig.java
└── exception/
    ├── GlobalExceptionHandler.java
    ├── ErrorResponse.java
    └── UpstreamException.java

src/test/java/com/example/extrato/
├── service/
│   └── ExtratoFiltrosServiceTest.java
├── mapper/
│   ├── RecentesMapperTest.java
│   └── FuturosMapperTest.java
└── integration/
    └── ExtratoFiltrosIntegrationTest.java
```

**Structure Decision**: Single project Maven sem módulos; BFF stateless sem banco.

## Observability Design

- Logbook gerencia `correlationId` via header HTTP — sem MDC manual.
- `logbook-spring-boot-starter` filtra requests inbound.
- `logbook-feign` + `LogbookFeignLogger` intercepta chamadas Feign outbound.
- `feign.client.config.default.loggerLevel: FULL` obrigatório para o Logbook receber dados
  das upstream.
- `logbook.obfuscate.headers` oculta `Authorization` e outros headers sensíveis.

## Parallelism Design

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    var recentesFuture = CompletableFuture.supplyAsync(
        () -> recentesClient.buscar(params), executor);
    var futurosFuture = CompletableFuture.supplyAsync(
        () -> futurosClient.buscar(params), executor);
    CompletableFuture.allOf(recentesFuture, futurosFuture).join();
    return mapper.toResponse(recentesFuture.join(), futurosFuture.join());
}
```

Feign timeout via `application.yml`:
```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 3000
        readTimeout: 5000
        loggerLevel: FULL
```

## Error Handling Design

- `GlobalExceptionHandler` (`@ControllerAdvice`) captura todas as exceções.
- `FeignException` → HTTP 502 com `ErrorResponse(codigo, mensagem)`.
- Validação de parâmetros inválidos → HTTP 400.
- Exceção não mapeada → HTTP 500 genérico.
- Nenhuma resposta de erro expõe stack trace, classe Java ou mensagem interna.
