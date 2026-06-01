# Feature Specification: Ajuste do Contrato de Resposta da API de Extrato

**Feature Branch**: `004-ajuste-contrato-response`

**Created**: 2026-05-31

**Status**: Draft

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Front-end renderiza tabela de lançamentos com colunas tipadas (Priority: P1)

O front-end recebe cada lançamento como um objeto estruturado com células tipadas (`data`, `tipo`, `valor`, `acao`), cada uma com `titulo` e `estilo`, permitindo renderização direta sem transformações no cliente.

**Por que esta prioridade**: É a mudança de maior impacto visual e a que bloqueia o desenvolvimento do front-end. Sem a nova estrutura de `dados`, o componente de tabela não pode ser construído.

**Independent Test**: Chamar `GET /api/v1/extratos-filtros` com parâmetros válidos e validar que `data.abas.RECENTES.dados[0]` contém os campos `acao.tipo`, `data.titulo`, `data.estilo`, `tipo.titulo`, `tipo.icone.token`, `tipo.icone.estilo`, `valor.titulo` e `valor.estilo`.

**Acceptance Scenarios**:

1. **Given** um lançamento de saída no upstream, **When** o BFF processa a resposta, **Then** `dados[i].valor.titulo` contém o sinal negativo ("- R$ X,XX") e `dados[i].valor.estilo` é `"NEGATIVO"`.
2. **Given** um lançamento de entrada no upstream, **When** o BFF processa a resposta, **Then** `dados[i].valor.titulo` não contém sinal negativo ("R$ X,XX") e `dados[i].valor.estilo` é `"POSITIVO"`.
3. **Given** qualquer lançamento, **When** o BFF processa a resposta, **Then** `dados[i].acao.tipo` é `"DEEPLINK"`, `dados[i].data.estilo` é `"NEUTRO"` e `dados[i].tipo.icone.estilo` é `"NEUTRO"`.
4. **Given** um lançamento com `categoria.nome = "Transferencia"`, **When** o BFF processa a resposta, **Then** `dados[i].tipo.icone.token` é `"ids_transferencia"`.

---

### User Story 2 — Front-end renderiza cabeçalho de colunas a partir da resposta (Priority: P2)

O front-end recebe a definição das colunas da tabela como uma lista de descritores `[{titulo, id}]`, eliminando a necessidade de hardcode no cliente e permitindo configuração server-driven das colunas exibidas.

**Por que esta prioridade**: Desacopla a definição de colunas do front-end, mas não bloqueia o desenvolvimento da tabela de lançamentos — pode ser implementado em paralelo.

**Independent Test**: Chamar `GET /api/v1/extratos-filtros` e validar que `data.abas.RECENTES.cabecalho` é uma lista com exatamente 3 itens: `[{titulo:"Data", id:"data"}, {titulo:"Tipo", id:"tipo"}, {titulo:"Valor", id:"valor"}]`.

**Acceptance Scenarios**:

1. **Given** uma requisição para qualquer aba, **When** o BFF responde, **Then** `cabecalho` é uma lista (array) com 3 objetos `{titulo, id}`.
2. **Given** uma requisição para aba RECENTES ou FUTUROS, **When** o BFF responde, **Then** as colunas são sempre `data`, `tipo`, `valor` nessa ordem.
3. **Given** circuit breaker aberto (fallback), **When** o BFF responde, **Then** `cabecalho` ainda contém as 3 colunas fixas.

---

### User Story 3 — Paginação consolidada dentro de cada aba (Priority: P2)

A paginação deixa de existir na raiz do response e passa a existir exclusivamente dentro de cada `AbaResponse`, independentemente de a requisição ser para uma aba específica ou para ambas as abas.

**Por que esta prioridade**: Simplifica o contrato e evita inconsistências, mas é uma mudança de posição de campo, não de comportamento — o front-end precisa apenas ler do novo local.

**Independent Test**: Chamar `GET /api/v1/extratos-filtros` com `aba=RECENTES` e validar que `paginacao` não existe na raiz do JSON mas existe em `data.abas.RECENTES.paginacao`.

**Acceptance Scenarios**:

1. **Given** requisição sem `aba` (ambas as abas), **When** o BFF responde com dados paginados, **Then** `paginacao` existe dentro de cada aba e **não existe** na raiz do response.
2. **Given** requisição com `aba=RECENTES`, **When** o BFF responde, **Then** `paginacao` existe em `data.abas.RECENTES.paginacao` e **não existe** na raiz.
3. **Given** upstream sem paginação, **When** o BFF responde, **Then** `paginacao` é omitido dentro da aba (null / campo ausente).

---

### User Story 4 — Opção PERSONALIZADO em filtros omite campo `selecionado` (Priority: P3)

A opção `PERSONALIZADO` no filtro de período não exibe o campo `selecionado` na resposta, pois sua semântica é diferente das demais opções (seleciona-se via metadados de data). As demais opções continuam exibindo `selecionado: true/false`.

**Por que esta prioridade**: Melhoria cosmética no contrato; não impacta a lógica de negócio do front-end.

**Independent Test**: Chamar `GET /api/v1/extratos-filtros` e verificar que na lista `filtros[0].opcoes`, o objeto com `id="PERSONALIZADO"` não contém o campo `selecionado`.

**Acceptance Scenarios**:

1. **Given** qualquer requisição, **When** o BFF responde, **Then** a opção `PERSONALIZADO` em `filtros` não possui o campo `selecionado`.
2. **Given** qualquer requisição, **When** o BFF responde, **Then** todas as demais opções de filtro possuem `selecionado: true` ou `selecionado: false`.

---

### Edge Cases

- O que acontece quando o campo `acao` do upstream não é "saida" nem "entrada"? → Tratado como entrada (estilo POSITIVO, sem sinal negativo).
- O que acontece quando `categoria` é nulo no upstream? → `tipo.icone.token` deve ser um valor default ou omitido graciosamente.
- O que acontece quando a lista de lançamentos está vazia? → `dados` é lista vazia, `cabecalho` permanece com as 3 colunas fixas.
- O que acontece com o circuit breaker aberto? → `dados` vazio, `filtros` vazio, `cabecalho` com 3 colunas fixas, `paginacao` omitido.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O campo `dados` em cada `AbaResponse` DEVE ter estrutura `{acao: {tipo, metadados}, data: {titulo, estilo}, tipo: {titulo, icone: {token, estilo}}, valor: {titulo, estilo}}`.
- **FR-002**: O campo `acao.tipo` de cada lançamento DEVE ser sempre `"DEEPLINK"`.
- **FR-003**: O campo `acao.metadados.params` de cada lançamento DEVE ser sempre `"a definir"`.
- **FR-004**: O campo `data.titulo` DEVE conter o valor do campo `lancamento` recebido do upstream.
- **FR-005**: O campo `data.estilo` DEVE ser sempre `"NEUTRO"`.
- **FR-006**: O campo `tipo.titulo` DEVE ser o campo `acao` do upstream capitalizado (ex: "saida" → "Saida").
- **FR-007**: O campo `tipo.icone.token` DEVE ser `"ids_"` concatenado com `categoria.nome` em minúsculas do upstream.
- **FR-008**: O campo `tipo.icone.estilo` DEVE ser sempre `"NEUTRO"`.
- **FR-009**: O campo `valor.titulo` DEVE ser `"- R$ X,XX"` para saídas e `"R$ X,XX"` para entradas.
- **FR-010**: O campo `valor.estilo` DEVE ser `"NEGATIVO"` para saídas e `"POSITIVO"` para entradas.
- **FR-011**: O campo `cabecalho` em cada `AbaResponse` DEVE ser uma lista com exatamente 3 itens fixos: `[{titulo:"Data", id:"data"}, {titulo:"Tipo", id:"tipo"}, {titulo:"Valor", id:"valor"}]`.
- **FR-012**: O campo `paginacao` DEVE existir apenas dentro de cada `AbaResponse`, nunca na raiz do response.
- **FR-013**: O campo `selecionado` da opção `PERSONALIZADO` DEVE ser omitido da resposta (nulo, não serializado).
- **FR-014**: Todas as demais opções de filtro DEVEM conter `selecionado` como booleano (`true` ou `false`).
- **FR-015**: O response raiz DEVE conter apenas `data` (e `erro` quando aplicável); o campo `paginacao` DEVE ser removido da raiz.

### Key Entities

- **LancamentoResponse**: Representa um item da lista de lançamentos. Contém `acao` (ação navigacional), `data` (célula de data), `tipo` (célula de tipo com ícone), `valor` (célula de valor monetário com estilo).
- **AcaoResponse**: Ação navegacional do item. Campos: `tipo` (string, ex: "DEEPLINK"), `metadados` (mapa de string para string).
- **CelulaResponse**: Célula genérica de exibição. Campos: `titulo` (texto exibido), `estilo` (token de estilo visual, ex: "NEUTRO", "NEGATIVO", "POSITIVO").
- **TipoCelulaResponse**: Célula de tipo com ícone. Campos: `titulo` (texto), `icone` (objeto com `token` e `estilo`).
- **CabecalhoResponse**: Descritor de coluna da tabela. Campos: `titulo` (label da coluna), `id` (identificador da coluna).
- **OpcaoFiltroResponse**: Opção de filtro. Campos: `id`, `titulo`, `selecionado` (Boolean nullable), `metadados` (mapa opcional).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Todos os campos do contrato de resposta correspondem exatamente ao formato especificado, verificável por testes automatizados.
- **SC-002**: O front-end consegue renderizar a tabela de lançamentos sem transformações adicionais, utilizando diretamente os campos `titulo` e `estilo` de cada célula.
- **SC-003**: Nenhum campo `paginacao` existe na raiz do response em nenhum cenário de uso.
- **SC-004**: 100% dos testes unitários e de integração existentes passam após a mudança de contrato.
- **SC-005**: A mudança é retrocompatível com o comportamento do circuit breaker: fallback continua retornando `cabecalho` com 3 colunas e `dados` vazio.

---

## Assumptions

- O upstream continua enviando os campos `acao` ("saida"/"entrada"), `lancamento` (data do lançamento), `categoria.nome` e `valor` no mesmo formato atual.
- O campo `lancamento` do upstream já contém a data no formato que deve ser exibido ao usuário (sem transformação adicional no BFF por ora).
- O filtro "OUTROS" (PIX, BANCARIOS) mencionado no contrato desejado para a aba RECENTES está fora do escopo desta feature — será tratado separadamente, pois requer mudanças no request e no upstream.
- O `CategoriaResponse` existente (`{id, nome}`) se torna obsoleto com esta mudança e pode ser removido.
- O comportamento do circuit breaker (fallback com listas vazias e `ErroResponse`) não muda — apenas a estrutura interna do `AbaResponse` de fallback é atualizada.
