# Research: Ajuste do Contrato de Resposta

**Feature**: `004-ajuste-contrato-response` | **Date**: 2026-05-31

---

## Decisões de Design

### 1. Novos records de resposta — estrutura de células tipadas

**Decision**: Criar records imutáveis separados para cada tipo de célula do novo contrato: `AcaoResponse`, `CelulaResponse`, `IconeResponse`, `TipoCelulaResponse`.

**Rationale**: A Constitution exige Java records para DTOs (imutáveis por construção). Manter cada célula como record separado segue o princípio de responsabilidade única e facilita reutilização futura. Não usar classes aninhadas dentro de `LancamentoResponse` porque dificulta a legibilidade dos imports e dos testes.

**Alternatives considered**:
- Maps aninhados (`Map<String, Object>`) — rejeitado: perde type safety, viola o padrão de records da Constitution.
- Inner records estáticos em `LancamentoResponse` — rejeitado: prejudica testabilidade e reutilização.

---

### 2. `cabecalho` como constante estática no service

**Decision**: Definir `CABECALHO_FIXO` como `List<CabecalhoResponse>` estática no `ExtratoFiltrosService`, reutilizada em todos os `AbaResponse` (normal e fallback).

**Rationale**: As colunas são fixas para todas as abas e cenários. Evita instanciar a mesma lista a cada request. Segue o princípio de não duplicar lógica.

**Alternatives considered**:
- Gerar dinamicamente no mapper — rejeitado: colunas são invariantes de negócio do BFF, não do upstream.
- Injetar via `application.yml` — rejeitado: over-engineering para algo que não vai mudar por configuração.

---

### 3. `OpcaoFiltroResponse.selecionado` — `Boolean` boxed + `@JsonInclude(NON_NULL)`

**Decision**: Mudar o campo `selecionado` de `boolean` (primitivo) para `Boolean` (boxed), aproveitando o `@JsonInclude(NON_NULL)` já presente no record.

**Rationale**: Permite `null` para a opção PERSONALIZADO sem alterar a estrutura do record. A anotação `@JsonInclude(NON_NULL)` já existe no record, então `null` → campo omitido automaticamente. Sem impacto nos outros filtros que continuam recebendo `true`/`false`.

**Alternatives considered**:
- Campo separado `temSelecionado: boolean` — rejeitado: complexidade desnecessária.
- Sub-tipo específico para PERSONALIZADO — rejeitado: over-engineering.

---

### 4. Remoção de `paginacao` da raiz de `ExtratoFiltrosResponse`

**Decision**: Remover o campo `paginacao` de `ExtratoFiltrosResponse`. Para requisições de aba única, a paginação já está dentro do `AbaResponse` (construída em `buildAbaRecentes`/`buildAbaFuturos`) — o código em `buscarAbaEspecifica` que reconstruía a paginação da raiz é eliminado.

**Rationale**: O contrato atual tinha comportamento inconsistente: em chamadas de ambas as abas, paginação ficava dentro de cada aba; em chamada de aba única, ficava na raiz. O novo contrato unifica: sempre dentro da aba.

**Alternatives considered**:
- Manter `paginacao` na raiz como duplicata — rejeitado: viola o novo contrato e cria ambiguidade.

---

### 5. `CategoriaResponse` — remoção

**Decision**: Deletar `CategoriaResponse.java`. Com a nova estrutura de `LancamentoResponse`, o campo `categoria` não existe mais na resposta.

**Rationale**: Código morto. Mantê-lo cria confusão sobre o contrato atual.

**Alternatives considered**:
- Marcar como deprecated — rejeitado: é um DTO interno do BFF, não uma API pública. Remoção imediata é segura.

---

### 6. Regras de mapeamento no mapper (não no service)

**Decision**: Toda a lógica de mapeamento upstream → novo `LancamentoResponse` fica nos mappers (`RecentesMapper`, `FuturosMapper`). O service apenas monta o `AbaResponse` com o resultado.

**Rationale**: SRP — o service coordena, o mapper transforma. Já era assim antes; esta feature apenas atualiza a transformação.

**Rationale da direção**: Upstream `acao` = "saida"/"entrada" → determina sinal do valor e estilo. Upstream `categoria.nome` → `tipo.icone.token` (prefixo "ids_" + lowercase). Upstream `lancamento` → `data.titulo` (pass-through).

---

### 7. Testes — atualização sem adição de novo framework

**Decision**: Atualizar os testes existentes (unit + integration) para o novo contrato. Nenhum novo framework de teste é necessário.

**Rationale**: JUnit 5 + WireMock + MockMvc cobrem todos os cenários. A mudança é de contrato, não de comportamento.
