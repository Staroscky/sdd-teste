# Feature Specification: Refatoração de Clean Code e Boas Práticas

**Feature Branch**: `002-clean-code-refactoring`

**Created**: 2026-05-31

**Status**: Draft

**Input**: Revisão do código existente do extrato-bff aplicando princípios de clean code, eliminação de duplicação e separação de responsabilidades — sem alteração de contrato de API ou comportamento externo.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Manutenção sem surpresas (Priority: P1)

Um desenvolvedor que precisa adicionar suporte a um novo tipo de lançamento encontra a lógica de formatação monetária em **um único lugar**, altera lá e a mudança se propaga para todos os contextos automaticamente, sem precisar caçar duplicatas.

**Why this priority**: É o benefício central da refatoração — eliminação de duplicação reduz risco de divergência silenciosa entre `RecentesMapper` e `FuturosMapper`.

**Independent Test**: Alterar o formato de moeda no componente centralizado e verificar que recentes e futuros são afetados sem toque adicional.

**Acceptance Scenarios**:

1. **Given** lógica de formatação BRL centralizada, **When** o desenvolvedor modifica o formato, **Then** a mudança aparece em lançamentos recentes e futuros sem outras alterações.
2. **Given** mappers de recentes e futuros, **When** inspecionados, **Then** nenhum dos dois contém código de formatação próprio — ambos delegam ao componente compartilhado.

---

### User Story 2 — Service fácil de entender e testar (Priority: P2)

Um desenvolvedor que lê `ExtratoFiltrosService` em 5 minutos consegue descrever exatamente o que ele faz: orquestrar chamadas upstream em paralelo e montar a resposta — sem precisar decifrar lógica de negócio embutida.

**Why this priority**: Service com SRP claro reduz o tempo de onboarding e facilita testes isolados de cada responsabilidade.

**Independent Test**: O service pode ser testado com mocks apenas para as dependências declaradas; nenhuma lógica de formatação ou cálculo de paginação precisa ser mockada diretamente nele.

**Acceptance Scenarios**:

1. **Given** o service refatorado, **When** lido, **Then** não contém lógica de formatação de moeda, cálculo de cabeçalho ou construção de paginação inline.
2. **Given** extração de parâmetros upstream, **When** invocada, **Then** ocorre em um único lugar (sem duplicação entre fluxo de aba única e ambas as abas).

---

### User Story 3 — Validações de request consolidadas (Priority: P3)

Um desenvolvedor que adiciona uma nova regra de validação de parâmetros sabe exatamente onde colocá-la — há um único ponto de validação, não metade no controller e metade no service.

**Why this priority**: Coesão de validação evita regras espalhadas que são difíceis de encontrar e testar.

**Independent Test**: Remover a validação de `pagina >= 1` do controller e adicioná-la ao ponto centralizado; todos os testes existentes continuam passando sem alteração.

**Acceptance Scenarios**:

1. **Given** validação de `pagina < 1`, **When** movida para o service (ou classe de validação), **Then** o controller não contém mais nenhum `if` de validação de negócio.
2. **Given** validação de `PERSONALIZADO` requer datas, **When** testada, **Then** está no mesmo método/classe que a validação de `pagina`.

---

### User Story 4 — `FiltrosMapper` sem repetição de lógica de seleção (Priority: P4)

Um desenvolvedor que adiciona um novo filtro (ex.: categoria) usa o padrão existente em um único lugar — não copia e cola o stream de resolução de título selecionado.

**Why this priority**: Eliminar padrão repetido em `buildFiltroPeriodo` e `buildFiltroEntradaSaida` antes que se torne o padrão de adição de novos filtros.

**Independent Test**: Após extração do método `resolverTituloSelecionado`, os dois métodos chamam-no e os testes de `FiltrosMapperTest` continuam passando.

**Acceptance Scenarios**:

1. **Given** `FiltrosMapper` refatorado, **When** inspecionado, **Then** o stream `filter → map → findFirst → orElse` aparece apenas em um método privado.
2. **Given** adição de um terceiro filtro, **When** implementado, **Then** reutiliza `resolverTituloSelecionado` sem duplicar o stream.

---

### Edge Cases

- O comportamento da API (resposta JSON, códigos HTTP, mensagens de erro) deve ser idêntico antes e depois da refatoração.
- `calcularCabecalho` atualmente retorna zero para todos os campos — a refatoração não altera esse comportamento (o stub permanece como stub, mas fica documentado ou movido para mapper).
- `NumberFormat` não é thread-safe; a solução centralizada deve garantir segurança em ambientes com virtual threads.
- Nenhuma dependência nova deve ser introduzida além das já declaradas no `plan.md` da feature 001.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE centralizar a lógica de formatação de valor monetário em BRL em um único componente reutilizável, eliminando as cópias em `RecentesMapper` e `FuturosMapper`.
- **FR-002**: O componente de formatação BRL DEVE ser seguro para uso concorrente com virtual threads (thread-safe).
- **FR-003**: `RecentesMapper` e `FuturosMapper` DEVEM delegar a formatação ao componente compartilhado, sem duplicar código de localização ou `NumberFormat`.
- **FR-004**: `ExtratoFiltrosService` DEVE conter apenas lógica de orquestração; construção de `AbaResponse`, cálculo de cabeçalho e montagem de paginação DEVEM ser delegados a mappers ou builders dedicados.
- **FR-005**: A extração de parâmetros para chamadas upstream (`periodo`, `entradaSaida`, `lancamento`, `pagina`) DEVE ocorrer em um único método, eliminando a duplicação entre `buscarAmbasAbas` e `buscarAbaEspecifica`.
- **FR-006**: As strings literais `"RECENTES"` e `"FUTUROS"` usadas como chaves de mapa e lista DEVEM ser substituídas por constantes ou derivadas do enum `Aba`.
- **FR-007**: Toda validação de parâmetros de request (`pagina >= 1` e `PERSONALIZADO` requer datas) DEVE estar concentrada em um único método ou classe, sem divisão entre camadas.
- **FR-008**: `FiltrosMapper` DEVE extrair o padrão de resolução de título selecionado para um método privado compartilhado `resolverTituloSelecionado`.
- **FR-009**: O comportamento externo da API (contrato JSON, códigos HTTP, mensagens de erro) DEVE ser idêntico após a refatoração.
- **FR-010**: Todos os testes existentes (unitários e de integração) DEVEM passar sem modificação de asserções após a refatoração.

### Key Entities

- **LancamentoMapper / CurrencyFormatter**: Componente compartilhado responsável por formatação de valores monetários em BRL e mapeamento comum de lançamentos.
- **ExtratoFiltrosService**: Orquestrador; conhece apenas clientes, mappers e a lógica de paralelismo — sem lógica de apresentação embutida.
- **FiltrosMapper**: Mapper de filtros de UI; com método privado centralizado de resolução de título selecionado.
- **UpstreamParams**: Possível record interno ou método que encapsula a extração dos parâmetros passados para clientes upstream.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nenhum arquivo fonte contém a lógica de `NumberFormat.getCurrencyInstance` duplicada — exatamente **1 ocorrência** no código de produção.
- **SC-002**: `ExtratoFiltrosService` não contém referências a `NumberFormat`, `CurrencyInstance` ou lógica de formatação de string.
- **SC-003**: O bloco de extração de parâmetros upstream (`periodo.id`, `entradaSaida.name()`, `lancamento.name()`, `pagina`) aparece exatamente **1 vez** no código de produção.
- **SC-004**: As strings literais `"RECENTES"` e `"FUTUROS"` aparecem **0 vezes** como literais soltas no service — somente como referência a constantes ou enum.
- **SC-005**: O stream `filter(selecionado) → map(titulo) → findFirst → orElse` aparece exatamente **1 vez** em `FiltrosMapper`.
- **SC-006**: **100%** dos testes existentes continuam passando após a refatoração, sem alteração de asserções.
- **SC-007**: O comportamento da API verificado por testes de integração (`ExtratoFiltrosIntegrationTest`) permanece inalterado.

---

## Assumptions

- A refatoração não introduz novas dependências Maven além das já declaradas.
- O stub de `calcularCabecalho` (retorno sempre zero) é mantido — implementar o cálculo real está fora do escopo desta feature.
- Lombok não é usado no projeto; injeção de dependência continua via construtor explícito.
- O padrão de `ThreadLocal<NumberFormat>` ou método estático utilitário é suficiente para thread-safety com virtual threads; sem necessidade de biblioteca externa.
- Os testes existentes cobrem o comportamento externo e servem como rede de segurança para a refatoração.
