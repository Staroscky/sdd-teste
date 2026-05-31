# Data Model: Extrato com Filtros

## Entidades de Request

### ExtratoFiltrosRequest
Record Java — parâmetros de entrada do endpoint BFF.

| Campo | Tipo | Obrigatoriedade | Valores Válidos |
|-------|------|-----------------|-----------------|
| `periodo` | `Periodo` (enum) | Obrigatório | `7_DIAS`, `15_DIAS`, `30_DIAS`, `60_DIAS`, `PERSONALIZADO` |
| `dataInicial` | `LocalDate` | Obrigatório se `periodo=PERSONALIZADO` | formato `yyyy-MM-dd` |
| `dataFinal` | `LocalDate` | Obrigatório se `periodo=PERSONALIZADO` | formato `yyyy-MM-dd`, >= `dataInicial` |
| `entradaSaida` | `EntradaSaida` (enum) | Obrigatório | `ENTRADA`, `SAIDA`, `ENTRADA_SAIDA` |
| `lancamento` | `TipoLancamento` (enum) | Obrigatório | `C`, `D` |

**Regra de validação**: Se `periodo=PERSONALIZADO`, `dataInicial` e `dataFinal` são
obrigatórios; caso contrário, são ignorados. `dataFinal` deve ser >= `dataInicial`.

---

## Entidades de Resposta BFF

### ExtratoFiltrosResponse
Record Java — contrato de saída do BFF.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `ordemAbas` | `List<String>` | Ordem de exibição das abas: `["RECENTES", "FUTUROS"]` |
| `abas` | `Map<String, AbaResponse>` | Mapa chave=nome da aba → conteúdo |

### AbaResponse
Record Java — conteúdo de uma aba (RECENTES ou FUTUROS).

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `filtros` | `List<FiltroResponse>` | Filtros ativos para exibição |
| `cabecalho` | `CabecalhoResponse` | Resumo formatado da aba |
| `dados` | `List<LancamentoResponse>` | Lançamentos formatados para renderização |

### FiltroResponse
Record Java — representa um filtro ativo.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `chave` | `String` | Identificador do filtro (ex.: `"periodo"`, `"entradaSaida"`) |
| `valor` | `String` | Valor exibível do filtro (ex.: `"7 dias"`, `"Entrada e Saída"`) |

### CabecalhoResponse
Record Java — resumo formatado da aba.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `totalEntradas` | `String` | Total de entradas formatado em BRL (ex.: `"R$ 500,00"`) |
| `totalSaidas` | `String` | Total de saídas formatado em BRL |
| `saldo` | `String` | Saldo resultante formatado em BRL |

### LancamentoResponse
Record Java — lançamento individual formatado para o frontend.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `tipo` | `String` | Tipo do lançamento (ex.: `"lancamentos"`) |
| `acao` | `String` | `"entrada"` ou `"saida"` |
| `impacto` | `String` | Nome/descrição do impacto (ex.: `"Joao Pedro"`) |
| `valor` | `String` | Valor formatado em BRL (ex.: `"R$ 100,00"`) |
| `lancamento` | `String` | `"C"` ou `"D"` |
| `categoria` | `CategoriaResponse` | Categoria formatada |
| `estilo` | `String` | Estilo visual resolvido (ex.: `"entrada"`, `"saida"`) |

### CategoriaResponse
Record Java — sub-objeto de categoria.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | `String` | UUID da categoria |
| `nome` | `String` | Nome da categoria (ex.: `"Transferencia"`) |

---

## Entidades Upstream

### RecentesUpstreamResponse
Record Java — resposta da API upstream de recentes.

```json
{
  "data": [ LancamentoRecenteUpstream ]
}
```

| Campo | Tipo |
|-------|------|
| `data` | `List<LancamentoRecenteUpstream>` |

### LancamentoRecenteUpstream
Record Java.

| Campo | Tipo | Exemplo |
|-------|------|---------|
| `tipo` | `String` | `"lancamentos"` |
| `acao` | `String` | `"saida"` |
| `impacto` | `String` | `"Joao Pedro"` |
| `valor` | `BigDecimal` | `100.00` |
| `lancamento` | `String` | `"D"` |
| `categoria` | `CategoriaUpstream` | — |

### FuturosUpstreamResponse
Record Java — resposta da API upstream de futuros.

```json
{
  "data": {
    "lancamentos_futuros": [ LancamentoFuturoUpstream ]
  }
}
```

| Campo | Tipo |
|-------|------|
| `data` | `FuturosDataUpstream` |

### FuturosDataUpstream
Record Java — wrapper do campo `data` da upstream de futuros.

| Campo | Tipo |
|-------|------|
| `lancamentosFuturos` | `List<LancamentoFuturoUpstream>` |

*(mapeado de `lancamentos_futuros` via `@JsonProperty`)*

### LancamentoFuturoUpstream
Record Java — mesma estrutura de `LancamentoRecenteUpstream`.

| Campo | Tipo | Exemplo |
|-------|------|---------|
| `tipo` | `String` | `"lancamentos"` |
| `acao` | `String` | `"entrada"` |
| `impacto` | `String` | `"Joao Pedro"` |
| `valor` | `BigDecimal` | `100.00` |
| `lancamento` | `String` | `"C"` |
| `categoria` | `CategoriaUpstream` | — |

### CategoriaUpstream
Record Java.

| Campo | Tipo | Exemplo |
|-------|------|---------|
| `id` | `String` | UUID |
| `nome` | `String` | `"Transferencia"` |

---

## Mapeamento Upstream → BFF

| Campo upstream | Campo BFF | Transformação |
|----------------|-----------|---------------|
| `valor` (BigDecimal) | `valor` (String) | `NumberFormat` BRL (`R$ X,XX`) |
| `acao` | `estilo` | direto (define estilo visual) |
| `categoria` | `categoria` | CategoriaUpstream → CategoriaResponse |
| lista de recentes | `abas.RECENTES.dados` | RecentesMapper |
| lista de futuros | `abas.FUTUROS.dados` | FuturosMapper |
