# BFF Response Contract: Ajuste do Contrato de Extrato

**Feature**: `004-ajuste-contrato-response` | **Date**: 2026-05-31  
**Endpoint**: `GET /api/v1/extratos-filtros`

---

## Contrato de Resposta (novo)

### Envelope raiz

```json
{
  "data": { ... },
  "erro": { ... }
}
```

> `erro` é omitido quando `null` (`@JsonInclude(NON_NULL)`).  
> `paginacao` **não existe** mais na raiz.

---

### ExtratoFiltrosData

```json
{
  "ordemAbas": ["RECENTES", "FUTUROS"],
  "abas": {
    "RECENTES": { ... },
    "FUTUROS": { ... }
  }
}
```

---

### AbaResponse

```json
{
  "filtros": [ ... ],
  "cabecalho": [
    { "titulo": "Data",  "id": "data"  },
    { "titulo": "Tipo",  "id": "tipo"  },
    { "titulo": "Valor", "id": "valor" }
  ],
  "dados": [ ... ],
  "paginacao": {
    "paginaAtual": 1,
    "tamanhoPagina": 10,
    "totalPaginas": 5,
    "totalRegistros": 100
  }
}
```

> `paginacao` é omitido quando o upstream não retorna paginação.

---

### FiltroResponse

```json
{
  "id": "periodo",
  "titulo": "7 dias",
  "placeholder": "Periodo",
  "opcoes": [
    { "id": "7_DIAS",       "titulo": "7 dias",        "selecionado": true  },
    { "id": "15_DIAS",      "titulo": "15 dias",       "selecionado": false },
    { "id": "30_DIAS",      "titulo": "30 dias",       "selecionado": false },
    { "id": "PERSONALIZADO","titulo": "Personalizado", "metadados": { "dataMinima": "2021-05-31", "dataMaxima": "2026-05-31" } }
  ]
}
```

> A opção `PERSONALIZADO` **não** contém o campo `selecionado` (omitido por ser `null`).

---

### LancamentoResponse (novo)

```json
{
  "acao": {
    "tipo": "DEEPLINK",
    "metadados": { "params": "a definir" }
  },
  "data": {
    "titulo": "2026-05-25",
    "estilo": "NEUTRO"
  },
  "tipo": {
    "titulo": "Saida",
    "icone": {
      "token": "ids_transferencia",
      "estilo": "NEUTRO"
    }
  },
  "valor": {
    "titulo": "- R$ 100,00",
    "estilo": "NEGATIVO"
  }
}
```

**Regras de `valor.titulo` e `valor.estilo`**:

| Direção  | `valor.titulo`      | `valor.estilo` |
|----------|---------------------|----------------|
| saida    | `"- R$ X.XXX,XX"`   | `"NEGATIVO"`   |
| entrada  | `"R$ X.XXX,XX"`     | `"POSITIVO"`   |

---

### ErroResponse (sem alteração)

```json
{
  "codigo": "UPSTREAM_INDISPONIVEL",
  "mensagem": "Serviço temporariamente indisponível."
}
```

---

## Comportamento no Fallback (Circuit Breaker OPEN)

Quando um upstream está indisponível, a aba afetada retorna:

```json
{
  "filtros": [],
  "cabecalho": [
    { "titulo": "Data",  "id": "data"  },
    { "titulo": "Tipo",  "id": "tipo"  },
    { "titulo": "Valor", "id": "valor" }
  ],
  "dados": [],
  "paginacao": null
}
```

> `cabecalho` sempre presente, mesmo no fallback.

---

## Contrato Anterior (deprecated após esta feature)

| Campo removido/alterado              | Substituído por                          |
|--------------------------------------|------------------------------------------|
| `paginacao` na raiz do response      | `paginacao` dentro de cada `AbaResponse` |
| `cabecalho.totalEntradas`            | `cabecalho[0] = {titulo:"Data", id:"data"}` |
| `cabecalho.totalSaidas`              | `cabecalho[1] = {titulo:"Tipo", id:"tipo"}` |
| `cabecalho.saldo`                    | `cabecalho[2] = {titulo:"Valor", id:"valor"}` |
| `dados[i].tipo` (string)             | `dados[i].tipo.titulo` (objeto)          |
| `dados[i].acao` (string)             | `dados[i].acao.tipo` + `dados[i].acao.metadados` |
| `dados[i].valor` (string BRL)        | `dados[i].valor.titulo` + `dados[i].valor.estilo` |
| `dados[i].lancamento` (string)       | `dados[i].data.titulo`                   |
| `dados[i].impacto` (string)          | ❌ removido                              |
| `dados[i].categoria` (objeto)        | ❌ removido (nome → `tipo.icone.token`)  |
| `dados[i].estilo` (string)           | `dados[i].valor.estilo`                  |
