# Tasks: Ajuste do Contrato de Resposta da API de Extrato

**Input**: Design documents from `specs/004-ajuste-contrato-response/`

**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | contracts/bff-response.md ✅

**Branch**: `004-ajuste-contrato-response`

---

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Pode rodar em paralelo (arquivos distintos, sem dependências incompletas)
- **[Story]**: A qual user story a task pertence (US1, US2, US3, US4)

---

## Phase 1: Setup

> Nenhuma estrutura de projeto nova ou dependência precisa ser criada. O projeto Java já está configurado. Esta fase não possui tasks — avançar direto para Phase 2.

---

## Phase 2: Foundational — Novos e Modificados DTOs de Resposta

**Purpose**: Criar e modificar todos os records de resposta. Bloqueia TODAS as user stories — mappers e service não compilam até este passo estar completo.

**⚠️ CRITICAL**: Nenhuma user story pode começar antes desta fase estar completa.

### Batch A — Novos records (paralelos entre si)

- [x] T001 [P] Criar `src/main/java/com/example/extrato/dto/response/AcaoResponse.java` — record `(String tipo, Map<String, String> metadados)`
- [x] T002 [P] Criar `src/main/java/com/example/extrato/dto/response/CelulaResponse.java` — record `(String titulo, String estilo)`
- [x] T003 [P] Criar `src/main/java/com/example/extrato/dto/response/IconeResponse.java` — record `(String token, String estilo)`
- [x] T004 [P] Criar `src/main/java/com/example/extrato/dto/response/TipoCelulaResponse.java` — record `(String titulo, IconeResponse icone)`

### Batch B — Records modificados independentes (paralelos entre si, paralelos com Batch A)

- [x] T005 [P] Modificar `src/main/java/com/example/extrato/dto/response/CabecalhoResponse.java` — substituir campos `{totalEntradas, totalSaidas, saldo}` por `(String titulo, String id)`
- [x] T006 [P] Modificar `src/main/java/com/example/extrato/dto/response/OpcaoFiltroResponse.java` — alterar `boolean selecionado` para `Boolean selecionado` (boxed)
- [x] T007 [P] Modificar `src/main/java/com/example/extrato/dto/response/ExtratoFiltrosResponse.java` — remover campo `paginacao`; manter apenas `(ExtratoFiltrosData data, ErroResponse erro)`

### Batch C — Records que dependem de Batch A e B

- [x] T008 Modificar `src/main/java/com/example/extrato/dto/response/LancamentoResponse.java` — substituir campos antigos por `(AcaoResponse acao, CelulaResponse data, TipoCelulaResponse tipo, CelulaResponse valor)` [depende de T001–T004]
- [x] T009 Modificar `src/main/java/com/example/extrato/dto/response/AbaResponse.java` — alterar `CabecalhoResponse cabecalho` para `List<CabecalhoResponse> cabecalho` [depende de T005, T008]

### Batch D — Remoção

- [x] T010 Deletar `src/main/java/com/example/extrato/dto/response/CategoriaResponse.java` — record obsoleto após T008 [depende de T008]

**Checkpoint**: Todos os DTOs compilam com a nova estrutura. Nenhuma referência a `CategoriaResponse` existe no código de produção.

---

## Phase 3: User Story 1 — Lançamentos tipados (Priority: P1) 🎯 MVP

**Goal**: Mappers produzem o novo `LancamentoResponse` com células tipadas (`acao`, `data`, `tipo`, `valor`). O front-end consegue renderizar a tabela sem transformações adicionais.

**Independent Test**: Rodar `RecentesMapperTest` e `FuturosMapperTest`; verificar que `dados[i].valor.titulo` contém o sinal correto e `dados[i].tipo.icone.token` está no formato `"ids_<categoria>"`.

### Implementação — US1

- [x] T011 [P] [US1] Reescrever `src/main/java/com/example/extrato/mapper/RecentesMapper.java` — nova lógica: `isSaida`, `capitalize`, `iconeToken = "ids_" + categoria.nome.toLowerCase()`, construir `LancamentoResponse` com 4 células [depende de T008, T010]
- [x] T012 [P] [US1] Reescrever `src/main/java/com/example/extrato/mapper/FuturosMapper.java` — mesma lógica de T011 para lançamentos futuros [depende de T008, T010]

### Testes — US1

- [x] T013 [P] [US1] Reescrever `src/test/java/com/example/extrato/mapper/RecentesMapperTest.java` — verificar: `acao.tipo`, `data.titulo`, `tipo.titulo`, `tipo.icone.token`, `valor.titulo` (com sinal para saida), `valor.estilo` [depende de T011]
- [x] T014 [P] [US1] Reescrever `src/test/java/com/example/extrato/mapper/FuturosMapperTest.java` — mesma estrutura de T013 para futuros [depende de T012]

**Checkpoint**: `mvn test -pl . -Dtest=RecentesMapperTest,FuturosMapperTest` passa. User Story 1 entregável de forma independente.

---

## Phase 4: User Story 2 — Cabeçalho de colunas (Priority: P2)

**Goal**: Todas as `AbaResponse` retornam `cabecalho` como lista com 3 descritores de coluna fixos (`data`, `tipo`, `valor`), inclusive no fallback de circuit breaker.

**Independent Test**: Chamar `GET /api/v1/extratos-filtros` e verificar que `data.abas.RECENTES.cabecalho` é um array com 3 objetos `{titulo, id}`.

### Implementação — US2

- [x] T015 [US2] Modificar `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java` — (a) adicionar constante `CABECALHO_FIXO = List.of(new CabecalhoResponse("Data","data"), ...)`, (b) remover `CurrencyFormatter` do construtor e dos campos, (c) substituir `calcularCabecalho(dados)` por `CABECALHO_FIXO` em `buildAbaRecentes`, `buildAbaFuturos` e `buildAbaFallback` [depende de T005, T009]

### Testes — US2

- [x] T016 [US2] Modificar `src/test/java/com/example/extrato/service/ExtratoFiltrosServiceTest.java` — (a) remover `currencyFormatter` do `setUp` do service, (b) adicionar teste `abaResponse_deveConterCabecalhoFixoComTresColunas` verificando `cabecalho` com 3 itens e ids `["data","tipo","valor"]` [depende de T015]

**Checkpoint**: `mvn test -Dtest=ExtratoFiltrosServiceTest` passa. User Story 2 entregável.

---

## Phase 5: User Story 3 — Paginação consolidada por aba (Priority: P2)

**Goal**: `paginacao` existe exclusivamente dentro de cada `AbaResponse` — nunca na raiz — em todos os cenários (ambas as abas, aba única, fallback).

**Independent Test**: Chamar `GET /api/v1/extratos-filtros?aba=RECENTES` e verificar que `paginacao` não existe na raiz (`$.paginacao` ausente) e existe em `$.data.abas.RECENTES.paginacao`.

### Implementação — US3

- [x] T017 [US3] Modificar `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java` — simplificar `buscarAbaEspecifica`: remover extração de `paginacao` para raiz (paginação já está dentro do `AbaResponse` construído nos métodos `buscarRecentesComCircuitBreaker`/`buscarFuturosComCircuitBreaker`); atualizar chamadas `new ExtratoFiltrosResponse(data, null, erro)` para `new ExtratoFiltrosResponse(data, erro)` em `buscarAmbasAbas` e `buscarAbaEspecifica` [depende de T007, T015]

### Testes — US3

- [x] T018 [US3] Modificar `src/test/java/com/example/extrato/service/ExtratoFiltrosServiceTest.java` — (a) renomear e reescrever `abaRecentes_paginacaoDeveEstarNaRaiz_naoNaAba` → `abaRecentes_paginacaoDeveEstarDentroDeAba` (verificar paginação dentro da aba, não na raiz), (b) remover asserção `assertThat(response.paginacao()).isNull()` de `semAba_paginacaoDeveEstarDentroDeCartaAba` (campo não existe mais), (c) atualizar `tamanhoPaginaSempreDeveSerDez` para ler de `response.data().abas().get("RECENTES").paginacao()` [depende de T017]
- [x] T019 [US3] Modificar `src/test/java/com/example/extrato/integration/ExtratoFiltrosIntegrationTest.java` — (a) substituir `$.data.abas.RECENTES.dados[0].valor` por `$.data.abas.RECENTES.dados[0].valor.titulo`, (b) substituir `$.data.abas.FUTUROS.dados[0].valor` por `$.data.abas.FUTUROS.dados[0].valor.titulo`, (c) adicionar asserção `$.data.abas.RECENTES.dados[0].valor.estilo` = `"NEGATIVO"`, (d) adicionar asserção `$.data.abas.FUTUROS.dados[0].valor.estilo` = `"POSITIVO"` [depende de T017, T011, T012]

**Checkpoint**: `mvn test -Dtest=ExtratoFiltrosServiceTest,ExtratoFiltrosIntegrationTest` passa. User Story 3 entregável.

---

## Phase 6: User Story 4 — Campo `selecionado` omitido para PERSONALIZADO (Priority: P3)

**Goal**: A opção `PERSONALIZADO` no filtro de período não serializa o campo `selecionado` (é `null`). Todas as demais opções continuam com `selecionado: true` ou `selecionado: false`.

**Independent Test**: Chamar `GET /api/v1/extratos-filtros` e verificar que em `filtros[0].opcoes`, o objeto com `id="PERSONALIZADO"` não contém o campo `selecionado`.

### Implementação — US4

- [x] T020 [US4] Modificar `src/main/java/com/example/extrato/mapper/FiltrosMapper.java` — (a) em `buildFiltroPeriodo`: calcular `Map<String,String> metadados = metadadosPeriodo(p)` e definir `Boolean sel = (metadados != null) ? null : (p == selecionado)`; (b) em `resolverTituloSelecionado`: substituir `.filter(OpcaoFiltroResponse::selecionado)` por `.filter(o -> Boolean.TRUE.equals(o.selecionado()))` [depende de T006]

### Testes — US4

- [x] T021 [US4] Modificar `src/test/java/com/example/extrato/mapper/FiltrosMapperTest.java` — atualizar todos os usos de `.filter(OpcaoFiltroResponse::selecionado)` para `.filter(o -> Boolean.TRUE.equals(o.selecionado()))` nos testes `apenasUmaOpcaoDeveEstarSelecionadaPorFiltro` e `opcaoSelecionadaDeveTerMesmoTituloDoFiltroPai` [depende de T020]

**Checkpoint**: `mvn test -Dtest=FiltrosMapperTest` passa. User Story 4 entregável.

---

## Phase 7: Polish & Validação Final

**Purpose**: Garantir que nenhuma referência antiga permanece e que o conjunto completo de testes passa.

- [x] T022 [P] Verificar ausência de referências a `CategoriaResponse` no código — buscar em `src/` por import ou uso do tipo
- [x] T023 [P] Verificar ausência de referências ao campo `paginacao` na raiz de `ExtratoFiltrosResponse` — buscar em testes por `response.paginacao()`
- [x] T024 Rodar suite completa: `mvn test` — todos os testes devem passar (unit + integration + actuator)

**Checkpoint**: `mvn test` verde. Feature pronta para revisão e merge.

---

## Dependencies & Execution Order

### Dependências entre phases

```
Phase 2 (Foundational)
  └── Phase 3 US1 (T011, T012 dependem de T008, T010)
  └── Phase 4 US2 (T015 depende de T005, T009)
        └── Phase 5 US3 (T017 depende de T007, T015)
              └── Phase 5 US3 tests (T018, T019 dependem de T017)
  └── Phase 6 US4 (T020 depende de T006)

Phase 3 US1 + Phase 4 US2 podem ser feitas em paralelo após Phase 2
Phase 6 US4 pode ser feita em paralelo com Phase 3 e 4
```

### Dependências dentro de Phase 2

```
Batch A (T001–T004): paralelos entre si — sem dependências
Batch B (T005–T007): paralelos entre si e com Batch A — sem dependências
Batch C (T008): depende de T001–T004
           T009: depende de T005, T008
Batch D (T010): depende de T008
```

### Oportunidades de paralelismo

- **Phase 2 Batch A + B**: T001, T002, T003, T004, T005, T006, T007 — todos em paralelo
- **Phase 3 US1**: T011 e T012 em paralelo; T013 e T014 em paralelo após T011/T012
- **Phase 4 US2 + Phase 6 US4**: podem rodar em paralelo (arquivos distintos)
- **Phase 7**: T022 e T023 em paralelo

---

## Parallel Execution Examples

### Phase 2 — Batch A + B (7 tasks em paralelo)

```
Task: "Criar AcaoResponse.java"         → T001
Task: "Criar CelulaResponse.java"       → T002
Task: "Criar IconeResponse.java"        → T003
Task: "Criar TipoCelulaResponse.java"   → T004
Task: "Modificar CabecalhoResponse.java"→ T005
Task: "Modificar OpcaoFiltroResponse.java" → T006
Task: "Modificar ExtratoFiltrosResponse.java" → T007
```

### Phase 3 — Mappers (paralelo após Phase 2)

```
Task: "Reescrever RecentesMapper.java"  → T011
Task: "Reescrever FuturosMapper.java"   → T012
```

---

## Implementation Strategy

### MVP (User Story 1 apenas)

1. Completar Phase 2: Foundational
2. Completar Phase 3: User Story 1 (T011–T014)
3. **PARAR e VALIDAR**: `mvn test -Dtest=RecentesMapperTest,FuturosMapperTest`
4. Os lançamentos já chegam com a nova estrutura tipada — front-end pode consumir

### Entrega incremental

1. Phase 2 → DTOs compilam ✅
2. Phase 3 (US1) → lançamentos tipados ✅ → validar mappers
3. Phase 4 (US2) → cabeçalho de colunas ✅ → validar service
4. Phase 5 (US3) → paginação consolidada ✅ → validar integration tests
5. Phase 6 (US4) → selecionado omitido ✅ → validar filtros
6. Phase 7 → suite completa verde ✅ → PR

---

## Notes

- `[P]` = arquivos distintos, sem dependências incompletas — podem rodar em paralelo
- Cada user story é independentemente testável após seu checkpoint
- Não há novas dependências no `pom.xml` — nenhum `mvn install` ou cache necessário
- Após T010 (delete CategoriaResponse), se o build quebrar, há referência não mapeada — investigar antes de avançar
- T015 e T017 modificam o mesmo arquivo (`ExtratoFiltrosService.java`) — fazer em sequência (T015 antes de T017)
