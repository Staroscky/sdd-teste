# Research: Refatoração de Clean Code e Boas Práticas

**Feature**: 002-clean-code-refactoring | **Date**: 2026-05-31

## Decisão 1: Thread-safety de `NumberFormat` com virtual threads

**Decisão**: `ThreadLocal<NumberFormat>` dentro de um `@Component` Spring (`CurrencyFormatter`).

**Rationale**: `NumberFormat` não é thread-safe. Com `Executors.newVirtualThreadPerTaskExecutor()`
cada tarefa roda em uma virtual thread separada. `ThreadLocal` garante uma instância por thread
sem sincronização — custo de criação é pago uma vez por thread e as virtual threads são
descartadas após a tarefa, evitando vazamento de memória.

**Alternativas consideradas**:
- `synchronized` no método `format`: descartado — cria ponto de contenção desnecessário.
- `new NumberFormat` por chamada: descartado — é o problema atual (caro + sem thread-safety garantida pelo contrato da JDK).
- `String.format` / `DecimalFormat` inline: descartado — perde a semântica de `getCurrencyInstance` que formata símbolo, separadores e casas decimais automaticamente para pt-BR.

---

## Decisão 2: Localização do `UpstreamParams`

**Decisão**: Record privado interno em `ExtratoFiltrosService` com factory method `from(request)`.

**Rationale**: Os parâmetros upstream são um detalhe de implementação do service — não precisam
ser expostos fora dele. Record privado interno é idiomático em Java 21, não cria arquivo extra
e fica visível apenas para quem mantém o service.

**Alternativas consideradas**:
- Método privado que retorna array/Map: descartado — sem tipagem.
- Novo record público `UpstreamParams`: descartado — expõe detalhe de implementação sem benefício.
- Inline nos dois métodos (estado atual): descartado — é exatamente o problema que estamos resolvendo.

---

## Decisão 3: Onde consolidar as validações

**Decisão**: Mover `pagina < 1` do controller para `ExtratoFiltrosService.validarParametros`.

**Rationale**: A regra de `pagina >= 1` é de negócio (não apenas de binding HTTP), portanto
pertence ao service junto com a regra de `PERSONALIZADO`. O controller continua sendo responsável
apenas por deserialização e roteamento. `GlobalExceptionHandler` já mapeia `IllegalArgumentException`
para HTTP 400, portanto o comportamento externo é idêntico.

**Alternativas consideradas**:
- `@Validated` + `@Min(1)` no controller: descartado — introduziria nova dependência de Bean
  Validation e a mensagem de erro seria diferente da mensagem atual (quebraria testes que
  verificam a mensagem).
- Nova classe `ExtratoFiltrosRequestValidator`: descartado — over-engineering para apenas duas regras.

---

## Decisão 4: Nomear o utilitário de formatação

**Decisão**: Classe `CurrencyFormatter` no pacote `util`.

**Rationale**: Nome descreve exatamente a responsabilidade (formatar moeda). Pacote `util` é
convencional para utilitários transversais sem domínio específico. Alternativamente `BrlFormatter`
seria mais específico mas menos reutilizável se o projeto expandir para outras moedas.

**Alternativas consideradas**:
- Método `static` em classe utilitária sem estado: descartado — `ThreadLocal` requer estado de
  instância gerenciado pelo Spring para lifecycle adequado.
- Mover para `mapper/`: descartado — formatação monetária não é responsabilidade de mapper.
