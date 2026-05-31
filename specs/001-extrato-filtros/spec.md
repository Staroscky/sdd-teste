# Feature Specification: Extrato com Filtros

**Feature Branch**: `001-extrato-filtros`

**Created**: 2026-05-31

**Status**: Draft

**Input**: BFF endpoint que agrega lançamentos recentes e futuros de duas APIs upstream em
paralelo, aplica filtros de período e tipo, e retorna um contrato pronto para renderização
direta no frontend — sem expor detalhes internos de erro.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Consultar extrato filtrado (Priority: P1)

O frontend envia uma requisição ao BFF com os parâmetros de período e tipo de lançamento e
recebe uma resposta estruturada contendo duas abas (RECENTES e FUTUROS), com valores
formatados em BRL, datas em pt-BR e estilos visuais já resolvidos, pronta para renderização
sem processamento adicional.

**Why this priority**: É o único fluxo do BFF. Sem ele, o produto não existe.

**Independent Test**: Enviar `GET /api/v1/extratos-filtros?periodo=7_DIAS&entradaSaida=ENTRADA_SAIDA`
e verificar que a resposta contém `ordemAbas`, `abas.RECENTES` e `abas.FUTUROS` com dados
formatados. Testável via WireMock simulando as duas APIs upstream.

**Acceptance Scenarios**:

1. **Given** um período válido e tipo de lançamento informados, **When** o frontend chama
   `GET /api/v1/extratos-filtros`, **Then** o BFF retorna HTTP 200 com as duas abas
   (RECENTES e FUTUROS) preenchidas, valores em BRL, datas em pt-BR, e os dois campos de
   filtros e cabeçalho presentes em cada aba.

2. **Given** `periodo=PERSONALIZADO` sem `data_inicial` e `data_final`, **When** o frontend
   chama o endpoint, **Then** o BFF retorna HTTP 400 com mensagem de erro estruturada — sem
   stack trace no corpo da resposta.

3. **Given** uma das APIs upstream retorna erro (5xx ou timeout), **When** o BFF processa a
   requisição, **Then** retorna HTTP 502 com envelope de erro estruturado — sem stack trace
   exposto.

4. **Given** as duas chamadas upstream completam com sucesso, **When** o BFF processa a
   requisição, **Then** as duas chamadas foram executadas em paralelo (não sequencialmente).

---

### Edge Cases

- `periodo=PERSONALIZADO` com `data_inicial` > `data_final` → HTTP 400 estruturado.
- Upstream retorna lista vazia → aba com `dados: []` e cabeçalho zerado, sem erro.
- Ambas as upstream falham simultaneamente → HTTP 502 único estruturado.
- Uma upstream demora além do timeout configurado → falha rápida, não bloqueia o thread
  principal além do timeout.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE expor `GET /api/v1/extratos-filtros` aceitando os parâmetros
  `periodo`, `data_inicial`, `data_final`, `entradaSaida` e `lancamento`.
- **FR-002**: Quando `periodo=PERSONALIZADO`, os campos `data_inicial` e `data_final` são
  obrigatórios. O sistema DEVE retornar HTTP 400 se ausentes.
- **FR-003**: O sistema DEVE chamar as duas APIs upstream (recentes e futuros) em paralelo.
- **FR-004**: O sistema DEVE transformar as respostas upstream no contrato de resposta BFF —
  estrutura `ordemAbas` + `abas` com `filtros`, `cabecalho` e `dados` formatados.
- **FR-005**: Valores monetários DEVEM ser formatados em BRL (ex.: `R$ 100,00`).
- **FR-006**: Datas DEVEM ser formatadas em pt-BR.
- **FR-007**: O sistema NUNCA DEVE expor stack trace ou mensagem de exceção interna no corpo
  da resposta. Erros DEVEM seguir um envelope estruturado (código, mensagem).
- **FR-008**: O sistema DEVE incluir um `correlationId` em todos os logs da requisição.
- **FR-009**: O sistema DEVE registrar logs estruturados de entrada e saída — tanto do
  request inbound quanto das chamadas upstream outbound.

### Key Entities

- **ExtratoFiltrosRequest**: parâmetros de entrada (`periodo`, `data_inicial`, `data_final`,
  `entradaSaida`, `lancamento`).
- **ExtratoFiltrosResponse**: contrato de saída do BFF — `ordemAbas` (lista de strings) e
  `abas` (mapa de nome → AbaResponse).
- **AbaResponse**: `filtros` (lista), `cabecalho` (resumo formatado), `dados` (lista de
  lançamentos formatados).
- **LancamentoUpstreamRecente**: DTO da API upstream de recentes.
- **LancamentoUpstreamFuturo**: DTO da API upstream de futuros.
- **Categoria**: sub-objeto compartilhado — `id` (UUID) e `nome`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: O endpoint responde em menos de 2 segundos para requisições onde ambas as
  upstream respondem dentro do timeout configurado.
- **SC-002**: 100% das respostas de erro não contêm stack trace ou classe Java no corpo.
- **SC-003**: As duas chamadas upstream são iniciadas simultaneamente — verificável via logs
  de timestamp ou WireMock com delay simulado.
- **SC-004**: O `correlationId` aparece em todos os logs gerados por uma mesma requisição.
- **SC-005**: Todos os valores monetários nas respostas aparecem no formato BRL (R$ X,XX).

## Assumptions

- O BFF é stateless. Nenhum dado é persistido entre requisições.
- Não há autenticação no escopo atual. O endpoint é aberto.
- Não há cache no escopo atual. Cada requisição dispara as chamadas upstream.
- As URLs das APIs upstream são configuradas via `application.yml` e não fazem parte do
  contrato do BFF.
- Os parâmetros de filtro são repassados às upstream conforme mapeamento definido no plano
  de implementação.
- Mobile e outros consumidores que não sejam frontend web estão fora do escopo desta versão.
