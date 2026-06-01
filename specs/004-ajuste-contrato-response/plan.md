# Implementation Plan: Ajuste do Contrato de Resposta da API de Extrato

**Branch**: `004-ajuste-contrato-response` | **Date**: 2026-05-31 | **Spec**: [spec.md](spec.md)

---

## Summary

Atualizar a estrutura de resposta do endpoint `GET /api/v1/extratos-filtros` para o novo formato cell-based esperado pelo front-end. As mudanças são puramente no contrato do BFF (DTOs, mappers, service): nenhuma dependência nova é adicionada, nenhuma chamada upstream é alterada.

---

## Technical Context

**Language/Version**: Java 21 (virtual threads habilitados)

**Primary Dependencies**:
- Spring Boot 3.3.5 (MVC)
- Jackson (serialização JSON — `@JsonInclude(NON_NULL)` já utilizado)
- JUnit 5 + Mockito + WireMock (testes existentes)

**Storage**: N/A

**Testing**: JUnit 5 + WireMock (via `spring-cloud-contract-wiremock`) + MockMvc

**Target Platform**: JVM / container Docker

**Project Type**: Web service (BFF)

**Performance Goals**: Sem impacto — mudança é estritamente de serialização de objetos já em memória.

**Constraints**: Resposta sempre `200 OK`; `erro` presente apenas quando necessário; `paginacao` nunca na raiz.

**Scale/Scope**: 4 novos records, 5 records modificados, 2 mappers reescritos, 1 mapper ajustado, 1 service simplificado, 1 record removido.

---

## Constitution Check

| Princípio | Status | Observação |
|-----------|--------|------------|
| I. Reactive, Non-Blocking | ⚠️ Violação pré-existente | Projeto usa MVC + virtual threads; esta feature não aprofunda a violação |
| II. Contract-First Design | ✅ | Spec `004-ajuste-contrato-response` aprovada antes da implementação; contrato documentado em `contracts/bff-response.md` |
| III. Test Discipline | ✅ | Todos os testes unitários e de integração são atualizados simultaneamente à mudança |
| IV. Structured Error Handling | ✅ | `ErroResponse` não é alterado; circuit breaker fallback mantido |
| V. Observability | ✅ | Nenhum log é alterado |
| Decisions — Alteração de contrato | ✅ | Aprovado via spec `004-ajuste-contrato-response` |
| Decisions — Nova dependência | ✅ | Nenhuma dependência nova |

---

## Project Structure

### Documentation (this feature)

```text
specs/004-ajuste-contrato-response/
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
src/main/java/com/example/extrato/dto/response/
├── AcaoResponse.java          ← novo
├── CelulaResponse.java        ← novo
├── IconeResponse.java         ← novo
├── TipoCelulaResponse.java    ← novo
├── LancamentoResponse.java    ← modificado (breaking change)
├── CabecalhoResponse.java     ← modificado (breaking change)
├── AbaResponse.java           ← modificado: cabecalho vira List
├── ExtratoFiltrosResponse.java← modificado: remove campo paginacao
├── OpcaoFiltroResponse.java   ← modificado: boolean → Boolean
└── CategoriaResponse.java     ← removido

src/main/java/com/example/extrato/
├── mapper/
│   ├── RecentesMapper.java        ← reescrito
│   ├── FuturosMapper.java         ← reescrito
│   └── FiltrosMapper.java         ← modificado
└── service/
    └── ExtratoFiltrosService.java ← modificado

src/test/java/com/example/extrato/
├── mapper/
│   ├── RecentesMapperTest.java        ← reescrito
│   ├── FuturosMapperTest.java         ← reescrito
│   └── FiltrosMapperTest.java         ← modificado
├── service/
│   └── ExtratoFiltrosServiceTest.java ← modificado
└── integration/
    └── ExtratoFiltrosIntegrationTest.java ← modificado
```

---

## Implementation Steps

### Step 1: Novos records de resposta

Criar os 4 novos records em `src/main/java/com/example/extrato/dto/response/`:

```java
// AcaoResponse.java
public record AcaoResponse(String tipo, Map<String, String> metadados) {}

// CelulaResponse.java
public record CelulaResponse(String titulo, String estilo) {}

// IconeResponse.java
public record IconeResponse(String token, String estilo) {}

// TipoCelulaResponse.java
public record TipoCelulaResponse(String titulo, IconeResponse icone) {}
```

### Step 2: Modificar records existentes

**`CabecalhoResponse`** — substituir campos financeiros por descriptor de coluna:
```java
public record CabecalhoResponse(String titulo, String id) {}
```

**`LancamentoResponse`** — nova estrutura cell-based:
```java
public record LancamentoResponse(
    AcaoResponse acao,
    CelulaResponse data,
    TipoCelulaResponse tipo,
    CelulaResponse valor
) {}
```

**`AbaResponse`** — `cabecalho` vira lista:
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AbaResponse(
    List<FiltroResponse> filtros,
    List<CabecalhoResponse> cabecalho,
    List<LancamentoResponse> dados,
    PaginacaoResponse paginacao
) {}
```

**`ExtratoFiltrosResponse`** — remover `paginacao`:
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtratoFiltrosResponse(ExtratoFiltrosData data, ErroResponse erro) {}
```

**`OpcaoFiltroResponse`** — `boolean` → `Boolean`:
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpcaoFiltroResponse(
    String id, String titulo, Boolean selecionado, Map<String, String> metadados
) {}
```

### Step 3: Remover `CategoriaResponse`

Deletar `CategoriaResponse.java` — sem referências após os steps anteriores.

### Step 4: Reescrever mappers

**`RecentesMapper`** e **`FuturosMapper`** — nova lógica de mapeamento:
```
isSaida = "saida".equalsIgnoreCase(acao_upstream)
valorTitulo = isSaida ? "- " + formatBRL(valor) : formatBRL(valor)
iconeToken  = "ids_" + categoria.nome.toLowerCase()
tipoCelula  = capitalize(acao_upstream)  →  "Saida" / "Entrada"

return new LancamentoResponse(
    new AcaoResponse("DEEPLINK", Map.of("params", "a definir")),
    new CelulaResponse(lancamento_upstream, "NEUTRO"),
    new TipoCelulaResponse(tipoCelula, new IconeResponse(iconeToken, "NEUTRO")),
    new CelulaResponse(valorTitulo, isSaida ? "NEGATIVO" : "POSITIVO")
)
```

**`FiltrosMapper`** — `selecionado` null para PERSONALIZADO:
```java
Map<String, String> metadados = metadadosPeriodo(p);
Boolean sel = (metadados != null) ? null : (p == selecionado);
// resolverTituloSelecionado: filter(o -> Boolean.TRUE.equals(o.selecionado()))
```

### Step 5: Atualizar `ExtratoFiltrosService`

- Adicionar constante estática:
  ```java
  private static final List<CabecalhoResponse> CABECALHO_FIXO = List.of(
      new CabecalhoResponse("Data", "data"),
      new CabecalhoResponse("Tipo", "tipo"),
      new CabecalhoResponse("Valor", "valor")
  );
  ```
- Substituir `calcularCabecalho(dados)` por `CABECALHO_FIXO` em `buildAbaRecentes`, `buildAbaFuturos` e `buildAbaFallback`
- Remover `CurrencyFormatter` do construtor (não mais usado no service)
- Simplificar `buscarAbaEspecifica`: remover extração de `paginacao` para a raiz; retornar `new ExtratoFiltrosResponse(data, erro)`
- Atualizar `buscarAmbasAbas`: `new ExtratoFiltrosResponse(data, null, erro)` → `new ExtratoFiltrosResponse(data, erro)`

### Step 6: Atualizar testes unitários

**`RecentesMapperTest`** e **`FuturosMapperTest`** — reescrever para nova estrutura:
- Verificar: `r.acao().tipo()`, `r.data().titulo()`, `r.tipo().titulo()`, `r.tipo().icone().token()`, `r.valor().titulo()`, `r.valor().estilo()`
- Adicionar caso de saida (sinal negativo) e entrada (sem sinal) separadamente

**`FiltrosMapperTest`** — ajustar:
- `filter(OpcaoFiltroResponse::selecionado)` → `filter(o -> Boolean.TRUE.equals(o.selecionado()))`

**`ExtratoFiltrosServiceTest`** — ajustar:
- Remover asserção `response.paginacao() == null` (campo não existe mais)
- Reescrever `abaRecentes_paginacaoDeveEstarNaRaiz_naoNaAba` → paginação agora DENTRO da aba
- Atualizar `tamanhoPaginaSempreDeveSerDez` → ler de `response.data().abas().get("RECENTES").paginacao()`
- Atualizar `setUp` do service — remover `currencyFormatter` do construtor
- Adicionar teste verificando `cabecalho` fixo com 3 colunas

### Step 7: Atualizar testes de integração

**`ExtratoFiltrosIntegrationTest`** — ajustar jsonPath:
- `$.data.abas.RECENTES.dados[0].valor` → `$.data.abas.RECENTES.dados[0].valor.titulo`
- `$.data.abas.FUTUROS.dados[0].valor` → `$.data.abas.FUTUROS.dados[0].valor.titulo`
- Adicionar: `$.data.abas.RECENTES.dados[0].valor.estilo` = `"NEGATIVO"` (saida)
- Adicionar: `$.data.abas.FUTUROS.dados[0].valor.estilo` = `"POSITIVO"` (entrada)

---

## Complexity Tracking

Sem violações da Constitution. Nenhuma entrada necessária nesta seção.
