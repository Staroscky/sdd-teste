# Feature Specification: Resiliência com Circuit Breaker e Timeouts

**Feature Branch**: `003-resiliencia-circuit-breaker`

**Created**: 2026-05-31

**Status**: Draft

**Input**: Adicionar resiliência à aplicação extrato-bff com circuit breaker e timeouts configuráveis por upstream. A aplicação usa Feign com OkHttp para chamar dois upstreams (recentes e futuros), faz chamadas paralelas com CompletableFuture e virtual threads. Atualmente os timeouts estão hardcoded no FeignConfig e sem circuit breaker. Preciso: (1) circuit breaker independente por upstream com estados CLOSED/OPEN/HALF_OPEN e thresholds configuráveis; (2) timeouts configuráveis por upstream no application.yml; (3) fallback adequado quando o circuit breaker abre; (4) métricas expostas via Actuator/Micrometer.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Proteção contra falha total de upstream (Priority: P1)

Um operador de TI que monitora o extrato-bff precisa que a aplicação continue respondendo de forma estruturada quando um ou ambos os upstreams estão fora do ar, retornando as abas sem dados e um campo de erro no envelope de resposta para que o front-end possa exibir a tela de indisponibilidade adequada.

**Why this priority**: Sem circuit breaker, uma falha de upstream causa cascata de erros 500 para todos os usuários simultaneamente. Esse é o risco mais crítico de disponibilidade.

**Independent Test**: Pode ser testado derrubando o serviço upstream de "recentes" e verificando que o endpoint de extrato retorna `200 OK` com lista vazia na aba afetada e campo `erro` preenchido no envelope de resposta.

**Acceptance Scenarios**:

1. **Given** o circuit breaker do upstream "recentes" está OPEN e "futuros" funcionando, **When** o endpoint é chamado para ambas as abas, **Then** a resposta retorna `200 OK` com dados normais de FUTUROS, aba RECENTES com filtros e dados vazios, e campo `erro` preenchido no envelope indicando indisponibilidade parcial.
2. **Given** ambos os circuit breakers estão OPEN, **When** o endpoint é chamado para ambas as abas, **Then** a resposta retorna `200 OK` com ambas as abas com filtros e dados vazios, e campo `erro` preenchido indicando indisponibilidade total.
3. **Given** o circuit breaker do upstream "recentes" está OPEN, **When** o endpoint é chamado para a aba RECENTES (aba única), **Then** a resposta retorna `200 OK` com a aba sem filtros e sem dados, e campo `erro` preenchido para o front-end exibir tela de indisponibilidade.
4. **Given** ambos os circuit breakers estão CLOSED, **When** os dois upstreams respondem normalmente, **Then** a resposta contém dados completos de ambas as abas e campo `erro` ausente ou nulo.

---

### User Story 2 - Recuperação automática após restabelecimento do upstream (Priority: P2)

Um operador de TI precisa que o sistema detecte automaticamente quando um upstream se recupera de uma falha e volte a consumi-lo sem intervenção manual, usando o estado HALF_OPEN do circuit breaker.

**Why this priority**: Sem recuperação automática, o sistema permaneceria em modo degradado mesmo após o upstream voltar, exigindo reinicialização da aplicação.

**Independent Test**: Pode ser testado simulando falha no upstream, confirmando abertura do circuit breaker, restaurando o upstream e verificando que após o período de espera configurado o circuit breaker retorna ao estado CLOSED automaticamente.

**Acceptance Scenarios**:

1. **Given** o circuit breaker está OPEN após falhas repetidas, **When** o período de espera configurado expira, **Then** o circuit breaker transita para HALF_OPEN e permite uma chamada de teste.
2. **Given** o circuit breaker está HALF_OPEN, **When** a chamada de teste ao upstream é bem-sucedida, **Then** o circuit breaker retorna ao estado CLOSED e o serviço volta ao comportamento normal.
3. **Given** o circuit breaker está HALF_OPEN, **When** a chamada de teste falha novamente, **Then** o circuit breaker volta ao estado OPEN e reinicia o período de espera.

---

### User Story 3 - Observabilidade do estado dos circuit breakers (Priority: P3)

Um engenheiro de plataforma precisa visualizar o estado atual de cada circuit breaker (CLOSED/OPEN/HALF_OPEN) e as métricas de falha/sucesso via ferramentas de monitoramento, sem precisar acessar logs da aplicação.

**Why this priority**: Sem visibilidade das métricas, é impossível detectar degradação silenciosa ou calibrar os thresholds de forma orientada a dados.

**Independent Test**: Pode ser testado consultando o endpoint de health/métricas da aplicação e verificando a presença de indicadores de estado dos circuit breakers para "recentes" e "futuros".

**Acceptance Scenarios**:

1. **Given** a aplicação está em execução, **When** o endpoint de métricas é consultado, **Then** são exibidos estado, taxa de falha e contagem de chamadas de cada circuit breaker individualmente.
2. **Given** o circuit breaker de "recentes" transita de CLOSED para OPEN, **When** o endpoint de saúde é consultado, **Then** a saúde da aplicação reflete o estado degradado do componente afetado.

---

### User Story 4 - Timeouts configuráveis por upstream (Priority: P2)

Um engenheiro de configuração precisa ajustar os tempos limite de conexão e leitura de cada upstream de forma independente via variáveis de configuração, sem recompilar ou reimplantar a aplicação.

**Why this priority**: Os upstreams "recentes" e "futuros" podem ter SLAs distintos; um timeout único aplicado a ambos resulta em timeouts desnecessariamente longos ou curtos para cada caso.

**Independent Test**: Pode ser testado alterando os valores de timeout no arquivo de configuração, reiniciando a aplicação e verificando que chamadas que excedem o tempo limite configurado são interrompidas no tempo correto.

**Acceptance Scenarios**:

1. **Given** o timeout de leitura do upstream "recentes" está configurado para 2 segundos, **When** o upstream demora 3 segundos para responder, **Then** a chamada é interrompida após 2 segundos e tratada como falha.
2. **Given** o timeout do upstream "futuros" é diferente do upstream "recentes", **When** ambos os upstreams são chamados em paralelo, **Then** cada um respeita seu próprio timeout configurado independentemente.

---

### Edge Cases

- O que acontece quando o upstream demora exatamente o tempo limite configurado (limiar de timeout)?
- Como o sistema se comporta quando ambos os circuit breakers abrem simultaneamente?
- Como o circuit breaker conta falhas quando as chamadas são paralelas via CompletableFuture?
- O que acontece quando um timeout ocorre durante a fase HALF_OPEN?
- Como o fallback é representado na resposta — campo `indisponivel`, mensagem de erro, ou ausência dos dados?

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE manter um circuit breaker independente para cada upstream (recentes e futuros), com estados CLOSED, OPEN e HALF_OPEN.
- **FR-002**: O sistema DEVE abrir o circuit breaker de um upstream quando a taxa de falha ultrapassar o threshold configurado dentro de uma janela de chamadas.
- **FR-003**: O sistema DEVE aguardar um período de espera configurável antes de transitar do estado OPEN para HALF_OPEN.
- **FR-004**: O sistema DEVE retornar `200 OK` com a aba afetada sem filtros e sem dados, e o campo `erro` preenchido no envelope de resposta, quando o circuit breaker de um upstream estiver OPEN — permitindo que o front-end exiba a tela de indisponibilidade.
- **FR-004a**: Quando apenas uma das abas falhar em uma requisição de ambas as abas, o sistema DEVE retornar os dados completos da aba disponível junto com a aba falha vazia e campo `erro` indicando indisponibilidade parcial.
- **FR-004b**: Quando todas as abas solicitadas falharem, o sistema DEVE retornar todas as abas vazias e campo `erro` indicando indisponibilidade total.
- **FR-005**: O sistema DEVE permitir configuração independente de timeout de conexão e de leitura por upstream via arquivo de configuração externo.
- **FR-006**: O sistema DEVE expor o estado de cada circuit breaker e métricas de falha/sucesso via endpoint de monitoramento.
- **FR-007**: O sistema DEVE refletir degradação parcial (circuit breaker aberto em um upstream) na resposta sem afetar dados do outro upstream disponível.
- **FR-008**: Os thresholds do circuit breaker (taxa de falha, tamanho da janela, tempo de espera, chamadas em HALF_OPEN) DEVEM ser configuráveis por upstream via arquivo de configuração.

### Key Entities

- **CircuitBreaker (por upstream)**: Representa o estado da proteção de resiliência de um upstream específico. Atributos: nome do upstream, estado atual (CLOSED/OPEN/HALF_OPEN), taxa de falha, contagem de chamadas na janela, timestamp da última transição de estado.
- **ErroEnvelope**: Campo `erro` adicionado ao envelope de resposta do extrato. Presente apenas quando uma ou mais abas falharam. Atributos: código de erro (ex.: `UPSTREAM_INDISPONIVEL`, `UPSTREAM_PARCIALMENTE_INDISPONIVEL`), mensagem descritiva ao usuário final.
- **AbaFallback**: Representação de uma aba retornada em modo degradado. Atributos: lista de filtros vazia, lista de lançamentos vazia, cabeçalho zerado, paginação nula.
- **UpstreamConfig**: Configuração de conectividade e resiliência por upstream. Atributos: URL, timeout de conexão, timeout de leitura, thresholds do circuit breaker.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Quando um upstream retorna erro em 50% ou mais das chamadas dentro de uma janela configurável, o sistema para de chamar esse upstream em até 5 segundos após atingir o threshold.
- **SC-002**: O endpoint do extrato continua retornando `200 OK` para 100% das requisições mesmo com um upstream totalmente indisponível, retornando dados parciais (aba disponível) ou lista vazia com indicação de indisponibilidade.
- **SC-003**: Após a recuperação do upstream, o sistema retoma o consumo normal sem intervenção manual em no máximo o dobro do período de espera configurado.
- **SC-004**: O estado de cada circuit breaker e suas métricas são observáveis via endpoint de monitoramento em tempo real, sem acesso a logs de aplicação.
- **SC-005**: Todos os parâmetros de resiliência (thresholds, timeouts, períodos de espera) podem ser ajustados via configuração sem recompilação.

---

## Assumptions

- A indicação de indisponibilidade é um campo `erro` adicionado ao envelope raiz de `ExtratoFiltrosResponse` (não dentro da aba), com `@JsonInclude(NON_NULL)` para não aparecer em respostas normais — adição não-destrutiva ao contrato.
- Quando apenas uma aba falha em uma requisição de ambas as abas, a resposta é `200 OK` (não `206 Partial Content`), mantendo compatibilidade com consumidores existentes.
- A configuração dos thresholds do circuit breaker é feita via arquivo de configuração da aplicação, não via interface de administração dinâmica.
- O fallback retorna lista de lançamentos vazia; não há cache de respostas anteriores a ser utilizado como fallback.
- A janela de contagem de falhas do circuit breaker usa contagem de chamadas (não janela de tempo), alinhando com o modelo padrão de sliding window por contagem.
- A aplicação é reiniciada com circuit breakers no estado CLOSED (sem persistência de estado entre reinicializações).
- Timeouts configurados por upstream substituem os valores atualmente hardcoded no `FeignConfig` e no `application.yml`.
