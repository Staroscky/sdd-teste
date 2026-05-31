# Contract: BFF Response — Extrato com Filtros

## Endpoint

```
GET /api/v1/extratos-filtros
```

### Query Parameters

| Parâmetro | Tipo | Obrigatório | Valores |
|-----------|------|-------------|---------|
| `periodo` | string | sim | `7_DIAS`, `15_DIAS`, `30_DIAS`, `60_DIAS`, `PERSONALIZADO` |
| `data_inicial` | string | se `periodo=PERSONALIZADO` | `yyyy-MM-dd` |
| `data_final` | string | se `periodo=PERSONALIZADO` | `yyyy-MM-dd` |
| `entradaSaida` | string | sim | `ENTRADA`, `SAIDA`, `ENTRADA_SAIDA` |
| `lancamento` | string | sim | `C`, `D` |

---

## Success Response — HTTP 200

```json
{
  "ordemAbas": ["RECENTES", "FUTUROS"],
  "abas": {
    "RECENTES": {
      "filtros": [
        { "chave": "periodo", "valor": "7 dias" },
        { "chave": "entradaSaida", "valor": "Entrada e Saída" }
      ],
      "cabecalho": {
        "totalEntradas": "R$ 500,00",
        "totalSaidas": "R$ 200,00",
        "saldo": "R$ 300,00"
      },
      "dados": [
        {
          "tipo": "lancamentos",
          "acao": "saida",
          "impacto": "Joao Pedro",
          "valor": "R$ 100,00",
          "lancamento": "D",
          "categoria": { "id": "550e8400-e29b-41d4-a716-446655440000", "nome": "Transferencia" },
          "estilo": "saida"
        }
      ]
    },
    "FUTUROS": {
      "filtros": [
        { "chave": "periodo", "valor": "7 dias" },
        { "chave": "entradaSaida", "valor": "Entrada e Saída" }
      ],
      "cabecalho": {
        "totalEntradas": "R$ 100,00",
        "totalSaidas": "R$ 0,00",
        "saldo": "R$ 100,00"
      },
      "dados": [
        {
          "tipo": "lancamentos",
          "acao": "entrada",
          "impacto": "Joao Pedro",
          "valor": "R$ 100,00",
          "lancamento": "C",
          "categoria": { "id": "550e8400-e29b-41d4-a716-446655440000", "nome": "Transferencia" },
          "estilo": "entrada"
        }
      ]
    }
  }
}
```

---

## Error Responses

### HTTP 400 — Parâmetros inválidos

```json
{
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "Os campos data_inicial e data_final são obrigatórios quando periodo=PERSONALIZADO."
}
```

### HTTP 502 — Falha em upstream

```json
{
  "codigo": "UPSTREAM_INDISPONIVEL",
  "mensagem": "Não foi possível obter os dados. Tente novamente em instantes."
}
```

### HTTP 500 — Erro interno

```json
{
  "codigo": "ERRO_INTERNO",
  "mensagem": "Ocorreu um erro inesperado. Tente novamente em instantes."
}
```

**Regra invariante**: Nenhuma resposta de erro contém stack trace, nome de classe Java
ou mensagem de exceção interna.

---

## Contract Stability Rules

Qualquer alteração neste contrato (adição, remoção ou renomeação de campos) requer
aprovação explícita antes da implementação, conforme Princípio II e Seção 7 da
constituição do projeto.
