# BFF Response Contract: Resiliência

**Branch**: `003-resiliencia-circuit-breaker` | **Date**: 2026-05-31

**Status**: Aguarda aprovação (Constitution Principle II — mudança de contrato)

---

## Mudança de Contrato

### Campo adicionado: `erro` em `ExtratoFiltrosResponse`

Campo nullable. Ausente em respostas normais (`@JsonInclude(NON_NULL)`).

---

## Cenários de Resposta

### Cenário 1: Todos os upstreams OK (comportamento atual mantido)

```json
{
  "data": {
    "ordemAbas": ["RECENTES", "FUTUROS"],
    "abas": {
      "RECENTES": {
        "filtros": [ { "tipo": "PERIODO", "opcoes": [...] } ],
        "cabecalho": { "entradas": "R$ 1.500,00", "saidas": "R$ 800,00", "saldo": "R$ 700,00" },
        "dados": [ { ... } ],
        "paginacao": null
      },
      "FUTUROS": {
        "filtros": [ { "tipo": "PERIODO", "opcoes": [...] } ],
        "cabecalho": { "entradas": "R$ 0,00", "saidas": "R$ 0,00", "saldo": "R$ 0,00" },
        "dados": [ { ... } ],
        "paginacao": null
      }
    }
  },
  "paginacao": null
}
```

Campo `erro` ausente (null, omitido pelo `@JsonInclude(NON_NULL)`).

---

### Cenário 2: Falha parcial — upstream FUTUROS indisponível (ambas as abas solicitadas)

```json
{
  "data": {
    "ordemAbas": ["RECENTES", "FUTUROS"],
    "abas": {
      "RECENTES": {
        "filtros": [ { "tipo": "PERIODO", "opcoes": [...] } ],
        "cabecalho": { "entradas": "R$ 1.500,00", "saidas": "R$ 800,00", "saldo": "R$ 700,00" },
        "dados": [ { ... } ],
        "paginacao": null
      },
      "FUTUROS": {
        "filtros": [],
        "cabecalho": { "entradas": "R$ 0,00", "saidas": "R$ 0,00", "saldo": "R$ 0,00" },
        "dados": [],
        "paginacao": null
      }
    }
  },
  "paginacao": null,
  "erro": {
    "codigo": "UPSTREAM_PARCIALMENTE_INDISPONIVEL",
    "mensagem": "Alguns dados do extrato estão temporariamente indisponíveis."
  }
}
```

---

### Cenário 3: Falha total — ambos os upstreams indisponíveis

```json
{
  "data": {
    "ordemAbas": ["RECENTES", "FUTUROS"],
    "abas": {
      "RECENTES": {
        "filtros": [],
        "cabecalho": { "entradas": "R$ 0,00", "saidas": "R$ 0,00", "saldo": "R$ 0,00" },
        "dados": [],
        "paginacao": null
      },
      "FUTUROS": {
        "filtros": [],
        "cabecalho": { "entradas": "R$ 0,00", "saidas": "R$ 0,00", "saldo": "R$ 0,00" },
        "dados": [],
        "paginacao": null
      }
    }
  },
  "paginacao": null,
  "erro": {
    "codigo": "UPSTREAM_INDISPONIVEL",
    "mensagem": "O serviço de extrato está temporariamente indisponível. Tente novamente em instantes."
  }
}
```

---

### Cenário 4: Falha em aba única (ex.: aba RECENTES solicitada individualmente)

```json
{
  "data": {
    "ordemAbas": ["RECENTES"],
    "abas": {
      "RECENTES": {
        "filtros": [],
        "cabecalho": { "entradas": "R$ 0,00", "saidas": "R$ 0,00", "saldo": "R$ 0,00" },
        "dados": [],
        "paginacao": null
      }
    }
  },
  "paginacao": null,
  "erro": {
    "codigo": "UPSTREAM_INDISPONIVEL",
    "mensagem": "O serviço de extrato está temporariamente indisponível. Tente novamente em instantes."
  }
}
```

---

## Classes Java Impactadas

### ExtratoFiltrosResponse (modificada)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtratoFiltrosResponse(
    ExtratoFiltrosData data,
    PaginacaoResponse paginacao,
    ErroResponse erro             // campo novo
) {}
```

### ErroResponse (nova — em dto.response)

```java
public record ErroResponse(String codigo, String mensagem) {}
```

> **Nota**: Classe separada de `exception.ErrorResponse` existente para manter separação entre envelope de resposta do consumidor e estrutura interna de tratamento de exceções.

---

## Aprovação Requerida

- [x] Time aprova adição do campo `erro` ao `ExtratoFiltrosResponse`
- [x] Time aprova nova classe `ErroResponse` em `dto.response`
- [x] Time aprova adição das dependências `spring-cloud-starter-circuitbreaker-resilience4j` e `spring-boot-starter-actuator` ao `pom.xml`
