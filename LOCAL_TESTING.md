# Teste Local — extrato-bff

## Pré-requisitos

| Ferramenta | Versão mínima |
|------------|---------------|
| Java       | 21            |
| Maven      | 3.9+          |
| Docker     | 24+           |
| Docker Compose | v2 (plugin) |

---

## 1. Subir as APIs upstream (WireMock)

```bash
docker compose up -d
```

Isso sobe dois containers:

| Container                  | Porta | Stub de              |
|----------------------------|-------|----------------------|
| `extrato-wiremock-recentes`| 9001  | `/recentes`          |
| `extrato-wiremock-futuros` | 9002  | `/futuros`           |

Para verificar os stubs carregados:

```bash
# Stubs da upstream de recentes
curl http://localhost:9001/__admin/mappings | jq '.mappings[].name'

# Stubs da upstream de futuros
curl http://localhost:9002/__admin/mappings | jq '.mappings[].name'
```

Para parar:

```bash
docker compose down
```

---

## 2. Rodar o BFF

```bash
mvn spring-boot:run
```

O BFF sobe em `http://localhost:8080`.

> Os logs estruturados do Logbook mostrarão cada request inbound e cada chamada
> outbound para as upstreams (incluindo corpo e headers).

---

## 3. Cenários de teste

### 3.1 Sem parâmetros — defaults aplicados (periodo=30_DIAS, entradaSaida=ENTRADA_SAIDA)

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros" | jq
```

**Esperado:**
- `data.ordemAbas: ["RECENTES", "FUTUROS"]`
- `filtros[0].titulo: "30 dias"` e opção `30_DIAS` com `selecionado: true`
- `paginacao` dentro de cada aba (`data.abas.RECENTES.paginacao`, `data.abas.FUTUROS.paginacao`)
- Sem campo `paginacao` na raiz

---

### 3.2 Filtro de período — 7 dias

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros?periodo=7_DIAS" | jq
```

**Esperado:**
- `filtros[0].titulo: "7 dias"` e opção `7_DIAS` com `selecionado: true`
- Duas abas retornadas

---

### 3.3 Somente aba RECENTES

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros?aba=RECENTES" | jq
```

**Esperado:**
- `data.ordemAbas: ["RECENTES"]`
- `data.abas` contém apenas `RECENTES`
- `paginacao` na **raiz** da resposta (não dentro da aba)
- Apenas a API de recentes é chamada (verificável nos logs do Logbook)

---

### 3.4 Somente aba FUTUROS

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros?aba=FUTUROS" | jq
```

**Esperado:**
- `data.ordemAbas: ["FUTUROS"]`
- `paginacao` na raiz com `totalRegistros: 12`, `totalPaginas: 2`

---

### 3.5 Paginação — página 2

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros?pagina=2" | jq
```

**Esperado:**
- `data.abas.RECENTES.paginacao.paginaAtual: 2`
- `data.abas.RECENTES.dados` contém iFood e Uber (itens da página 2)
- `tamanhoPagina: 10` em todas as respostas de paginação

---

### 3.6 Aba específica + página 2

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros?aba=FUTUROS&pagina=2" | jq
```

**Esperado:**
- `paginacao.paginaAtual: 2` na raiz
- `data.abas.FUTUROS.dados` contém IPTU parcela 8

---

### 3.7 Erro 502 — upstream falha (periodo=60_DIAS dispara 500 no stub)

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros?periodo=60_DIAS" | jq
```

**Esperado:**
```json
{
  "codigo": "UPSTREAM_INDISPONIVEL",
  "mensagem": "Não foi possível obter os dados. Tente novamente em instantes."
}
```
HTTP 502.

---

### 3.8 Erro 400 — periodo=PERSONALIZADO sem datas

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros?periodo=PERSONALIZADO" | jq
```

**Esperado:**
```json
{
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "Os campos data_inicial e data_final sao obrigatorios quando periodo=PERSONALIZADO."
}
```
HTTP 400.

---

### 3.9 Erro 400 — pagina menor que 1

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros?pagina=0" | jq
```

**Esperado:**
```json
{
  "codigo": "PARAMETRO_INVALIDO",
  "mensagem": "O parametro pagina deve ser >= 1."
}
```
HTTP 400.

---

### 3.10 Erro 400 — aba com valor inválido

```bash
curl -s "http://localhost:8080/api/v1/extratos-filtros?aba=INVALIDA" | jq
```

**Esperado:** HTTP 400 com `codigo: "PARAMETRO_INVALIDO"`.

---

## 4. Stubs disponíveis

### Upstream Recentes (porta 9001)

| Arquivo | Prioridade | Condição | Resposta |
|---------|-----------|----------|----------|
| `recentes-erro-upstream.json` | 1 | `periodo=60_DIAS` | HTTP 500 |
| `recentes-pagina2.json` | 2 | `pagina=2` | HTTP 200, página 2 (iFood, Uber) |
| `recentes-default.json` | 10 | qualquer | HTTP 200, página 1 (5 lançamentos) |

### Upstream Futuros (porta 9002)

| Arquivo | Prioridade | Condição | Resposta |
|---------|-----------|----------|----------|
| `futuros-erro-upstream.json` | 1 | `periodo=60_DIAS` | HTTP 500 |
| `futuros-pagina2.json` | 2 | `pagina=2` | HTTP 200, página 2 (IPTU) |
| `futuros-default.json` | 10 | qualquer | HTTP 200, página 1 (4 lançamentos) |

---

## 5. Adicionar novos stubs em tempo real

O WireMock aceita stubs via API sem reiniciar:

```bash
curl -X POST http://localhost:9001/__admin/mappings \
  -H "Content-Type: application/json" \
  -d '{
    "request": { "method": "GET", "urlPath": "/recentes",
                 "queryParameters": { "entradaSaida": { "equalTo": "ENTRADA" } } },
    "response": { "status": 200, "headers": { "Content-Type": "application/json" },
                  "jsonBody": { "data": [], "paginacao": { "pagina": 1, "totalPaginas": 0, "totalRegistros": 0 } } }
  }'
```
