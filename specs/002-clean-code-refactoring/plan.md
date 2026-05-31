# Implementation Plan: Refatoração de Clean Code e Boas Práticas

**Branch**: `002-clean-code-refactoring` | **Date**: 2026-05-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-clean-code-refactoring/spec.md`

## Summary

Refatoração interna do extrato-bff para eliminar duplicação de código, reduzir responsabilidades
do service e consolidar validações — sem alterar o contrato de API nem o comportamento externo.
Os oito problemas identificados são endereçados em três grupos ordenados por dependência:
(1) extração de utilitário de formatação BRL compartilhado, (2) simplificação dos mappers e do
service, (3) consolidação de validações e literais.

## Technical Context

**Language/Version**: Java 21 (LTS) com virtual threads habilitadas

**Primary Dependencies**: Spring Boot 3.3.x, Spring Cloud OpenFeign, feign-okhttp, Zalando Logbook
— sem novas dependências nesta feature.

**Storage**: N/A — BFF stateless.

**Testing**: JUnit 5 + Mockito (unitários), WireMock (integração), AssertJ (assertions).

**Target Platform**: JVM / servidor Linux (container).

**Project Type**: Web service (Spring Boot REST API — BFF).

**Performance Goals**: Sem impacto em performance esperado; formatação BRL via `ThreadLocal`
elimina instanciação desnecessária por chamada.

**Constraints**:
- Nenhuma nova dependência Maven.
- Contrato de API inalterado (campos, estrutura, nomes).
- Nomes de métodos/classes existentes em português são mantidos (convenção já estabelecida);
  novos elementos seguem inglês conforme a constituição.
- Todos os testes existentes devem passar sem alteração de asserções.

**Scale/Scope**: Refatoração interna; escopo limitado a 6 arquivos de produção + atualização
dos testes correspondentes.

## Constitution Check

*GATE: Must pass before implementation. Re-check after Phase 1 design.*

| Princípio | Status | Observação |
|-----------|--------|------------|
| I. Reactive, Non-Blocking Architecture | ✅ | Nenhuma alteração no modelo de threading; virtual threads + Feign síncrono permanecem. |
| II. Contract-First Design | ✅ | Contrato de resposta não é alterado — refatoração é puramente interna. |
| III. Test Discipline | ✅ | Todos os testes existentes devem continuar passando; novos testes unitários para `CurrencyFormatter` serão adicionados. |
| IV. Structured Error Handling | ✅ | Tratamento de erros não é afetado. |
| V. Observability | ✅ | Logging e correlationId não são alterados. |
| Code Patterns | ✅ | Nenhum campo público introduzido; DTOs permanecem records; sem lógica em construtores. |
| Aprovação explícita | ✅ | Nenhuma nova dependência; contrato inalterado; sem cache ou estado compartilhado. |

## Project Structure

### Documentation (this feature)

```text
specs/002-clean-code-refactoring/
├── plan.md              ← este arquivo
├── research.md
├── data-model.md
└── tasks.md             (gerado por /speckit-tasks)
```

### Source Code — arquivos afetados

```text
src/main/java/com/example/extrato/
├── util/
│   └── CurrencyFormatter.java          ← NOVO: formatação BRL thread-safe
├── mapper/
│   ├── RecentesMapper.java             ← MODIFICADO: delega a CurrencyFormatter
│   ├── FuturosMapper.java              ← MODIFICADO: delega a CurrencyFormatter
│   └── FiltrosMapper.java              ← MODIFICADO: extrai resolverTituloSelecionado
├── service/
│   └── ExtratoFiltrosService.java      ← MODIFICADO: SRP, params extraídos, sem literais soltas
└── controller/
    └── ExtratoFiltrosController.java   ← MODIFICADO: validação de pagina removida para service

src/test/java/com/example/extrato/
├── util/
│   └── CurrencyFormatterTest.java      ← NOVO: testa formatação e thread-safety
├── mapper/
│   ├── RecentesMapperTest.java         ← sem alteração de asserções
│   ├── FuturosMapperTest.java          ← sem alteração de asserções
│   └── FiltrosMapperTest.java          ← sem alteração de asserções
└── service/
    └── ExtratoFiltrosServiceTest.java  ← sem alteração de asserções
```

**Structure Decision**: Single project Maven sem módulos; novo pacote `util` para utilitários
transversais (não são mappers nem configurações).

## Observability Design

Sem alterações — Logbook e correlationId permanecem intactos.

## Parallelism Design

Sem alterações — `CompletableFuture.allOf` + `Executors.newVirtualThreadPerTaskExecutor()` permanecem.
`CurrencyFormatter` usa `ThreadLocal<NumberFormat>` para segurança com virtual threads.

## Error Handling Design

Sem alterações — `GlobalExceptionHandler` permanece inalterado.

## Detalhamento das Mudanças

### 1. `CurrencyFormatter` (novo — `util/`)

```java
// util/CurrencyFormatter.java
@Component
public class CurrencyFormatter {

    private static final Locale PT_BR = Locale.of("pt", "BR");

    private static final ThreadLocal<NumberFormat> FORMAT =
        ThreadLocal.withInitial(() -> NumberFormat.getCurrencyInstance(PT_BR));

    public String format(BigDecimal valor) {
        return FORMAT.get().format(valor);
    }
}
```

- Remove a constante `PT_BR` duplicada de `RecentesMapper`, `FuturosMapper` e `ExtratoFiltrosService`.
- `ThreadLocal` garante uma instância de `NumberFormat` por virtual thread — sem contenção e sem
  criação por chamada.
- `ExtratoFiltrosService` também usa `CurrencyFormatter` em `calcularCabecalho`, eliminando sua
  própria constante `PT_BR` e `NumberFormat`.

### 2. `RecentesMapper` e `FuturosMapper` (simplificados)

- Removem `PT_BR`, `formatarBRL` e import de `NumberFormat`/`Locale`.
- Recebem `CurrencyFormatter` por injeção de construtor.
- Chamam `currencyFormatter.format(lancamento.valor())`.
- Lógica de mapeamento de `LancamentoResponse` e `CategoriaResponse` permanece idêntica.

### 3. `FiltrosMapper` — `resolverTituloSelecionado`

```java
private String resolverTituloSelecionado(List<OpcaoFiltroResponse> opcoes, String placeholder) {
    return opcoes.stream()
            .filter(OpcaoFiltroResponse::selecionado)
            .map(OpcaoFiltroResponse::titulo)
            .findFirst()
            .orElse(placeholder);
}
```

- `buildFiltroPeriodo` e `buildFiltroEntradaSaida` chamam este método em vez de duplicar o stream.

### 4. `ExtratoFiltrosService` — SRP e deduplicação

**4a. Extração de parâmetros upstream**

```java
private record UpstreamParams(String periodo, String entradaSaida, String lancamento, int pagina) {
    static UpstreamParams from(ExtratoFiltrosRequest r) {
        return new UpstreamParams(r.periodo().id, r.entradaSaida().name(), r.lancamento().name(), r.pagina());
    }
}
```

`buscarAmbasAbas` e `buscarAbaEspecifica` chamam `UpstreamParams.from(request)` e usam os campos
do record — eliminando o bloco duplicado de 4 linhas em cada método.

**4b. Constantes para chaves de aba**

```java
private static final String ABA_RECENTES = Aba.RECENTES.name();
private static final String ABA_FUTUROS  = Aba.FUTUROS.name();
```

Substitui todas as ocorrências de `"RECENTES"` e `"FUTUROS"` como literais.

**4c. `calcularCabecalho` mantido como stub documentado**

O método permanece retornando zero — mas é movido para uma responsabilidade clara: o service
delega o cálculo futuro ao mesmo ponto. Nenhuma lógica de negócio nova é adicionada.

**4d. Remoção de `PT_BR` e `NumberFormat` do service**

Após introdução do `CurrencyFormatter`, o service não precisa mais de `PT_BR` nem de importações
de `NumberFormat`. O `calcularCabecalho` passa a chamar `currencyFormatter.format(BigDecimal.ZERO)`.

### 5. `ExtratoFiltrosController` — validação consolidada

- Remove o `if (pagina < 1)` com `throw new IllegalArgumentException`.
- `ExtratoFiltrosService.validarParametros` recebe a regra de `pagina`.
- Ambas as validações ficam em `validarParametros`: `pagina < 1` e `PERSONALIZADO` sem datas.

## Complexity Tracking

Nenhuma violação de constituição identificada. Seção omitida.
