# Data Model: Resiliência com Circuit Breaker e Timeouts

**Branch**: `003-resiliencia-circuit-breaker` | **Date**: 2026-05-31

---

## Entidades Modificadas

### ExtratoFiltrosResponse (modificada)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtratoFiltrosResponse(
    ExtratoFiltrosData data,
    PaginacaoResponse paginacao,
    ErroResponse erro             // NOVO: preenchido apenas em caso de falha de upstream
) {}
```

**Regra de negócio**:
- `erro == null` → resposta normal, front-end exibe dados normalmente.
- `erro != null` + alguma aba com dados → falha parcial, front-end exibe aviso de indisponibilidade parcial.
- `erro != null` + todas as abas vazias → falha total, front-end exibe tela de indisponibilidade.

**Impacto de contrato**: adição de campo nullable com `@JsonInclude(NON_NULL)` — campo ausente em respostas normais. **Requer aprovação** (Constitution Principle II).

### ErroResponse (nova — reutiliza estrutura existente de `ErrorResponse`)

```java
// Em com.example.extrato.dto.response (separado do exception.ErrorResponse existente)
public record ErroResponse(
    String codigo,       // ex.: "UPSTREAM_INDISPONIVEL", "UPSTREAM_PARCIALMENTE_INDISPONIVEL"
    String mensagem      // mensagem ao usuário final
) {}
```

**Códigos definidos**:

| Código | Quando usar |
|--------|-------------|
| `UPSTREAM_INDISPONIVEL` | Todas as abas solicitadas falharam (aba única ou ambas) |
| `UPSTREAM_PARCIALMENTE_INDISPONIVEL` | Apenas uma das abas falhou em requisição de ambas |

### AbaResponse (sem alteração)

A `AbaResponse` não recebe novo campo. Em modo fallback, é populada com `filtros=[]`, `dados=[]`, `cabecalho` zerado e `paginacao=null` — sem mudança de contrato nesta entidade.

---

## Configuração por Upstream (application.yml)

### Estrutura proposta

```yaml
upstream:
  recentes:
    url: ${UPSTREAM_RECENTES_URL:http://localhost:9001}
    timeout:
      connect: ${UPSTREAM_RECENTES_CONNECT_TIMEOUT:3000}   # ms
      read: ${UPSTREAM_RECENTES_READ_TIMEOUT:5000}          # ms
  futuros:
    url: ${UPSTREAM_FUTUROS_URL:http://localhost:9002}
    timeout:
      connect: ${UPSTREAM_FUTUROS_CONNECT_TIMEOUT:3000}    # ms
      read: ${UPSTREAM_FUTUROS_READ_TIMEOUT:5000}           # ms

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

### Properties de bind (UpstreamProperties)

```java
@ConfigurationProperties(prefix = "upstream")
public record UpstreamProperties(
    UpstreamConfig recentes,
    UpstreamConfig futuros
) {
    public record UpstreamConfig(
        String url,
        TimeoutConfig timeout
    ) {}

    public record TimeoutConfig(
        int connect,  // milissegundos
        int read      // milissegundos
    ) {}
}
```

---

## Novos Beans de Configuração

### FeignConfig (modificada)

- `recentesOkHttpClient()` — `OkHttpClient` com timeouts de `upstream.recentes.timeout.*`
- `futurosOkHttpClient()` — `OkHttpClient` com timeouts de `upstream.futuros.timeout.*`
- Cada bean inclui o `LogbookInterceptor` (mantém observabilidade existente)

### RecentesFeignConfig / FuturosFeignConfig (novas)

Classes de configuração Feign por cliente que referenciam o `OkHttpClient` nomeado correspondente.

```java
// Usado em @FeignClient(name = "recentes-client", configuration = RecentesFeignConfig.class)
public class RecentesFeignConfig {
    @Bean
    public feign.Client feignClient(OkHttpClient recentesOkHttpClient) {
        return new feign.okhttp.OkHttpClient(recentesOkHttpClient);
    }
}
```

---

## Fluxo de Estados do Circuit Breaker

```
         ┌─────────────────────────────────────────────────┐
         │                    CLOSED                        │
         │  Conta falhas/sucessos na janela deslizante      │
         └────────────────────┬────────────────────────────┘
                              │ failureRate >= 50%
                              │ após minimumNumberOfCalls
                              ▼
         ┌─────────────────────────────────────────────────┐
         │                     OPEN                         │
         │  Rejeita chamadas imediatamente (fallback)       │
         │  Aguarda waitDurationInOpenState (30s)           │
         └────────────────────┬────────────────────────────┘
                              │ após 30s
                              ▼
         ┌─────────────────────────────────────────────────┐
         │                  HALF_OPEN                       │
         │  Permite até 3 chamadas de teste                 │
         └────────┬───────────────────────┬────────────────┘
                  │ sucesso               │ falha
                  ▼                       ▼
              CLOSED                    OPEN
```

**Exceção disparada no estado OPEN**: `io.github.resilience4j.circuitbreaker.CallNotPermittedException` — capturada no service para retornar fallback.

---

## Impacto em Classes Existentes

| Classe | Tipo de mudança | Descrição |
|--------|----------------|-----------|
| `ExtratoFiltrosResponse` | Modificação | Campo `erro: ErroResponse` adicionado (nullable, NON_NULL) |
| `ErroResponse` | Nova classe | DTO de erro para o envelope de resposta |
| `FeignConfig` | Modificação | Substituído por beans nomeados por upstream |
| `ExtratoFiltrosService` | Modificação | Chamadas aos Feign clients decoradas com circuit breaker |
| `RecentesClient` | Modificação | Anotação `@FeignClient` referencia `RecentesFeignConfig` |
| `FuturosClient` | Modificação | Anotação `@FeignClient` referencia `FuturosFeignConfig` |
| `application.yml` | Modificação | Timeouts por upstream + config Resilience4j + Actuator |
| `pom.xml` | Modificação | 2 novas dependências (requer aprovação) |
| `RecentesFeignConfig` | Nova classe | Config Feign para upstream recentes |
| `FuturosFeignConfig` | Nova classe | Config Feign para upstream futuros |
| `UpstreamProperties` | Nova classe | Bind de configuração por upstream |
