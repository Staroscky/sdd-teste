# Tasks: RefatoraÃ§Ã£o de Clean Code e Boas PrÃ¡ticas

**Input**: Design documents from `specs/002-clean-code-refactoring/`

**Prerequisites**: plan.md âœ… | spec.md âœ… | research.md âœ… | data-model.md âœ…

**OrganizaÃ§Ã£o**: Tarefas agrupadas por User Story para permitir implementaÃ§Ã£o e teste independentes.
As histÃ³rias US1 e US2 tÃªm dependÃªncia no componente criado na fase fundacional (T002); US3 e US4
sÃ£o completamente independentes entre si e das demais.

---

## Phase 1: Setup

**Objetivo**: Criar o Ãºnico artefato novo de produÃ§Ã£o que bloqueia as demais histÃ³rias.

- [x] T001 Criar pacote `src/main/java/com/example/extrato/util/` e classe `CurrencyFormatter.java` com `ThreadLocal<NumberFormat>` e mÃ©todo `format(BigDecimal): String`
- [x] T002 Criar `src/test/java/com/example/extrato/util/CurrencyFormatterTest.java` com testes: formato correto para valor positivo, valor zero, valor negativo e chamada concorrente (thread-safety bÃ¡sica)

**Checkpoint**: `CurrencyFormatter` compilando e testes passando â€” base para US1 e US2.

---

## Phase 2: User Story 1 â€” FormataÃ§Ã£o BRL centralizada (Priority: P1) ðŸŽ¯ MVP

**Goal**: Eliminar duplicaÃ§Ã£o de `formatarBRL` e `PT_BR` nos dois mappers; ambos delegam ao `CurrencyFormatter` compartilhado.

**Independent Test**: ApÃ³s esta fase, `RecentesMapper` e `FuturosMapper` nÃ£o contÃªm mais `NumberFormat` nem `Locale`; os testes `RecentesMapperTest` e `FuturosMapperTest` continuam passando sem alteraÃ§Ã£o de asserÃ§Ãµes.

### ImplementaÃ§Ã£o

- [x] T003 [P] [US1] Remover constante `PT_BR`, campo `NumberFormat`, mÃ©todo `formatarBRL` e imports de `Locale`/`NumberFormat` de `src/main/java/com/example/extrato/mapper/RecentesMapper.java`; injetar `CurrencyFormatter` por construtor e substituir chamada
- [x] T004 [P] [US1] Remover constante `PT_BR`, campo `NumberFormat`, mÃ©todo `formatarBRL` e imports de `Locale`/`NumberFormat` de `src/main/java/com/example/extrato/mapper/FuturosMapper.java`; injetar `CurrencyFormatter` por construtor e substituir chamada

**Checkpoint**: `mvn test -pl . -Dtest=RecentesMapperTest,FuturosMapperTest` passa. Nenhuma ocorrÃªncia de `NumberFormat` nos dois mappers.

---

## Phase 3: User Story 2 â€” Service com SRP (Priority: P2)

**Goal**: `ExtratoFiltrosService` torna-se orquestrador puro: sem lÃ³gica de formataÃ§Ã£o, sem bloco de extraÃ§Ã£o de parÃ¢metros duplicado, sem string literals soltas.

**Independent Test**: ApÃ³s esta fase, `ExtratoFiltrosServiceTest` passa sem alteraÃ§Ã£o; `ExtratoFiltrosIntegrationTest` passa sem alteraÃ§Ã£o. Grep por `"RECENTES"` e `"FUTUROS"` retorna 0 literais no service.

### ImplementaÃ§Ã£o

- [x] T005 [US2] Adicionar record privado interno `UpstreamParams(String periodo, String entradaSaida, String lancamento, int pagina)` com factory `from(ExtratoFiltrosRequest)` em `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java`
- [x] T006 [US2] Substituir os dois blocos de extraÃ§Ã£o de parÃ¢metros em `buscarAmbasAbas` e `buscarAbaEspecifica` pela chamada `UpstreamParams.from(request)` em `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java`
- [x] T007 [US2] Adicionar constantes `ABA_RECENTES = Aba.RECENTES.name()` e `ABA_FUTUROS = Aba.FUTUROS.name()` e substituir todas as ocorrÃªncias das string literals `"RECENTES"` e `"FUTUROS"` em `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java`
- [x] T008 [US2] Injetar `CurrencyFormatter` no construtor de `ExtratoFiltrosService`; remover constante `PT_BR`, imports de `Locale`/`NumberFormat` e substituir `NumberFormat.getCurrencyInstance` em `calcularCabecalho` pela chamada `currencyFormatter.format(BigDecimal.ZERO)` em `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java`

**Checkpoint**: `mvn test -pl . -Dtest=ExtratoFiltrosServiceTest,ExtratoFiltrosIntegrationTest` passa. Service sem imports de `NumberFormat` ou `Locale`.

---

## Phase 4: User Story 3 â€” ValidaÃ§Ãµes consolidadas (Priority: P3)

**Goal**: Toda validaÃ§Ã£o de parÃ¢metros de request vive em `ExtratoFiltrosService.validarParametros`; o controller nÃ£o contÃ©m mais lÃ³gica de validaÃ§Ã£o de negÃ³cio.

**Independent Test**: ApÃ³s esta fase, chamar o endpoint com `pagina=0` retorna HTTP 400 com `PARAMETRO_INVALIDO`; chamar com `periodo=PERSONALIZADO` sem datas tambÃ©m retorna HTTP 400 â€” ambos os casos verificados por `ExtratoFiltrosIntegrationTest` existente ou por smoke test manual.

### ImplementaÃ§Ã£o

- [x] T009 [US3] Mover regra `if (pagina < 1) throw new IllegalArgumentException(...)` do controller para o mÃ©todo `validarParametros` em `src/main/java/com/example/extrato/service/ExtratoFiltrosService.java`; remover o `if` de `src/main/java/com/example/extrato/controller/ExtratoFiltrosController.java`

**Checkpoint**: `mvn test` completo passa. Controller sem nenhum `if` de validaÃ§Ã£o de negÃ³cio.

---

## Phase 5: User Story 4 â€” FiltrosMapper sem stream duplicado (Priority: P4)

**Goal**: O padrÃ£o `filter â†’ map â†’ findFirst â†’ orElse` existe uma Ãºnica vez em `FiltrosMapper`, extraÃ­do em mÃ©todo privado `resolverTituloSelecionado`.

**Independent Test**: ApÃ³s esta fase, `FiltrosMapperTest` passa sem alteraÃ§Ã£o; grep pelo stream duplicado retorna 0 ocorrÃªncias nos mÃ©todos `buildFiltroPeriodo` e `buildFiltroEntradaSaida`.

### ImplementaÃ§Ã£o

- [x] T010 [US4] Extrair mÃ©todo privado `resolverTituloSelecionado(List<OpcaoFiltroResponse> opcoes, String placeholder): String` em `src/main/java/com/example/extrato/mapper/FiltrosMapper.java` e substituir os dois streams duplicados pela chamada ao mÃ©todo extraÃ­do

**Checkpoint**: `mvn test -pl . -Dtest=FiltrosMapperTest` passa. Apenas 1 ocorrÃªncia do stream `filter(selecionado)` no arquivo.

---

## Phase 6: Polish & VerificaÃ§Ã£o Final

**Objetivo**: Garantir que todos os testes passam, nenhum resÃ­duo de cÃ³digo duplicado permanece e a suÃ­te de integraÃ§Ã£o valida o contrato inalterado.

- [x] T011 [P] Executar `mvn test` completo e confirmar que todos os testes passam (unitÃ¡rios + integraÃ§Ã£o)
- [x] T012 [P] Verificar via grep que `NumberFormat` aparece **apenas** em `CurrencyFormatter.java` no cÃ³digo de produÃ§Ã£o
- [x] T013 [P] Verificar via grep que as strings literais `"RECENTES"` e `"FUTUROS"` nÃ£o aparecem em `ExtratoFiltrosService.java`
- [x] T014 [P] Verificar via grep que o bloco de extraÃ§Ã£o de parÃ¢metros (`periodo.id`, `entradaSaida.name()`, `lancamento.name()`) aparece apenas no mÃ©todo `UpstreamParams.from` dentro do service
- [x] T015 Atualizar `specs/002-clean-code-refactoring/plan.md` com qualquer desvio de implementaÃ§Ã£o identificado durante a execuÃ§Ã£o

---

## DependÃªncias e Ordem de ExecuÃ§Ã£o

### DependÃªncias entre fases

- **Phase 1 (Setup)**: Sem dependÃªncias â€” iniciar imediatamente
- **Phase 2 (US1)**: Depende de T001/T002 (Phase 1) â€” `CurrencyFormatter` deve existir
- **Phase 3 (US2)**: Depende de T001/T002 (Phase 1) â€” usa `CurrencyFormatter`; pode rodar em paralelo com Phase 2
- **Phase 4 (US3)**: Independente de US1 e US2 â€” pode iniciar apÃ³s Phase 1 ou mesmo em paralelo
- **Phase 5 (US4)**: Totalmente independente â€” pode iniciar a qualquer momento
- **Phase 6 (Polish)**: Depende de todas as fases anteriores

### Parallelismo dentro das fases

- **T003 e T004** (Phase 2): arquivos diferentes â†’ executar em paralelo
- **T005â€“T008** (Phase 3): mesmo arquivo â†’ executar sequencialmente
- **T011â€“T014** (Phase 6): verificaÃ§Ãµes independentes â†’ executar em paralelo

### Oportunidade de paralelismo entre histÃ³rias

```
Phase 1: T001 â†’ T002
               â†“
    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
  Phase 2    Phase 3    Phase 4    Phase 5
(T003+T004) (T005â†’T008)  (T009)    (T010)
    â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
               â†“
           Phase 6
        (T011â€“T015)
```

---

## EstratÃ©gia de ImplementaÃ§Ã£o

### MVP (US1 apenas)

1. Completar Phase 1 (T001, T002)
2. Completar Phase 2 (T003, T004)
3. **Validar**: grep e testes confirmam zero duplicaÃ§Ã£o de `formatarBRL`
4. Demais histÃ³rias sÃ£o melhorias incrementais independentes

### Entrega Incremental

1. Phase 1 â†’ `CurrencyFormatter` pronto e testado
2. Phase 2 â†’ mappers limpos (MVP de deduplicaÃ§Ã£o)
3. Phase 3 â†’ service com SRP
4. Phase 4 â†’ validaÃ§Ãµes consolidadas
5. Phase 5 â†’ `FiltrosMapper` limpo
6. Phase 6 â†’ verificaÃ§Ã£o final e fechamento

---

## Notas

- `[P]` = tarefas em arquivos diferentes, sem dependÃªncias entre si
- `[USn]` = rastreabilidade para a User Story n do spec.md
- Nenhum teste tem asserÃ§Ã£o alterada â€” a refatoraÃ§Ã£o Ã© transparente para os testes existentes
- Commits sugeridos: um por fase concluÃ­da
