# SPEC — gestor-ativos-brutos (Coletor e Orquestrador)

| Campo | Valor |
|---|---|
| Versão da spec | 1.0.0 |
| Data | 2026-09-25 |
| Status | Ativa — baseline do estado atual + backlog planejado |
| Branch analisada | `feature-teste` (último commit `596a888`) |
| Alterações não commitadas | 15 arquivos (renomeação de controllers, série histórica, SQS, properties) — ver `ISS-14` |
| Specs relacionadas | `gerar-insights/SPEC.md` (consumidor SQS) · `infra-b3-ecossytem/SPEC.md` (ecossistema, Docker e contratos `INT-`) |
| Público | Desenvolvedores humanos e agentes de IA (Codex, ChatGPT, Claude ou outros) |

---

## 1. Como usar este arquivo (protocolo para agentes)

Fluxo SDD: `Spec → Plano → Tarefas → Implementação → Verificação → Atualizar Spec`.

1. Leia as seções 2 a 7 antes de alterar código. Toda mudança referencia um ID (`REQ-`, `NFR-`, `ISS-`, `TASK-`).
2. IDs são estáveis: nunca renumere nem apague; para descontinuar, use o status `DESCARTADO` com justificativa.
3. Status: `ABERTO`, `EM_ANDAMENTO`, `BLOQUEADO`, `CONCLUIDO`, `DESCARTADO` (tarefas/problemas) e `IMPLEMENTADO`, `PARCIAL`, `PLANEJADO` (requisitos).
4. **Mudanças em payloads SQS ou no uso da tabela `insight_acao` são mudanças de contrato.** Elas exigem atualizar a seção de contratos da spec do ecossistema (`infra-b3-ecossytem/SPEC.md`, IDs `CTR-`) e avisar o consumidor `gerar-insights`.
5. Decisões viram `DEC-`. Não resolva decisões abertas sem registrar a escolha.
6. Critérios de aceite em **Dado / Quando / Então**, convertidos em testes sempre que possível.
7. Nunca grave segredos (chaves BRAPI/Gemini) em arquivos versionados, nem mesmo como valor padrão.

---

## 2. Visão do produto (funcional)

`gestor-ativos-brutos` é a **porta de entrada** do ecossistema B3. É uma API Spring Boot que:

1. **Coleta** cotações e séries históricas OHLCV na **BRAPI** (`https://brapi.dev`).
2. **Publica** os dados brutos em filas **SQS** para o worker Python `gerar-insights`, que faz o valuation.
3. **Lê** os insights que o `gerar-insights` gravou em MySQL (`insight_acao`), consolida os sinais e deriva a decisão (sentimento, risco, recomendação) por **regras deterministicas**, sem IA nem storage externo.

> **ATUALIZAÇÃO (2026-09-25):** Gemini e S3 foram removidos deste serviço (DynamoDB, nunca usado, foi removido do `gerar-insights`). A decisão consolidada agora é calculada em `MontadorDecisaoDeterministica` a partir dos mesmos dados de `AnaliseConsolidadaDTO`, sem chamada externa nem persistência de arquivo. Itens marcados OBSOLETO abaixo (`ISS-13`, `ISS-16`, `F1`, `F3`, `TASK-22`, `TASK-23`, `CTR-04`, `REQ-05`) eram específicos do fluxo removido. **`ISS-02` continua ABERTO e agora é mais relevante**: o bug de contagem alimenta diretamente `MontadorDecisaoDeterministica`, não só o prompt do Gemini.
>
> **ATUALIZAÇÃO (2026-09-25, 2ª):** `POST /ativos/registrar/{ativo}` deixou de enfileirar em memória (`ISS-08` **RESOLVIDO**). Agora persiste o ativo na tabela `ativo_monitorado` (já existia no schema, nunca usada até aqui) via `ServicoAtivoMonitorado`, dispara a primeira coleta robusta na hora, e o `AgendadorAtivos` reprocessa cada ativo ativo automaticamente a cada `intervalo_segundos` (30s por padrão) — sobrevive a restart. Novo `GET /ativos/registrados` lista o que está cadastrado, consumido pela nova aba "Monitorados" do front (`painel-ativos-frontend`, `TASK-01`/`ISS-01` do front **RESOLVIDO**).

### 2.1 Endpoints HTTP

| Método | Rota | Efeito | Efeitos colaterais |
|---|---|---|---|
| GET | `/ativos/{ativo}` | Consulta a cotação na BRAPI e devolve o `Ativo` | **Publica** na fila `tratar-ativos` (um GET com efeito colateral, ver `ISS-09`) |
| GET | `/ativos/robusto/{ativo}` | Cotação + série (`brapi.historico.range`, padrão `3mo`/`1d`; ver `ISS-18`) | Publica em `tratar-ativos` **e** em `sqs-registrar-series-historicas` |
| POST | `/ativos/registrar/{ativo}` | Persiste o ativo em `ativo_monitorado` (upsert por símbolo, `tipo_coleta=COTACAO_E_HISTORICO`, `intervalo_segundos=30`) e devolve 202 | Dispara `processarRobusto()` imediatamente (falha aqui é só logada — o registro já foi persistido, o agendador tenta de novo sozinho); depois, `AgendadorAtivos` reprocessa a cada `intervalo_segundos` até o ativo ser desativado |
| GET | `/ativos/registrados` | Lista os ativos cadastrados em `ativo_monitorado`, ordenados por símbolo (`AtivoMonitoradoDTO[]`) | — |
| GET | `/analises/{simbolo}/analise` | Consolida todo o histórico de `insight_acao` e devolve a decisão deterministica (média) | — |
| GET | `/analises/{simbolo}/fundamentos` | Devolve o `detalhes_json` **bruto** (não mediado) da análise mais recente do símbolo — os números e classificações exatos de um único ciclo, mais o perfil de operação e riscos derivados (`FundamentosAtivoDTO`, ver `PerfilOperacaoClassificador` na seção 3.2) | — |
| GET | `/api/v2/stocks/historical` | Proxy para a BRAPI (`symbols`, `range`, `interval`, `startDate`, `endDate`, `sortOrder`) | — |
| GET | `/actuator/health`, `/actuator/prometheus` | Saúde e métricas | — |

### 2.2 Fluxos principais

**Fluxo A — consulta simples (`GET /ativos/{ativo}`)**
BRAPI `quote` → `AtivoBrapiDTO` → ModelMapper → `Ativo` → JSON → SQS `tratar-ativos`.

**Fluxo B — processamento completo (`ServicoAtivo.processar`, usado pelo agendador)**
1. Consulta a BRAPI e publica em `tratar-ativos`.

**Fluxo C — robusto (`processarRobusto`)**: igual ao B, mas também publica a série histórica de 1 ano.

**Fluxo E — registro e monitoramento recorrente (`POST /ativos/registrar/{ativo}` + `AgendadorAtivos`)**
1. `ServicoAtivoMonitorado.registrar()` faz upsert em `ativo_monitorado` por símbolo (novo: `tipoColeta=COTACAO_E_HISTORICO`, `intervaloSegundos=30`, `atualizadoEm=agora`; existente: só reativa `ativo=true`).
2. O controller chama `processarRobusto()` na mesma requisição (best-effort, não falha o 202 se a BRAPI estiver fora).
3. `AgendadorAtivos.processarAtivosMonitorados()` roda a cada 5s (`@Scheduled(fixedDelay=5000)`), busca `findByAtivoTrue()` e, para cada linha, só processa se `atualizadoEm + intervaloSegundos` já passou — tick fino (5s) checando um intervalo mais grosso (30s) por ativo, em vez de um tick fixo de 30s que sincronizaria artificialmente todos os cadastros.
4. Em caso de sucesso, `marcarProcessado()` grava `atualizadoEm=agora`, adiando o próximo ciclo. Em caso de falha (ex.: BRAPI fora do ar), `atualizadoEm` não avança, então o ativo continua "devido" e é retentado no próximo tick de 5s — sem backoff (mesma limitação de `ISS-10`).

**Fluxo D — decisão consolidada (`GET /analises/{simbolo}/analise`)**
1. Lê `insight_acao` do símbolo (todo o histórico).
2. Consolida (sinal predominante, % de venda, média da margem, médias dos campos numéricos de `detalhes_json`) em `ConsolidadorAnaliseAcao`.
3. `MontadorDecisaoDeterministica` deriva sentimento/força do sinal/risco/confiança/recomendação por regras fixas — sem IA, sem S3.

**Fluxo F — fundamentos de um único ciclo (`GET /analises/{simbolo}/fundamentos`, 2026-09-25)**
1. `ServicoAnaliseAcao.buscarUltimaPorSimbolo()` busca só a linha mais recente de `insight_acao` (`findFirstBySimboloOrderByDataAnaliseDesc`), sem consolidar/mediar.
2. `FundamentosAtivoDTO.de()` devolve o `detalhes_json` dessa linha como está — o payload v2.0 completo que o `gerar-insights` gravou naquele ciclo (cenários de preço justo Graham, classificações de P/L/earnings yield, contexto técnico do dia, sinal técnico de série quando houver histórico, insights e fatores de decisão). O Java não reconstrói esses campos em DTOs próprios; só repassa o `JsonNode` como veio — evita duplicar/desatualizar o schema do Python no Java.
3. Sem registro para o símbolo: devolve 200 com só o campo `simbolo` preenchido (mesmo padrão de "sem dado" de `/analise`).
4. `PerfilOperacaoClassificador.classificar(detalhes)` (2026-09-25) deriva, do mesmo `JsonNode`, 4 campos extras direto em `FundamentosAtivoDTO`: `perfisAplicaveis` (lista não exclusiva — `DAY_TRADE` se `sinal_momentum != NEUTRO_TECNICO`, `SWING_REVERSAO` se `sinal_reversao != NEUTRO_TECNICO`, `LONGO_PRAZO` se margem conservadora ≥20% e earnings yield Atrativo/Razoável), `riscoCompraAgora`/`riscoVendaAgora` (BAIXO/MEDIO/ALTO por combinação de `zona_52w` + margem base) e `confluenciaSinais` (contagem de concordância entre recomendação fundamentalista, `sinal_momentum` e `sinal_reversao` — **deliberadamente não é uma probabilidade estatística de sucesso**: isso exigiria acompanhar o resultado futuro de recomendações passadas, mecanismo que não existe hoje). Navegação null-safe: histórico com menos de 20 candles não gera `contexto_tecnico_serie`, e o classificador degrada sem erro (`perfisAplicaveis` fica sem `DAY_TRADE`/`SWING_REVERSAO`).

---

## 3. Arquitetura técnica

### 3.1 Stack
Java 21 (compilado com Maven em imagem Temurin 24), Spring Boot 3.3.0, Spring Web **e** WebFlux (os dois declarados), Spring Data JPA/Hibernate, MySQL Connector/J, AWS SDK v2 (SQS), ModelMapper 3.1.1, Lombok, Micrometer Prometheus, Logstash Logback Encoder, OpenFeign (declarado, não usado — o cliente BRAPI usa `RestTemplate`).

> Google GenAI SDK e AWS SDK S3 foram removidos (ver nota de 2026-09-25 na seção 2).

### 3.2 Estrutura (arquitetura hexagonal parcial)

| Pacote | Responsabilidade |
|---|---|
| `entrypoint/controller` | Controllers REST (`AtivoController`, `AnaliseAcaoController`, `HistoricoAcoesController`) |
| `entrypoint/schedule` | `AgendadorAtivos`: le `ativo_monitorado` via `ServicoAtivoMonitorado`, `@Scheduled(fixedDelay=5000)` (tick fino checando o intervalo por ativo, ver Fluxo E) |
| `service` | `ServicoAtivo` (orquestração), `ServicoAnaliseAcao` (leitura de insights), `ServicoAtivoMonitorado` (CRUD de `ativo_monitorado`) |
| `port` | `PortaFilaMensagens` (porta de saída para mensageria) |
| `external/queue` | `AdaptadorFilaSqs` (3 tentativas extras, sem backoff) |
| `external/http` | `ClienteBrApi` (RestTemplate; token no header `Authorization`) |
| `external` | `Ativo` (modelo), `AnaliseAcaoEntity` (JPA sobre `insight_acao`), `AtivoMonitoradoEntity` (JPA sobre `ativo_monitorado`), `TipoColeta` (enum), DTOs |
| `repository` | `RepositorioAnaliseAcao` (JPA + query nativa), `RepositorioAtivoMonitorado` (JPA) |
| `tools` | `ConsolidadorAnaliseAcao`, `MontadorDecisaoDeterministica`, `PerfilOperacaoClassificador` (2026-09-25 — perfil de operação e riscos de compra/venda a partir de um único ciclo), `ConversorJson`, `ConversorJsonNode`, constantes |
| `exceptions` | Hierarquia `ExcecaoAplicacao` + `TratadorGlobalExcecoes` (`@RestControllerAdvice`) |
| `config` | Beans de SQS e `ConfigProperties` |

### 3.3 Decisão deterministica (`MontadorDecisaoDeterministica`)
- Substitui a antiga integração com Gemini (seção OBSOLETA abaixo). Deriva sentimento (POSITIVO/NEUTRO/NEGATIVO por `percentualSinaisVenda`), força do sinal (dominância compra vs. venda), risco (por `variacaoMedia`, isto é, margem de segurança média) e confiança (por quantidade de registros), tudo por regras fixas em `AnaliseConsolidadaDTO`.

### 3.3-OBSOLETO Integração com o Gemini (`ServicoGemini`) — removida em 2026-09-25
- Modelo padrão `gemini-3.1-flash-lite`; em caso de cota excedida (HTTP 429 ou mensagem contendo *quota*/*rate limit*), tenta em sequência `gemini-3.5-flash`, `gemini-2.5-flash` e `gemini-2.5-flash-lite`, com 4 s de espera entre as tentativas.
- `responseMimeType=application/json` com `responseSchema` (ativo, sentimento, forca_sinal, risco, confianca_analise 0–100, resumo ≤ 200, analise_tecnica, analise_fundamentalista, possivel_cenario, recomendacao), `temperature=0.2`, `maxOutputTokens=600`.

### 3.4 Configuração

| Propriedade | Variável de ambiente | Padrão `dev` | Padrão `test` |
|---|---|---|---|
| `server.port` | `SERVER_PORT` | 9090 | 9191 |
| `aws.sqs.endpoint.base` | `AWS_SQS_ENDPOINT_BASE` | `http://localstack:4566` | `http://localhost:4566` |
| `aws.sqs.queue.url` | `AWS_SQS_QUEUE_URL` | `.../tratar-ativos` | idem (localhost) |
| `aws.sqs.historical-series.queue.url` | `AWS_SQS_HISTORICAL_SERIES_QUEUE_URL` | `.../sqs-registrar-series-historicas` | idem (localhost) |
| `aws.accessKeyId` / `aws.secretAccessKey` | `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | `teste` | `teste` |
| `spring.datasource.*` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `mysql:3306/minha_base`, spring | `localhost:3305/minha_base`, spring |
| `brapi.api.key` | `BRAPI_API_KEY` | obrigatória | **valor real embutido** (`ISS-01`) |
| `spring.jpa.hibernate.ddl-auto` | — | `update` (`ISS-05`) | `update` |

`application.properties` está **vazio**; o `Dockerfile` define `SPRING_PROFILES_ACTIVE=docker`, mas não existe `application-docker.properties` (`ISS-11`).

### 3.5 Tabela `ativo_monitorado` (carteira de monitoramento, 2026-09-25)

Definida em `infra-b3-ecossytem/mysql-init/1 - schema.sql`, mapeada por `AtivoMonitoradoEntity`. Fonte de verdade da carteira recorrente (Fluxo E, seção 2.2) — substituiu a fila em memória do `AgendadorAtivos`.

| Coluna | Tipo | Observação |
|---|---|---|
| `simbolo` | VARCHAR(10), UNIQUE | Chave de upsert em `ServicoAtivoMonitorado.registrar()` |
| `ativo` | BOOLEAN, default true | Sem UI/endpoint para desativar ainda (ver 5.3) |
| `tipo_coleta` | ENUM (`COTACAO`, `COTACAO_E_HISTORICO`) | Todo cadastro novo via API entra como `COTACAO_E_HISTORICO` |
| `intervalo_segundos` | INT, `CHECK >= 30` | 30 por padrão nos cadastros novos; o `AgendadorAtivos` respeita o valor da linha |
| `versao` | BIGINT | `@Version` — optimistic locking nativo do JPA |
| `atualizado_em` | DATETIME | Marca o último processamento bem-sucedido; usado pelo agendador pra saber se o ativo está "devido" |

---

## 4. Contratos de dados (lado produtor/leitor)

A definição canônica dos contratos entre serviços fica em `infra-b3-ecossytem/SPEC.md` (seção de contratos, IDs `CTR-`). Resumo do que este serviço produz e consome:

| ID | Direção | Canal | Formato |
|---|---|---|---|
| CTR-01 | Produz | SQS `tratar-ativos` | `Ativo` serializado com `new ObjectMapper()` (camelCase, BigDecimal como número): `symbol`, `currency`, `shortName`, `longName`, `marketCap`, `regularMarketChange`, `regularMarketChangePercent`, `regularMarketTime` (LocalDateTime — ver `ISS-07`), `regularMarketPrice`, `regularMarketDayHigh`, `regularMarketDayLow`, `regularMarketDayRange`, `regularMarketVolume`, `regularMarketPreviousClose`, `regularMarketOpen`, `fiftyTwoWeekRange`, `fiftyTwoWeekLow`, `fiftyTwoWeekHigh`, `priceEarnings`, `earningsPerShare`, `logoUrl`, `id` (sempre nulo) |
| CTR-02 | Produz | SQS `sqs-registrar-series-historicas` | `RespostaHistoricoAcoesDTO`: `results[].{requestedSymbol, symbol, changed, data.{usedInterval, usedRange, historicalDataPrice[].{date(epoch s), open, high, low, close, volume, adjustedClose, dataFormatada(dd/MM/yyyy, America/Sao_Paulo)}}}`, `requestedAt`, `took` |
| CTR-03 | Consome (leitura) | MySQL `insight_acao` | Colunas `simbolo`, `data_analise`, `preco_justo_graham`, `margem_seguranca_percent`, `recomendacao`, `detalhes_json` — **escritas pelo `gerar-insights`** |
| CTR-04 | OBSOLETO (S3 removido) | ~~S3 `bucket-salvar-insights`~~ | ~~`{simbolo}/analises/{HH:mm:ss}.json` contendo `RespostaAnaliseIaDTO`~~ — resposta agora só retornada via HTTP, sem persistência |

---

## 5. Regras de negócio (comportamento atual)

### 5.1 Consolidação (`ConsolidadorAnaliseAcao`)
- Entrada: **todas** as linhas de `insight_acao` do símbolo, sem janela de tempo.
- `sinalPredominante` = recomendação mais frequente.
- `percentualSinaisVenda` = contagem de `"VENDA"` ÷ total × 100. **O `gerar-insights` nunca emite `"VENDA"`**, e sim `"VENDA_VALUATION"`, então o valor é sempre 0 (`ISS-02`). Esse valor alimenta diretamente `MontadorDecisaoDeterministica` (sentimento, força do sinal), então o bug afeta a decisão final, não só um prompt de IA.
- `variacaoMedia` = média de `margem_seguranca_percent` (o nome engana: não é uma variação de preço).
- `indicadores` = média de cada campo **numérico de primeiro nível** de `detalhes_json`. No payload v2.0 do `gerar-insights` isso cobre só `earnings_yield_percent`, `desconto_maxima_52w_percent` e `crescimento_projetado_utilizado`; os campos aninhados (valuation, contexto técnico) são ignorados.

### 5.2 Prompt (`MontadorPromptAnalise`)
OBSOLETO — descrevia o prompt enviado ao Gemini (removido). O prompt pedia "risco 0-100", mas o schema definia `risco` como string (inconsistência, `ISS-13`, também obsoleto). A decisão hoje é montada em código por `MontadorDecisaoDeterministica`, sem prompt/schema.

### 5.3 Agendador
**RESOLVIDO (2026-09-25, ver `ISS-08`):** Cada `POST /ativos/registrar/{ativo}` persiste em `ativo_monitorado` e entra em monitoramento recorrente real (30s por padrão, configurável por linha via `intervalo_segundos`, mínimo 30 pelo `CHECK` do schema). Sobrevive a restart do serviço. Deduplicação por símbolo via `UNIQUE KEY uq_ativo_monitorado_simbolo` (upsert). Não há UI nem endpoint para desativar/pausar um ativo (`ativo=false`) — a coluna existe no schema mas nada a escreve ainda; fora de escopo do pedido original.

---

## 6. Requisitos

### 6.1 Funcionais

| ID | Requisito | Status |
|---|---|---|
| REQ-01 | Consultar a cotação de um ativo na BRAPI e devolvê-la via HTTP | IMPLEMENTADO |
| REQ-02 | Publicar a cotação bruta em `tratar-ativos` (CTR-01) | IMPLEMENTADO |
| REQ-03 | Publicar a série histórica em `sqs-registrar-series-historicas` (CTR-02) | IMPLEMENTADO (só via `/ativos/robusto`, não commitado) |
| REQ-04 | Gerar análise consolidada a partir dos insights (hoje por regras deterministicas, não IA) | IMPLEMENTADO (com defeito `ISS-02` ainda aberto) |
| REQ-05 | Salvar, listar e baixar análises no S3 | REMOVIDO (2026-09-25) |
| REQ-06 | Agendar coleta **recorrente** de uma carteira de ativos | IMPLEMENTADO (`TASK-20`, 2026-09-25 — cadastro persistido + reprocessamento a cada 30s; não há ainda restrição ao horário de pregão) |
| REQ-07 | Gerar a análise de IA somente depois que o insight do dia estiver disponível | PLANEJADO (`TASK-21`) |
| REQ-08 | Expor o retrato bruto (não mediado) de um único ciclo de análise, para transparência de metodologia | IMPLEMENTADO (2026-09-25, `GET /analises/{simbolo}/fundamentos`) |

Critérios de aceite de referência:
- **REQ-02** — *Dado* que a BRAPI devolve PETR4, *quando* `GET /ativos/PETR4` é chamado, *então* uma mensagem com `symbol=PETR4` e `regularMarketPrice` numérico chega a `tratar-ativos`.
- **REQ-04** — *Dado* 10 insights com 4 `VENDA_VALUATION`, *quando* a consolidação roda, *então* `percentualSinaisVenda = 40`.
- **REQ-05** — OBSOLETO: critério do fluxo S3 removido.

### 6.2 Não funcionais

| ID | Requisito | Status |
|---|---|---|
| NFR-01 | Nenhum segredo em arquivos versionados ou em imagens | NÃO ATENDIDO (`ISS-01`) |
| NFR-02 | Endpoints GET sem efeitos colaterais; publicação idempotente | NÃO ATENDIDO (`ISS-09`) |
| NFR-03 | Resiliência nas integrações (BRAPI, SQS) com backoff e timeouts | PARCIAL (`ISS-10`) |
| NFR-04 | Schema do banco gerenciado por uma fonte única; a aplicação só valida | NÃO ATENDIDO (`ISS-05`) |
| NFR-05 | Testes unitários e de integração reais no CI | NÃO ATENDIDO (`ISS-04`) |
| NFR-06 | Logs estruturados enviados ao ELK sem ruído nem duplicação | PARCIAL (`ISS-12`) |
| NFR-07 | Imagem mínima (JRE), sem root, com perfil de configuração existente | NÃO ATENDIDO (`ISS-11`) |

---

## 7. Problemas identificados

### 7.1 Segurança e integração

| ID | Sev. | Problema | Evidência | Impacto | Correção sugerida | Status |
|---|---|---|---|---|---|---|
| ISS-01 | **Crítico** | Chave real da BRAPI gravada como valor padrão (a chave do Gemini foi removida junto com a integração em 2026-09-25) | `src/main/resources/application-test.properties` (alteração ainda **não commitada**; o arquivo é versionado). A mesma chave está em `infra-b3-ecossytem/docker-compose-local.yml` (não versionado) | Um commit publica a chave no GitHub | Remover o padrão (`${BRAPI_API_KEY}` sem default), **revogar e gerar nova chave**, usar `.env` fora do git, adicionar secret scanning (gitleaks) no CI | ABERTO |
| ISS-02 | Alto | A consolidação conta `"VENDA"`, mas o produtor emite `"VENDA_VALUATION"` | `tools/ConsolidadorAnaliseAcao.java` (`getOrDefault("VENDA", 0L)`) | `percentualSinaisVenda` fica sempre 0, enviesando `MontadorDecisaoDeterministica` (sentimento/força do sinal) | Enum/lista compartilhada de recomendações (CTR-03); contar pelo prefixo `VENDA` | ABERTO |
| ISS-03 | Alto | Condição de corrida: publica no SQS e **lê `insight_acao` na sequência**, antes de o worker processar a mensagem | `service/ServicoAtivo.java` (`processar`, `processarRobusto`) | A análise da IA ignora o dado recém-coletado; na primeira coleta de um ativo não há análise | Separar os passos: a análise de IA roda em outro gatilho (evento "insight gerado" via SNS/SQS ou agendamento posterior) | ABERTO |
| ISS-04 | Alto | Não há testes reais (o único teste é `assertTrue(true)`); o build usa `-DskipTests` | `src/test/.../GestorAtivosBrutosApplicationTests.java`, `Dockerfile` | Regressões passam direto | Testes unitários de consolidador/prompt/serviços e de integração com Testcontainers (MySQL + LocalStack) | ABERTO |
| ISS-05 | Alto | Três fontes de schema para as mesmas tabelas: `mysql-init` (infra), Hibernate `ddl-auto=update` (Java) e entidades SQLAlchemy (Python) | `application-*.properties`, `external/AnaliseAcaoEntity.java` | Drift de schema; o Hibernate pode alterar uma tabela que pertence ao Python | `ddl-auto=validate`; schema único versionado (ver DEC do ecossistema) | ABERTO |
| ISS-06 | Médio | `ConfigSqs` ignora a configuração: credenciais fixas `"test"/"test"` e região fixa `SA_EAST_1` | `config/ConfigSqs.java` | Não funciona em AWS real | Usar `DefaultCredentialsProvider` (ou as properties) e região configurável | ABERTO |
| ISS-07 | Médio | `ConversorJson` usa `new ObjectMapper()` sem `JavaTimeModule`; `Ativo.regularMarketTime` é `LocalDateTime` e vem de uma String via ModelMapper | `tools/ConversorJson.java`, `external/Ativo.java` | O campo tende a chegar nulo, ou a serialização falha se for preenchido; o consumidor perde o timestamp da cotação, necessário para idempotência | Injetar o `ObjectMapper` do Spring; converter o epoch da BRAPI para `Instant`; teste de contrato | ABERTO (a verificar com teste) |
| ISS-08 | Médio | ~~O agendador não é recorrente e a fila fica em memória~~ | `entrypoint/schedule/AgendadorAtivos.java` | ~~Perda de trabalho no restart; README incorreto~~ | Carteira persistida em `ativo_monitorado` + tick de 5s checando `intervalo_segundos` por ativo + deduplicação por `UNIQUE KEY` | RESOLVIDO (2026-09-25) — falta ainda restringir ao horário de pregão, ver `TASK-20` |
| ISS-09 | Médio | `GET /ativos/{ativo}` publica no SQS (efeito colateral); cada chamada gera histórico e insight novos | `entrypoint/controller/AtivoController.java` | Duplicatas no `gerar-insights`; semântica HTTP errada | `POST` para publicar; `GET` só consulta; chave de deduplicação (`symbol` + `regularMarketTime`) como atributo da mensagem | ABERTO |
| ISS-10 | Médio | Retentativas do SQS sem backoff; `RestTemplate` sem timeout; a espera de 4 s bloqueia a thread do scheduler | `AdaptadorFilaSqs.java`, `ClienteBrApi.java`, `AgendadorAtivos.java` | Tempestade de retentativas; threads presas | Backoff exponencial (Resilience4j/Spring Retry); timeouts de conexão e leitura; rate limiter | ABERTO |
| ISS-11 | Médio | Dockerfile: JDK completo em runtime, root, perfil `docker` inexistente, `-DskipTests`, compila com Temurin 24 para alvo Java 21 | `Dockerfile` | Imagem pesada e insegura; sem o compose, a imagem sobe sem configuração | Runtime `eclipse-temurin:21-jre-alpine`, `USER` não-root, `application-docker.properties`, rodar os testes no build/CI | ABERTO |
| ISS-12 | Médio | Logback envia sempre para `logstash:5000` e também grava em arquivo com padrão texto, que o Logstash lê com codec JSON | `src/main/resources/logback-spring.xml`, `infra-b3-ecossytem/logstash.conf` | Erros de conexão fora do Docker; parse falho e logs duplicados no Elasticsearch | Appender Logstash só no perfil docker (`<springProfile>`); arquivo em JSON **ou** remover o input de arquivo | ABERTO |
| ISS-13 | Baixo | Prompt pede "risco 0-100", mas o schema espera string; `variacaoMedia` é na verdade a margem média | `tools/MontadorPromptAnalise.java`, `ServicoGemini.java` | Saída da IA inconsistente | Alinhar prompt e schema; renomear para `margemSegurancaMedia` | ABERTO |
| ISS-14 | Médio | 15 arquivos não commitados (renomeação de controllers, série histórica, remoção de classes) | `git status` | Risco de perda; PR grande demais | Commitar em partes (refactor de nomes ≠ feature) **depois** de resolver `ISS-01` | ABERTO |
| ISS-15 | Baixo | Dependências não usadas: WebFlux (junto com Web MVC), OpenFeign, `jackson-module-kotlin`; `mapstruct-processor` configurado sem MapStruct; `show-sql=true` | `pom.xml`, properties | Build e imagem maiores; logs verbosos | Limpar o `pom.xml`; `show-sql` só em debug | ABERTO |
| ISS-16 | OBSOLETO | ~~Chave S3 `{simbolo}/analises/{HH:mm:ss}.json` sem data; `:` em nome de arquivo~~ | — | S3 removido em 2026-09-25, análise não é mais persistida em arquivo | — | RESOLVIDO (remoção) |
| ISS-17 | Baixo | Consolidação sem janela temporal e com NPE possível se `recomendacao` for nula (`groupingBy` não aceita chave nula) | `tools/ConsolidadorAnaliseAcao.java` | Sinais antigos dominam; erro 500 esporádico | Janela configurável (ex.: 90 dias); filtrar nulos | ABERTO |
| ISS-18 | Alto | ~~`processarRobusto` (usado por `GET /ativos/robusto` e por todo cadastro em `ativo_monitorado`) pedia `range=1y` no histórico da BRAPI; o plano Free da BRAPI só libera `1d/5d/1mo/3mo` (`400 INVALID_RANGE`) para qualquer ticker fora da lista de demonstração deles (ex.: `PETR4`, `MGLU3` passavam mesmo sem chave; `WEGE3`, `RAIZ4` não)~~ | `service/ServicoAtivo.java` (achado em 2026-09-25 testando a aba Monitorados: `atualizado_em` nunca avançava pra tickers fora da lista demo) | Com chave BRAPI real configurada, o monitoramento recorrente falhava silenciosamente (retry a cada 5s sem nunca suceder) pra praticamente qualquer ativo cadastrado | `range` extraído para `${brapi.historico.range:3mo}` | RESOLVIDO (2026-09-25) |

### 7.2 Visão de analista financeiro
- **F1 (OBSOLETO):** era sobre o Gemini receber só médias agregadas e poder alucinar. Sem IA, não se aplica mais — a decisão é 100% determinada pelas mesmas médias, sem inferência.
- **F2:** a resposta contém o campo `recomendacao` em texto (COMPRA/VENDA/NEUTRO), sem aviso legal. O risco regulatório (CVM Res. 20/2021) descrito em `gerar-insights` `ISS-F6` continua valendo mesmo sem IA — é uma recomendação de investimento devolvida por API. Inclua um disclaimer fixo na resposta.
- **F3 (OBSOLETO):** era sobre misturar metodologias (regras Graham + LLM) sem rastreabilidade. Sem Gemini, só resta a metodologia determinística única — não há mais mistura a rastrear.

---

## 8. Roadmap e tarefas

### Fase 0 — Segurança e estabilização (fazer primeiro)

| ID | Tarefa | Resolve | Critério de aceite | Status |
|---|---|---|---|---|
| TASK-01 | Remover chave BRAPI embutida, revogar e gerar nova chave, adicionar gitleaks ao CI (chave do Gemini já removida junto com a integração) | ISS-01, NFR-01 | `git grep -nE "BRAPI_API_KEY:[^}]"` sem resultado; pipeline falha se houver segredo | ABERTO |
| TASK-02 | Commitar as mudanças pendentes em commits separados | ISS-14 | `git status` limpo; PR com descrição | ABERTO |
| TASK-03 | Corrigir a contagem de venda (usar o enum compartilhado de CTR-03) | ISS-02 | Teste do critério REQ-04 passa | ABERTO |
| TASK-04 | Testes reais + remover `-DskipTests`; job de teste no CI antes da publicação da imagem | ISS-04, NFR-05 | Cobertura ≥ 70% em `tools` e `service` | ABERTO |
| TASK-05 | Dockerfile multi-stage com JRE 21, não-root, `application-docker.properties` | ISS-11, NFR-07 | `docker run` sem o compose sobe e responde em `/actuator/health` (com as dependências apontadas) | ABERTO |

### Fase 1 — Integração correta

| ID | Tarefa | Resolve | Critério de aceite | Depende de | Status |
|---|---|---|---|---|---|
| TASK-10 | `ObjectMapper` do Spring + `regularMarketTime` como `Instant` ISO-8601 no payload | ISS-07 | Teste de contrato: a mensagem publicada contém `regularMarketTime` não nulo | TASK-04 | ABERTO |
| TASK-11 | `ddl-auto=validate` | ISS-05 | A aplicação falha no startup se o schema divergir | DEC do ecossistema sobre schema | ABERTO |
| TASK-12 | `GET /ativos/{ativo}` sem efeito colateral; `POST /ativos/{ativo}/coletas` publica; atributo `dedupKey` na mensagem | ISS-09, NFR-02 | Chamar o GET 5× não gera mensagens | — | ABERTO |
| TASK-13 | Configuração SQS via properties e cadeia de credenciais padrão | ISS-06 | Mesma imagem funciona com LocalStack e com AWS real mudando só o env | — | ABERTO |
| TASK-14 | Backoff + timeouts (Resilience4j) para BRAPI e SQS | ISS-10, NFR-03 | Testes com falha simulada respeitam o backoff | TASK-04 | ABERTO |
| TASK-15 | Logback por perfil; logs de arquivo em JSON | ISS-12, NFR-06 | Cada evento aparece uma vez no Kibana | — | ABERTO |

### Fase 2 — Produto

| ID | Tarefa | Resolve | Critério de aceite | Depende de | Status |
|---|---|---|---|---|---|
| TASK-20 | Carteira persistida + coleta agendada por cron no horário do pregão | ISS-08, REQ-06 | Ativos da carteira coletados 1×/dia útil, sem duplicata | TASK-12 | PARCIAL (2026-09-25) — carteira persistida (`ativo_monitorado`) e reprocessamento recorrente a cada 30s implementados; falta restringir ao horário de pregão (hoje roda 24/7) |
| TASK-21 | Análise de IA disparada por evento "insight gerado" (SNS `transmitir-lote-dados` ou fila nova), não na sequência da publicação | ISS-03, REQ-07 | A análise usa o insight do dia | contrato novo no ecossistema | ABERTO |
| TASK-22 | OBSOLETO — prompt/schema do Gemini removidos; disclaimer legal (`F2`) deve ser adicionado direto na resposta HTTP de `MontadorDecisaoDeterministica` | F2 | Resposta HTTP contém `aviso_legal` | TASK-03 | ABERTO (reescopado para F2) |
| TASK-23 | OBSOLETO — S3 removido, sem chave a corrigir | ISS-16 | — | — | RESOLVIDO (remoção) |
| TASK-24 | Limpeza do `pom.xml` | ISS-15 | Build verde; imagem menor | TASK-04 | ABERTO |

---

## 9. Decisões

| ID | Pergunta | Opções | Status |
|---|---|---|---|
| DEC-01 | Gatilho da análise de IA | síncrona no endpoint / evento pós-insight / agendada | ABERTO |
| DEC-02 | Lista de ativos acompanhados | tabela no MySQL / configuração / endpoint de carteira | ABERTO |
| DEC-03 | Manter WebFlux ou só Web MVC | MVC (recomendado; não há código reativo) / WebFlux | ABERTO |
| DEC-04 | Nome e contrato das recomendações | ver `infra-b3-ecossytem/SPEC.md` (DEC do ecossistema) | ABERTO |

---

## 10. Comandos de verificação

```bash
# build e testes
./mvnw clean verify

# execução local (infra do ecossistema rodando; chaves via env, nunca em arquivo)
SPRING_PROFILES_ACTIVE=test BRAPI_API_KEY=... GEMINI_API_KEY=... ./mvnw spring-boot:run

# fumaça
curl http://localhost:9191/ativos/PETR4
curl http://localhost:9191/actuator/health
awslocal sqs receive-message --queue-url http://localhost:4566/000000000000/tratar-ativos
```

Observação: no perfil `dev` a porta padrão é 9090, a mesma do Prometheus no compose do ecossistema. Rode localmente com o perfil `test` (9191) ou com `SERVER_PORT` explícito.
