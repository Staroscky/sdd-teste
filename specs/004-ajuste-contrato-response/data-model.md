# Data Model: Ajuste do Contrato de Resposta

**Feature**: `004-ajuste-contrato-response` | **Date**: 2026-05-31

---

## Entidades de Resposta (Response DTOs)

### ExtratoFiltrosResponse *(modificado)*

Envelope raiz da resposta.

| Campo  | Tipo                | Nullable | Antes              | Depois             |
|--------|---------------------|----------|--------------------|--------------------|
| data   | ExtratoFiltrosData  | não      | ✅ mantido          | ✅ mantido          |
| paginacao | PaginacaoResponse | sim   | ✅ presente na raiz | ❌ **removido**     |
| erro   | ErroResponse        | sim      | ✅ mantido          | ✅ mantido          |

---

### AbaResponse *(modificado)*

Representa os dados de uma aba (RECENTES ou FUTUROS).

| Campo     | Tipo                       | Nullable | Antes                  | Depois                      |
|-----------|----------------------------|----------|------------------------|-----------------------------|
| filtros   | `List<FiltroResponse>`     | não      | ✅ mantido              | ✅ mantido                   |
| cabecalho | `CabecalhoResponse`        | sim      | objeto único `{totalEntradas, totalSaidas, saldo}` | `List<CabecalhoResponse>` com 3 itens fixos |
| dados     | `List<LancamentoResponse>` | não      | estrutura antiga       | nova estrutura tipada        |
| paginacao | `PaginacaoResponse`        | sim      | ✅ presente na aba      | ✅ mantido (agora também para aba única) |

---

### CabecalhoResponse *(modificado — breaking change)*

Antes: descritor de totais financeiros. Agora: descritor de coluna da tabela.

| Campo  | Tipo   | Descrição              |
|--------|--------|------------------------|
| titulo | String | Label da coluna (ex: "Data") |
| id     | String | Identificador da coluna (ex: "data") |

**Valor fixo para todas as abas**:
```json
[
  { "titulo": "Data",  "id": "data"  },
  { "titulo": "Tipo",  "id": "tipo"  },
  { "titulo": "Valor", "id": "valor" }
]
```

---

### LancamentoResponse *(modificado — breaking change)*

Antes: `{tipo, acao, impacto, valor, lancamento, categoria, estilo}`
Depois: objeto com 4 células tipadas.

| Campo | Tipo              | Descrição                              |
|-------|-------------------|----------------------------------------|
| acao  | AcaoResponse      | Ação navegacional do item              |
| data  | CelulaResponse    | Célula da coluna Data                  |
| tipo  | TipoCelulaResponse| Célula da coluna Tipo (com ícone)      |
| valor | CelulaResponse    | Célula da coluna Valor                 |

---

### AcaoResponse *(novo)*

| Campo    | Tipo                  | Descrição                          |
|----------|-----------------------|------------------------------------|
| tipo     | String                | Tipo de ação (sempre "DEEPLINK")   |
| metadados| `Map<String, String>` | Parâmetros da ação (`params: "a definir"`) |

---

### CelulaResponse *(novo)*

Célula genérica de exibição.

| Campo  | Tipo   | Descrição                                             |
|--------|--------|-------------------------------------------------------|
| titulo | String | Texto a exibir                                        |
| estilo | String | Token de estilo: `"NEUTRO"`, `"NEGATIVO"`, `"POSITIVO"` |

---

### TipoCelulaResponse *(novo)*

Célula de tipo com ícone.

| Campo | Tipo          | Descrição                       |
|-------|---------------|---------------------------------|
| titulo| String        | Texto do tipo (ex: "Saida")     |
| icone | IconeResponse | Ícone associado ao tipo         |

---

### IconeResponse *(novo)*

| Campo | Tipo   | Descrição                                      |
|-------|--------|------------------------------------------------|
| token | String | Token do ícone (ex: "ids_transferencia")       |
| estilo| String | Estilo do ícone (sempre `"NEUTRO"`)            |

---

### OpcaoFiltroResponse *(modificado — campo selecionado)*

| Campo     | Tipo                  | Nullable | Antes              | Depois             |
|-----------|-----------------------|----------|--------------------|--------------------|
| id        | String                | não      | ✅ mantido          | ✅ mantido          |
| titulo    | String                | não      | ✅ mantido          | ✅ mantido          |
| selecionado | `boolean` (primitivo) | não  | sempre presente    | `Boolean` (boxed), omitido para PERSONALIZADO |
| metadados | `Map<String, String>` | sim      | ✅ mantido          | ✅ mantido          |

---

## Regras de Mapeamento (Upstream → Response)

### LancamentoRecenteUpstream / LancamentoFuturoUpstream → LancamentoResponse

| Campo upstream       | Campo response             | Regra                                                     |
|----------------------|----------------------------|-----------------------------------------------------------|
| `acao` = "saida"     | `valor.estilo`             | `"NEGATIVO"`                                              |
| `acao` = "entrada"   | `valor.estilo`             | `"POSITIVO"`                                              |
| `acao`               | `tipo.titulo`              | `capitalize(acao)` → "Saida", "Entrada"                   |
| `acao` = "saida"     | `valor.titulo`             | `"- " + formatBRL(valor)`                                 |
| `acao` = "entrada"   | `valor.titulo`             | `formatBRL(valor)`                                        |
| `lancamento`         | `data.titulo`              | pass-through                                              |
| —                    | `data.estilo`              | sempre `"NEUTRO"`                                         |
| `categoria.nome`     | `tipo.icone.token`         | `"ids_" + nome.toLowerCase()`                             |
| —                    | `tipo.icone.estilo`        | sempre `"NEUTRO"`                                         |
| —                    | `acao.tipo`                | sempre `"DEEPLINK"`                                       |
| —                    | `acao.metadados.params`    | sempre `"a definir"`                                      |

---

## Entidades Removidas

| Entidade           | Razão                                                        |
|--------------------|--------------------------------------------------------------|
| `CategoriaResponse`| Campo `categoria` eliminado da resposta de lançamentos       |
| `paginacao` (raiz) | Consolidado dentro de cada `AbaResponse`                     |
| `CabecalhoResponse` (campos antigos: totalEntradas, totalSaidas, saldo) | Substituído por descriptor de coluna |
