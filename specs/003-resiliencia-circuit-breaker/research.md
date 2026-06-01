# Research: Resiliência com Circuit Breaker e Timeouts

**Branch**: `003-resiliencia-circuit-breaker` | **Date**: 2026-05-31

---

## Decision 1: Biblioteca de Circuit Breaker

**Decision**: Resilience4j via `spring-cloud-starter-circuitbreaker-resilience4j`

**Rationale**: Já incluso no BOM do Spring Cloud 2023.0.3 (usado no projeto) — zero conflito de versão. Integra-se nativamente com Spring Boot Actuator e Micrometer. A variante Spring Cloud Starter provê auto-configuration e suporte a `@CircuitBreaker` annotation sobre beans Spring, simplificando o wiring. Hystrix está oficialmente deprecated desde Spring Cloud 2020.

**Alternatives considered**:
- `io.github.resilience4j:resilience4j-spring-boot3` standalone: mais controle granular, porém exige configuração manual de todos os beans. Preterido pois o starter do Spring Cloud já cobre o caso de uso.
- Sentinel (Alibaba): robusto mas exige infraestrutura de dashboard externa. Excesso de complexidade para dois upstreams.

---

## Decision 2: Timeouts por Upstream com OkHttp

**Decision**: Criar um `OkHttpClient` bean nomeado por upstream (`recentesOkHttpClient`, `futurosOkHttpClient`) e associar cada cliente Feign ao seu bean via `@FeignClient(configuration = ...)`.

**Rationale**: O projeto já usa OkHttp como transporte do Feign (via `feign-okhttp`). OkHttp permite timeouts por instância de cliente. Criar beans separados por upstream é a forma idiomática de aplicar configurações de rede distintas sem introduzir outro transporte. Os valores são externalizados em `application.yml` sob `upstream.recentes.timeout.*` e `upstream.futuros.timeout.*`.

**Alternatives considered**:
- `feign.client.config.<nome-cliente>` com `connectTimeout`/`readTimeout`: funciona apenas quando o transporte é o HTTP client padrão do Feign (Apache/Java). Com OkHttp o bean do cliente prevalece sobre essa config. Descartado.
- Interceptor genérico de timeout: não suportado nativamente pelo OkHttp por chamada — o timeout é por instância de cliente. Descartado.

---

## Decision 3: Ponto de Aplicação do Circuit Breaker

**Decision**: Decorar os métodos do service layer (`ExtratoFiltrosService`) programaticamente via `CircuitBreakerFactory`, envolvendo cada chamada ao Feign client individualmente.

**Rationale**: Aplicar no service (e não no Feign client via annotation) mantém a lógica de fallback junto à lógica de negócio, onde o contexto de qual aba está sendo buscada já é conhecido. Isso permite retornar `AbaResponse` com `indisponivel=true` sem precisar lançar exceção e capturá-la em outro nível. A annotation `@CircuitBreaker` em interfaces Feign não suporta fallback com acesso ao contexto da aba.

**Alternatives considered**:
- `@CircuitBreaker` na interface Feign com `fallbackFactory`: suportado, mas o fallback retorna o tipo da resposta do upstream (ex: `RecentesUpstreamResponse`), exigindo lógica adicional de conversão fora do service. Mais indireto.
- AOP + `@CircuitBreaker` no service: equivalente ao programático mas com menos controle explícito. Preterido para manter clareza sobre o que está sendo protegido.

---

## Decision 4: Configuração dos Thresholds

**Decision**: Configuração via `application.yml` usando o namespace `resilience4j.circuitbreaker.instances.<nome>` (integração nativa do Spring Cloud Starter).

**Rationale**: O Spring Cloud Circuit Breaker Starter lê automaticamente as propriedades `resilience4j.circuitbreaker.instances.*` do ambiente Spring, sem código de configuração adicional. Permite sobrescrever valores por ambiente via variáveis de ambiente ou ConfigMaps (Kubernetes). Cada upstream (`recentes`, `futuros`) terá sua própria instância nomeada.

**Thresholds padrão propostos** (ajustáveis):

| Parâmetro | Valor padrão | Descrição |
|-----------|-------------|-----------|
| `slidingWindowType` | COUNT_BASED | Janela por contagem de chamadas |
| `slidingWindowSize` | 10 | Tamanho da janela de avaliação |
| `failureRateThreshold` | 50 | % de falhas para abrir o circuito |
| `waitDurationInOpenState` | 30s | Tempo em OPEN antes de tentar HALF_OPEN |
| `permittedNumberOfCallsInHalfOpenState` | 3 | Chamadas de teste em HALF_OPEN |
| `minimumNumberOfCalls` | 5 | Mínimo de chamadas antes de avaliar |

---

## Decision 5: Fallback — Estrutura da Resposta

**Decision**: Adicionar campo `erro: ErroResponse` ao envelope raiz `ExtratoFiltrosResponse` com `@JsonInclude(NON_NULL)`. Quando o circuit breaker está OPEN, a aba afetada é retornada com `filtros=[]`, `dados=[]`, cabeçalho zerado, e o campo `erro` é preenchido no envelope com código e mensagem. Comportamento por cenário:

| Cenário | HTTP | Abas retornadas | Campo `erro` |
|---------|------|-----------------|--------------|
| Todos os upstreams OK | 200 | Dados completos | ausente (null) |
| Uma aba falha (requisição de ambas) | 200 | Aba OK com dados + aba falha vazia | `UPSTREAM_PARCIALMENTE_INDISPONIVEL` |
| Todas as abas falham | 200 | Todas as abas vazias | `UPSTREAM_INDISPONIVEL` |
| Aba única falha | 200 | Aba vazia sem filtros | `UPSTREAM_INDISPONIVEL` |

**Rationale**: O campo `erro` no envelope raiz é o padrão já adotado no `GlobalExceptionHandler` do projeto (`ErrorResponse`). Manter o erro no topo (não dentro da aba) permite ao front-end tomar a decisão de exibir tela de indisponibilidade total ou parcial com uma única verificação. Sempre retornar `200` mantém compatibilidade com consumidores existentes.

**CONTRATO REQUER APROVAÇÃO** (Constitution Principle II): campo `erro` adicionado ao `ExtratoFiltrosResponse` e nova classe `ErroResponse` em `dto.response`.

**Alternatives considered**:
- Campo `indisponivel: boolean` por aba: exigiria que o front-end verificasse cada aba individualmente. Descartado.
- HTTP 206 Partial Content: quebra contratos de consumidores que esperam 200. Descartado.
- HTTP 503 em falha total: impede o front-end de receber a estrutura de abas para montar a tela de indisponibilidade contextualizada. Descartado.

---

## Decision 6: Métricas e Observabilidade

**Decision**: Adicionar `spring-boot-starter-actuator` + configuração de exposição do endpoint `/actuator/health` com circuit breaker health indicator, e `/actuator/metrics` com métricas Micrometer automáticas do Resilience4j.

**Rationale**: O Resilience4j Spring Boot Starter registra automaticamente métricas Micrometer (quando Actuator está presente) sem código adicional: `resilience4j.circuitbreaker.state`, `resilience4j.circuitbreaker.failure.rate`, `resilience4j.circuitbreaker.calls`, etc. O health indicator expõe o estado de cada circuit breaker no `/actuator/health`.

**Alternatives considered**:
- Métricas customizadas via `MeterRegistry`: desnecessário; Resilience4j já integra com Micrometer automaticamente.
- Exportar para Prometheus: possível adicionando `micrometer-registry-prometheus`, mas fora do escopo desta feature (não há requisito de endpoint `/actuator/prometheus`).

---

## Novas Dependências (requerem aprovação)

```xml
<!-- Circuit Breaker - já no BOM do Spring Cloud 2023.0.3 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>

<!-- Actuator para health e métricas -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Nota**: `spring-cloud-starter-circuitbreaker-resilience4j` traz transitivamente `resilience4j-core`, `resilience4j-spring-boot3`, `resilience4j-micrometer` — sem versão explícita necessária no pom.xml.
