# Data Model: Refatoração de Clean Code e Boas Práticas

**Feature**: 002-clean-code-refactoring | **Date**: 2026-05-31

> Esta feature não altera nenhum DTO de request ou response. O modelo de dados público
> permanece idêntico ao definido em `specs/001-extrato-filtros/data-model.md`.
> Este arquivo documenta apenas os novos artefatos internos introduzidos pela refatoração.

---

## Novos Artefatos Internos

### `CurrencyFormatter` (util)

| Atributo | Tipo | Descrição |
|----------|------|-----------|
| `FORMAT` | `ThreadLocal<NumberFormat>` | Instância de `NumberFormat` pt-BR por thread |

**Método público**:
- `format(BigDecimal valor): String` — retorna o valor formatado em BRL (ex.: `R$ 1.250,00`)

**Ciclo de vida**: Singleton Spring (`@Component`); `ThreadLocal` é inicializado lazily por thread.

---

### `UpstreamParams` (record privado interno em `ExtratoFiltrosService`)

| Campo | Tipo | Origem |
|-------|------|--------|
| `periodo` | `String` | `request.periodo().id` |
| `entradaSaida` | `String` | `request.entradaSaida().name()` |
| `lancamento` | `String` | `request.lancamento().name()` |
| `pagina` | `int` | `request.pagina()` |

**Factory**: `UpstreamParams.from(ExtratoFiltrosRequest): UpstreamParams`

**Visibilidade**: `private` — uso exclusivo dentro de `ExtratoFiltrosService`.

---

## Artefatos Sem Alteração

- `ExtratoFiltrosRequest` — sem mudança
- `ExtratoFiltrosResponse` / `AbaResponse` / `LancamentoResponse` — sem mudança
- `RecentesUpstreamResponse` / `FuturosUpstreamResponse` — sem mudança
- `ErrorResponse` — sem mudança
