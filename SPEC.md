# SPEC — gestor-ativos-brutos (Coletor e Orquestrador)

| Campo | Valor |
|---|---|
| Versão da spec | 1.0.0 |
| Data | 2026-09-27 |
| Status | IMPLEMENTADO |
| Specs relacionadas | `gerar-insights/SPEC.md` (consumidor SQS) · `infra-b3-ecossytem/SPEC.md` (ecossistema, Docker e contratos `INT-`) |
| Público | Desenvolvedores humanos e agentes de IA (Codex, ChatGPT, Claude ou outros) |

---

## Corte e estados comuns

Data de corte: **2026-09-27** (America/Sao_Paulo). `PLANEJADO`: ainda não executado; `EM ANDAMENTO`: entrega parcial; `IMPLEMENTADO`: código ou decisão presente, sem confirmação integral nesta revisão; `VERIFICADO`: aceite demonstrado por verificação registrada; `BLOQUEADO`: dependência impeditiva identificada. Datas anteriores permanecem como histórico. Resolver um problema significa implementar sua correção; funcionalidades descontinuadas mantêm o ID e registram a resolução. Evidências antigas não são nova validação operacional.

## 1. Como usar este arquivo (protocolo para agentes)

Fluxo SDD: `Spec → Plano → Tarefas → Implementação → Verificação → Atualizar Spec`.

1. Leia as seções 2 a 7 antes de alterar código. Toda mudança referencia um ID (`REQ-`, `NFR-`, `ISS-`, `TASK-`).
2. IDs são estáveis: nunca renumere nem apague; para descontinuar, use o status `IMPLEMENTADO (descontinuado)` com justificativa.
3. Status: `PLANEJADO`, `EM ANDAMENTO`, `BLOQUEADO`, `IMPLEMENTADO`, `IMPLEMENTADO (descontinuado)` (tarefas/problemas) e `IMPLEMENTADO`, `EM ANDAMENTO`, `PLANEJADO` (requisitos).
4. **Mudanças em payloads SQS ou no uso da tabela `insight_acao` são mudanças de contrato.** Elas exigem atualizar a seção de contratos da spec do ecossistema (`infra-b3-ecossytem/SPEC.md`, IDs `CTR-`) e avisar o consumidor `gerar-insights`.
5. Decisões viram `DEC-`. Não resolva decisões abertas sem registrar a escolha.
6. Critérios de aceite em **Dado / Quando / Então**, convertidos em testes sempre que possível.
7. Nunca grave segredos (chaves BRAPI/Gemini) em arquivos versionados, nem mesmo como valor padrão.

---

## 1A. Coordenação entre agentes (estado em 2026-10-04)

**Hub:** `infra-b3-ecossytem/SPEC.md` seção 1A — fila única, contratos, handoff e diário. Leia antes de codar; atualize lá ao pegar e ao fechar tarefa. Em conflito com seções antigas abaixo, vale o hub e esta seção.

- **Dono neste repo:** API HTTP, coleta BRAPI (orçamento, ciclo intradiário :05/:35 das 10h às 17h, snapshot 17:40), scheduler, leitura de `insight_acao`/`indicador_fundamentalista`/`provento_contabil`/`fator_valor`. **Não cria tabelas** (Flyway; `ddl-auto=validate`); tabelas novas são lidas por JDBC e devolvem vazio sem a migration (nunca 500).
- **Endpoints atuais além da tabela da seção 2:** `/ativos/{s}/fatores` (LAC-GES-2), `/ativos/{s}/proventos-contabeis` (LAC-GES-3), `/validacao/saude-dados` com 13 fontes e `coberturaProventosContabeis` (LAC-GES-4), `/validacao/backtest?metodo=RANKING` (LAC-GES-1), favoritos/camada Base, `/setores`, `/pregoes`, `/painel/ativos` (TASK-UX-5), `/ativos/{s}/opiniao` (TASK-OPI-1). GETs não gravam nem publicam.
- **Obsoleto nesta spec:** blocos Gemini/S3 e `ddl-auto=update` (hoje `validate`), fluxos de scheduler de 5 s — o ciclo atual é por camada (V13).
- **Fila local:** LAC-GES-1..4 `IMPLEMENTADO` (f548f97). Sem tarefa aberta própria; quando o painel (LAC-FE-*) pedir campo novo, abrir tarefa no hub.
- **Arquivos não commitados de outra sessão (contratos/inbox):** `entrypoint/controller/*`, `ServicoAtivo`, `ServicoAtualizacaoCache`, `ConsolidadorAnaliseAcao`, `application.properties`, `contracts/` — não editar nem commitar sem o dono.

## 2. Visão do produto (funcional)

`gestor-ativos-brutos` é a **porta de entrada** do ecossistema B3. É uma API Spring Boot que:

1. **Coleta** cotações e séries históricas OHLCV na **BRAPI** (`https://brapi.dev`).
2. **Publica** os dados brutos em filas **SQS** para o worker Python `gerar-insights`, que faz o valuation.
3. **Lê** os insights que o `gerar-insights` gravou em MySQL (`insight_acao`), consolida os sinais e deriva a decisão (sentimento, risco, recomendação) por **regras deterministicas**, sem IA nem storage externo.

> **ATUALIZAÇÃO (2026-09-25):** Gemini e S3 foram removidos deste serviço (DynamoDB, nunca usado, foi removido do `gerar-insights`). A decisão consolidada agora é calculada em `MontadorDecisaoDeterministica` a partir dos mesmos dados de `AnaliseConsolidadaDTO`, sem chamada externa nem persistência de arquivo. Itens marcados OBSOLETO abaixo (`ISS-13`, `ISS-16`, `F1`, `F3`, `TASK-22`, `TASK-23`, `CTR-04`, `REQ-05`) eram específicos do fluxo removido. **`ISS-02` continua PLANEJADO e agora é mais relevante**: o bug de contagem alimenta diretamente `MontadorDecisaoDeterministica`, não só o prompt do Gemini.
>
> **ATUALIZAÇÃO (2026-09-25, 2ª):** `POST /ativos/registrar/{ativo}` deixou de enfileirar em memória (`ISS-08` **IMPLEMENTADO**). Agora persiste o ativo na tabela `ativo_monitorado` (já existia no schema, nunca usada até aqui) via `ServicoAtivoMonitorado`, dispara a primeira coleta robusta na hora, e o `AgendadorAtivos` reprocessa cada ativo ativo automaticamente a cada `intervalo_segundos` (30s por padrão) — sobrevive a restart. Novo `GET /ativos/registrados` lista o que está cadastrado, consumido pela nova aba "Monitorados" do front (`painel-ativos-frontend`, `TASK-01`/`ISS-01` do front **IMPLEMENTADO**).

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
| GET | `/empresas/{simbolo}/comunicados` | Linha do tempo dos comunicados oficiais da CVM do ticker (`categorias`, `desde`, `ate`, `pagina`, `tamanho` ≤ 100), mais recente primeiro (`LinhaDoTempoComunicadosDTO`) | — |
| GET | `/comunicados/newsletter` | Edição da carteira monitorada agrupada por ticker e ordenada por relevância (`semana` ISO `2026-W39`, ou `desde`+`ate`; sem parâmetro, a semana do documento mais recente) (`NewsletterComunicadosDTO`) | — |
| GET | `/painel/ativos` | Tabela única paginada para o painel, combinando Base, Favoritos, Monitorados e Setores em uma resposta com preço oficial, variação, sparkline, sinal, último comunicado e selos (`AtivoListagemDTO`) | — |
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
Java 21 (build Maven/Temurin 21 e runtime Temurin JRE 21), Spring Boot 3.3.0, Spring Web MVC, Spring Data JPA/Hibernate, MySQL Connector/J, AWS SDK v2 (SQS), ModelMapper 3.1.1, Lombok, Micrometer Prometheus e Logstash Logback Encoder. As chamadas HTTP externas usam `RestTemplate`; não há código reativo nem clientes Feign.

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

`application.properties` contém as configurações compartilhadas. O perfil `docker` tem overrides próprios em `application-docker.properties`; `debug` habilita SQL detalhado, sem poluir os perfis `dev`, `test` ou `docker`.

### 3.5 Tabela `ativo_monitorado` (carteira de monitoramento, 2026-09-25)

Definida em `infra-b3-ecossytem/mysql-migrations/V1__baseline.sql`, mapeada por `AtivoMonitoradoEntity`. Fonte de verdade da carteira recorrente (Fluxo E, seção 2.2) — substituiu a fila em memória do `AgendadorAtivos`.

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
| CTR-03 | Consome (leitura) | MySQL `insight_acao` | Colunas `simbolo`, `data_analise`, `preco_justo_graham`, `margem_seguranca_percent`, `recomendacao`, `detalhes_json` — **escritas pelo `gerar-insights`**. Desde 2026-10-07 aceita payload 3.0 (`sinal_quantitativo`, `aviso_legal`) e mantém leitura do histórico 2.1; `SEM_MARGEM` não é contado como direção de venda |
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
**IMPLEMENTADO (2026-09-25, ver `ISS-08`):** Cada `POST /ativos/registrar/{ativo}` persiste em `ativo_monitorado` e entra em monitoramento recorrente real (30s por padrão, configurável por linha via `intervalo_segundos`, mínimo 30 pelo `CHECK` do schema). Sobrevive a restart do serviço. Deduplicação por símbolo via `UNIQUE KEY uq_ativo_monitorado_simbolo` (upsert). Não há UI nem endpoint para desativar/pausar um ativo (`ativo=false`) — a coluna existe no schema mas nada a escreve ainda; fora de escopo do pedido original.

---

## 6. Requisitos

### 6.1 Funcionais

| ID | Requisito | Status |
|---|---|---|
| REQ-01 | Consultar a cotação de um ativo na BRAPI e devolvê-la via HTTP | IMPLEMENTADO |
| REQ-02 | Publicar a cotação bruta em `tratar-ativos` (CTR-01) | IMPLEMENTADO |
| REQ-03 | Publicar série histórica em `sqs-registrar-series-historicas` (CTR-02) | IMPLEMENTADO — cadastro POST e coleta agendada; GET não publica |
| REQ-04 | Gerar análise consolidada a partir dos insights por regras determinísticas | IMPLEMENTADO — enum canônico e compatibilidade com VENDA legada |
| REQ-05 | Salvar, listar e baixar análises no S3 | IMPLEMENTADO (descontinuado) (2026-09-25) |
| REQ-06 | Agendar coleta **recorrente** de uma carteira de ativos | EM ANDAMENTO (`TASK-20`: cron intradiário em dias úteis, 10:05–17:35; favoritar pode coletar imediatamente fora da janela e não há calendário de feriados) |
| REQ-07 | Ler apenas insights já persistidos, sem encadear publicação e leitura síncronas | IMPLEMENTADO — decisão determinística na consulta; geração Gemini descontinuada |
| REQ-08 | Expor o retrato bruto (não mediado) de um único ciclo de análise, para transparência de metodologia | IMPLEMENTADO (2026-09-25, `GET /analises/{simbolo}/fundamentos`) |
| REQ-09 | Expor fundamentos contábeis da CVM com múltiplos derivados do preço atual | IMPLEMENTADO (2026-09-26, `GET /analises/{simbolo}/fundamentos-cvm`); lê `indicador_fundamentalista` (`infra#CTR-06`), escrita pelo ETL `etl-fundamentos-cvm` |
| REQ-10 | `GET /ativos/registrados` passa a ser contrato de navegacao, nao so de listagem | IMPLEMENTADO (2026-09-26); o painel usa a carteira para popular o seletor de ativos de todas as abas, entao indisponibilidade dessa rota degrada a navegacao do front (que cai para busca manual) |
| REQ-13 | Expor o diário de sinais (`GET /validacao/diario?simbolo=&limite=`): totais, placar por versão × recomendação × horizonte com taxa-base da direção apostada (compra: fração de janelas em alta; venda: em queda) e selo de amostra mínima (30), e a linha do tempo dos sinais com o resultado de cada horizonte | IMPLEMENTADO (2026-09-26, `ValidacaoController` + `ServicoDiarioDeSinais`). Lê `sinal_diario`/`sinal_resultado` (`infra#CTR-11`, escritas só pelo gerar-insights) por `NamedParameterJdbcTemplate`, sem entidade JPA, com leitura JDBC; Hibernate configurado para validação. Janelas com `evento_suspeito` ficam fora do placar. `ServicoDiarioDeSinaisTest` (7 casos) |
| REQ-12 | Manter o histórico diário completo do CDI (SGS 12) desde `indices.macro.historico.inicio` (padrão 2016-01-01), base do excesso sobre o CDI no backtest e no diário de sinais | IMPLEMENTADO (2026-09-26, `CargaHistoricoIndicesMacro` na subida, em thread virtual): busca ano a ano só o que falta antes do ponto mais antigo (folga de 7 dias para o 1º dia útil) e buracos recentes maiores que o ciclo horário; com a série completa não faz chamada. `ServicoAtualizacaoIndicesMacroTest` (6 casos) |
| REQ-11 | Expor os comunicados oficiais da CVM (base IPE) por ticker e como newsletter da carteira | IMPLEMENTADO (2026-09-26, `ComunicadoController` + `ServicoComunicados`); lê `comunicado_cvm` (`infra#CTR-08`, escrita só pelo ETL) juntando com `cvm_ticker`; contrato HTTP `infra#CTR-10`. Somente leitura: entidade `@Immutable`, sem `unique` declarado e Hibernate configurado para validação |
| REQ-14 | Expor listagem única de ativos para o painel, evitando 3 chamadas por linha na fusão Base + Favoritos + Setores + Monitorados | IMPLEMENTADO (2026-10-07, `GET /painel/ativos`, TASK-UX-5); leitura JDBC, sem BRAPI, sem gravação e sem publicação |
| REQ-15 | Expor a opinião por horizonte (`opiniao_ia`, infra V22, escrita pelo gerar-insights) ao painel: pregão mais recente do ativo, um item por horizonte (21/63/126 pregões), linha do modelo local antes da de regra, aviso fixo de regra experimental | IMPLEMENTADO (2026-10-07, `GET /ativos/{s}/opiniao`, TASK-OPI-1); leitura JDBC, vazio sem a V22, sem gravação e sem publicação |

Critérios de aceite de referência:
- **REQ-02** — *Dado* que a BRAPI devolve PETR4, *quando* `GET /ativos/PETR4` é chamado, *então* uma mensagem com `symbol=PETR4` e `regularMarketPrice` numérico chega a `tratar-ativos`.
- **REQ-04** — *Dado* 10 insights com 4 `VENDA_VALUATION`, *quando* a consolidação roda, *então* `percentualSinaisVenda = 40`.
- **REQ-05** — OBSOLETO: critério do fluxo S3 removido.
- **REQ-11** — *Dado* os comunicados de 2026 carregados, *quando* `GET /empresas/PETR4/comunicados?categorias=FATO_RELEVANTE,COMUNICADO_MERCADO&desde=2026-01-01&ate=2026-12-31` é chamado, *então* `total = 93`. *Dado* categoria desconhecida, semana malformada ou `desde` > `ate`, *então* 400. *Sem* `semana`, a newsletter usa a semana do documento mais recente (`dadosAte`), não a corrente, porque a CVM republica com ~1 semana de atraso.

### 6.2 Não funcionais

| ID | Requisito | Status |
|---|---|---|
| NFR-01 | Nenhum segredo em arquivos versionados ou em imagens | EM ANDAMENTO (`ISS-01`; configurações atuais sem hardcode, histórico ainda requer tratamento; `run-local.env` staged para remoção do índice e ignorado localmente) |
| NFR-02 | GET sem gravação nem publicação; eventos com chave estável | IMPLEMENTADO — fallbacks somente consultam BRAPI; inbox transacional no consumidor |
| NFR-03 | Resiliência nas integrações (BRAPI, SQS) com backoff e timeouts | EM ANDAMENTO (`ISS-10`) |
| NFR-04 | Schema com proprietário único; aplicação só valida | IMPLEMENTADO — Flyway na infraestrutura; validate em configuração base, dev, test e compose |
| NFR-05 | Testes unitários e de integração reais no CI | IMPLEMENTADO (`ISS-04`; executados no build da imagem) |
| NFR-06 | Logs estruturados enviados ao ELK sem ruído nem duplicação | IMPLEMENTADO (2026-10-08: Logstash só no perfil docker; arquivo só fora do docker) |
| NFR-07 | Imagem mínima (JRE), sem root, com perfil de configuração existente | IMPLEMENTADO (`ISS-11`; execução pendente) |

---

## 7. Problemas identificados

### 7.1 Segurança e integração

| ID | Sev. | Problema | Evidência | Impacto | Correção sugerida | Status |
|---|---|---|---|---|---|---|
| ISS-01 | **Crítico** | Não foi identificado valor real hardcoded nas configurações atuais; commits anteriores à remoção ainda podem conter o segredo | `src/main/resources/application-test.properties`, `application-dev.properties` e `application-docker.properties` referenciam `BRAPI_API_KEY` sem valor secreto hardcoded. O histórico registra `aa4241e Remove chave real da BRAPI do arquivo versionado`; `infra-b3-ecossytem/docker-compose-local.yml` está ignorado pelo Git e referencia a variável. `run-local.env` está staged para remoção do índice e ignorado; a cópia local permanece | Um segredo no histórico Git pode continuar exposto/reutilizável | Rotação e revogação dependem do usuário. Gitleaks em CI não foi adicionado para preservar os workflows, conforme instrução explícita | EM ANDAMENTO — configs atuais sem hardcode; histórico e rotação/revogação pendentes; gitleaks em CI adiado |
| ISS-02 | Alto | A consolidação contava `"VENDA"`, mas o produtor emite a família `"VENDA_*"` | `ConsolidadorAnaliseAcao` agora agrupa recomendações pelo prefixo `VENDA` e ignora valores nulos | Percentual volta a representar os sinais de venda | Teste unitário cobre família e nulos | IMPLEMENTADO (2026-09-26) |
| ISS-03 | Alto | Condição de corrida: publica no SQS e **lê `insight_acao` na sequência**, antes de o worker processar a mensagem | `service/ServicoAtivo.java` (`processar`, `processarRobusto`) | A análise da IA ignora o dado recém-coletado; na primeira coleta de um ativo não há análise | Separar os passos: a análise de IA roda em outro gatilho (evento "insight gerado" via SNS/SQS ou agendamento posterior) | PLANEJADO |
| ISS-04 | Alto | Um teste vazio afirmava apenas `assertTrue(true)` e o Dockerfile pulava testes | `src/test`, `Dockerfile` | Regressões passam direto | Manter a suíte de testes existente e executar `mvn verify` no estágio de build da imagem | IMPLEMENTADO (2026-10-11; execução local pendente) |
| ISS-05 | Alto | Propriedade de schema unificada | Flyway da infraestrutura; Hibernate validate; inicialização SQL desativada | Evita alteração de tabelas por ORM | IMPLEMENTADO |
| ISS-06 | Médio | `ConfigSqs` ignora a configuração: credenciais fixas `"test"/"test"` e região fixa `SA_EAST_1` | `config/ConfigSqs.java` | Não funciona em AWS real | Região vem de property/env; credenciais estáticas só quando configuradas, senão usa `DefaultCredentialsProvider` | IMPLEMENTADO (2026-10-08) |
| ISS-07 | Médio | `ConversorJson` usa `new ObjectMapper()` sem `JavaTimeModule`; `Ativo.regularMarketTime` é `LocalDateTime` e vem de uma String via ModelMapper | `tools/ConversorJson.java`, `external/Ativo.java` | O campo tende a chegar nulo, ou a serialização falha se for preenchido; o consumidor perde o timestamp da cotação, necessário para idempotência | Injetar o `ObjectMapper` do Spring; converter o epoch da BRAPI para `Instant`; teste de contrato | PLANEJADO (a verificar com teste) |
| ISS-08 | Médio | ~~O agendador não é recorrente e a fila fica em memória~~ | `entrypoint/schedule/AgendadorAtivos.java` | ~~Perda de trabalho no restart; README incorreto~~ | Carteira persistida em `ativo_monitorado` + tick de 5s checando `intervalo_segundos` por ativo + deduplicação por `UNIQUE KEY` | IMPLEMENTADO (2026-09-25). Para a coleta intradiária dos favoritos, `AgendadorCacheAtivos` agenda ciclos de 10:05 a 17:35 em dias úteis; o favoritar ainda pode disparar coleta imediata fora dessa janela e não há calendário de feriados (`TASK-20`) |
| ISS-09 | Médio | GETs de cotação, histórico e perfil gravavam ou publicavam no fallback | Fallbacks consultam sem persistência; cadastro POST e agendador mantêm escrita | Sem efeitos de negócio ao navegar | IMPLEMENTADO |
| ISS-10 | Médio | Retentativas do SQS sem backoff; `RestTemplate` sem timeout; a espera de 4 s bloqueia a thread do scheduler | `AdaptadorFilaSqs.java`, `ClienteBrApi.java`, `AgendadorAtivos.java` | Tempestade de retentativas; threads presas | Backoff exponencial (Resilience4j/Spring Retry); timeouts de conexão e leitura; rate limiter | PLANEJADO |
| ISS-11 | Médio | Dockerfile: JDK completo em runtime, root, perfil `docker` inexistente, testes ignorados e build Temurin 24 para alvo Java 21 | `Dockerfile` | Imagem pesada e insegura; sem o compose, a imagem sobe sem configuração | Runtime `eclipse-temurin:21-jre-alpine`, usuário não-root, `application-docker.properties` e testes no build da imagem | IMPLEMENTADO (2026-10-11; execução local pendente) |
| ISS-12 | Médio | Logback envia sempre para `logstash:5000` e também grava em arquivo com padrão texto, que o Logstash lê com codec JSON | `src/main/resources/logback-spring.xml`, `infra-b3-ecossytem/logstash.conf` | Erros de conexão fora do Docker; parse falho e logs duplicados no Elasticsearch | Appender Logstash só no perfil docker; arquivo só fora do docker. O input de arquivo da infra fica para logs locais legados | IMPLEMENTADO (2026-10-08) |
| ISS-13 | Baixo | Prompt pede "risco 0-100", mas o schema espera string; `variacaoMedia` é na verdade a margem média | `tools/MontadorPromptAnalise.java`, `ServicoGemini.java` | Saída da IA inconsistente | Alinhar prompt e schema; renomear para `margemSegurancaMedia` | PLANEJADO |
| ISS-14 | Médio | 15 arquivos registrado no códigos (renomeação de controllers, série histórica, remoção de classes) | `git status` | Risco de perda; PR grande demais | Commitar em partes (refactor de nomes ≠ feature) **depois** de resolver `ISS-01` | PLANEJADO |
| ISS-15 | Baixo | Dependências não usadas: WebFlux, OpenFeign, `jackson-module-kotlin`; `mapstruct-processor` sem MapStruct; SQL detalhado ativo fora de debug | `pom.xml`, properties | Build e imagem maiores; logs verbosos | Remover dependências sem uso e habilitar `show-sql` apenas no perfil `debug` | IMPLEMENTADO (2026-10-11; build pendente) |
| ISS-16 | OBSOLETO | ~~Chave S3 `{simbolo}/analises/{HH:mm:ss}.json` sem data; `:` em nome de arquivo~~ | — | S3 removido em 2026-09-25, análise não é mais persistida em arquivo | — | IMPLEMENTADO (remoção) |
| ISS-17 | Baixo | Consolidação sem janela temporal e com NPE possível se `recomendacao` for nula (`groupingBy` não aceita chave nula) | `tools/ConsolidadorAnaliseAcao.java` | Sinais antigos dominam; erro 500 esporádico | Janela configurável (ex.: 90 dias); filtrar nulos | PLANEJADO |
| ISS-18 | Alto | ~~`processarRobusto` (usado por `GET /ativos/robusto` e por todo cadastro em `ativo_monitorado`) pedia `range=1y` no histórico da BRAPI; o plano Free da BRAPI só libera `1d/5d/1mo/3mo` (`400 INVALID_RANGE`) para qualquer ticker fora da lista de demonstração deles (ex.: `PETR4`, `MGLU3` passavam mesmo sem chave; `WEGE3`, `RAIZ4` não)~~ | `service/ServicoAtivo.java` (achado em 2026-09-25 testando a aba Monitorados: `atualizado_em` nunca avançava pra tickers fora da lista demo) | Com chave BRAPI real configurada, o monitoramento recorrente falhava silenciosamente (retry a cada 5s sem nunca suceder) pra praticamente qualquer ativo cadastrado | `range` extraído para `${brapi.historico.range:3mo}` | IMPLEMENTADO (2026-09-25) |

### 7.2 Visão de analista financeiro
- **F1 (OBSOLETO):** era sobre o Gemini receber só médias agregadas e poder alucinar. Sem IA, não se aplica mais — a decisão é 100% determinada pelas mesmas médias, sem inferência.
- **F2:** IMPLEMENTADO (2026-10-08). A resposta determinística inclui `aviso_legal` fixo informando que o conteúdo é quantitativo experimental e não constitui recomendação de investimento, análise de valores mobiliários ou oferta.
- **F3 (OBSOLETO):** era sobre misturar metodologias (regras Graham + LLM) sem rastreabilidade. Sem Gemini, só resta a metodologia determinística única — não há mais mistura a rastrear.

---

## 8. Roadmap e tarefas

### Fase 0 — Segurança e estabilização (fazer primeiro)

| ID | Tarefa | Resolve | Critério de aceite | Status |
|---|---|---|---|---|
| TASK-01 | Fechar a trilha de segredo da BRAPI: revisar o histórico e os arquivos locais, orientar rotação da chave e avaliar secret scanning | ISS-01, NFR-01 | Configurações atuais sem valor hardcoded; `run-local.env` fora do índice; rotação/revogação da chave histórica pelo usuário. Secret scanning em CI permanece adiado | EM ANDAMENTO — configurações atuais sem hardcode; rotação/revogação depende do usuário; gitleaks em CI adiado para preservar workflows |
| TASK-02 | Commitar as mudanças pendentes em commits separados | ISS-14 | `git status` limpo; PR com descrição | PLANEJADO |
| TASK-03 | Corrigir a contagem de venda (usar a família `VENDA_*` de CTR-03) | ISS-02 | Teste do critério REQ-04 passa | IMPLEMENTADO (2026-09-26) |
| TASK-04 | Manter a suíte de testes reais e executar `mvn verify` no estágio de build da imagem, sem bypass | ISS-04, NFR-05 | O build da imagem falha se compilação ou testes falharem; sem regra de cobertura mínima | IMPLEMENTADO (2026-10-11; execução pendente) |
| TASK-05 | Dockerfile multi-stage com JRE 21, usuário não-root e `application-docker.properties` | ISS-11, NFR-07 | `docker run` sem o compose sobe e responde em `/actuator/health` (com as dependências apontadas) | IMPLEMENTADO (2026-10-11; execução pendente) |

### Fase 1 — Integração correta

| ID | Tarefa | Resolve | Critério de aceite | Depende de | Status |
|---|---|---|---|---|---|
| TASK-10 | `ObjectMapper` do Spring + `regularMarketTime` como `Instant` ISO-8601 no payload | ISS-07 | Teste de contrato: a mensagem publicada contém `regularMarketTime` não nulo | TASK-04 | PLANEJADO |
| TASK-11 | `ddl-auto=validate` | ISS-05 | A aplicação falha no startup se o schema divergir | DEC do ecossistema sobre schema | PLANEJADO |
| TASK-12 | `GET /ativos/{ativo}` sem efeito colateral; `POST /ativos/{ativo}/coletas` publica; atributo `dedupKey` na mensagem | ISS-09, NFR-02 | Chamar o GET 5× não gera mensagens | — | PLANEJADO |
| TASK-13 | Configuração SQS via properties e cadeia de credenciais padrão | ISS-06 | Mesma imagem funciona com LocalStack e com AWS real mudando só o env | — | IMPLEMENTADO (2026-10-08) |
| TASK-14 | Backoff + timeouts (Resilience4j) para BRAPI e SQS | ISS-10, NFR-03 | Testes com falha simulada respeitam o backoff | TASK-04 | PLANEJADO |
| TASK-15 | Logback por perfil; logs de arquivo em JSON | ISS-12, NFR-06 | Perfil docker envia ao Logstash; fora do docker grava arquivo local, evitando duplicidade | — | IMPLEMENTADO (2026-10-08) |

### Fase 2 — Produto

| ID | Tarefa | Resolve | Critério de aceite | Depende de | Status |
|---|---|---|---|---|---|
| TASK-20 | Carteira persistida + coleta intradiária agendada por cron em janela de pregão | ISS-08, REQ-06 | O cron coleta favoritos às :05 e :35, das 10h às 17h, de segunda a sexta; favoritar pode disparar coleta imediata fora da janela; não há calendário de feriados | TASK-12 | EM ANDAMENTO (2026-10-11) — `AgendadorCacheAtivos.CRON_INTRADIARIO` (`0 5,35 10-17 * * MON-FRI`, fuso `America/Sao_Paulo`) agenda 16 ciclos entre 10:05 e 17:35 em dias úteis. `AgendadorCacheAtivosTest.intradiario_tem_16_ciclos_de_10h05_a_17h35_so_em_dia_util` cobre os horários e fim de semana; não cobre feriados. A coleta imediata ao favoritar permanece fora do cron |
| TASK-21 | Análise de IA disparada por evento "insight gerado" (SNS `transmitir-lote-dados` ou fila nova), não na sequência da publicação | ISS-03, REQ-07 | A análise usa o insight do dia | contrato novo no ecossistema | PLANEJADO |
| TASK-22 | OBSOLETO — prompt/schema do Gemini removidos; disclaimer legal (`F2`) deve ser adicionado direto na resposta HTTP de `MontadorDecisaoDeterministica` | F2 | Resposta HTTP contém `aviso_legal` | TASK-03 | IMPLEMENTADO (2026-10-08) |
| TASK-23 | OBSOLETO — S3 removido, sem chave a corrigir | ISS-16 | — | — | IMPLEMENTADO (remoção) |
| TASK-24 | Limpeza do `pom.xml` | ISS-15 | Dependências sem uso removidas; build verde | TASK-04 | IMPLEMENTADO (2026-10-11; build pendente) |
| TASK-25 | API de comunicados oficiais da CVM: linha do tempo por ticker e newsletter semanal | REQ-11 | Critério REQ-11 passa; `ServicoComunicadosTest` verde | ETL `--comunicados` (etl#REQ-09) | IMPLEMENTADO (2026-09-26) |
| TASK-26 | Tela de comunicados no painel (`#/comunicados`) consumindo `infra#CTR-10` | REQ-11 | Chips por ticker, linha do tempo, link "Abrir documento na CVM ↗" | TASK-25 | IMPLEMENTADO (2026-09-26, `painel#REQ-08`) |
| TASK-UX-5 | Endpoint de listagem única de ativos para o painel (`GET /painel/ativos`) com preço, variação, sparkline, sinal, último comunicado, favorito, setor e selos | REQ-14, `painel#TASK-UX-5` | Resposta paginada entrega a linha inteira em duas leituras por página; GET sem efeito colateral; teste de serviço cobre variação, sinal/comunicado ausentes, defasagem e limite de tamanho | — | IMPLEMENTADO (2026-10-07) |
| TASK-OPI-1 | `GET /ativos/{s}/opiniao` (`OpiniaoAtivoController`, `ServicoOpiniaoIa`, `RepositorioOpiniaoIa`) | REQ-15, infra V22 | Modelo prevalece sobre regra por horizonte; entre modelos, o mais recente; JSON em camelCase com `oQueInvalida`; aviso sempre; teste de serviço cobre escolha, vazio, JSON ausente e serialização | — | IMPLEMENTADO (2026-10-07) |

---

## 9. Decisões

| ID | Pergunta | Opções | Status |
|---|---|---|---|
| DEC-01 | Gatilho da análise de IA | síncrona no endpoint / evento pós-insight / agendada | PLANEJADO |
| DEC-02 | Lista de ativos acompanhados | IMPLEMENTADO (2026-09-25): tabela MySQL `ativo_monitorado` + endpoints de carteira (`POST /ativos/registrar/{ativo}`, `GET /ativos/registrados`); configuração avulsa não é a fonte da carteira |
| DEC-03 | Manter WebFlux ou só Web MVC | Só Web MVC: endpoints são síncronos e não há código reativo | IMPLEMENTADO (2026-10-11) |
| DEC-04 | Nome e contrato das recomendações | Enum gerado do JSON Schema canônico em infra/contracts | IMPLEMENTADO |

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

## Revisão integrada de 2026-09-27

| Entrega | Estado | Evidência e limite |
|---|---|---|
| Proprietário único do schema | IMPLEMENTADO | Infra/Flyway: V1 bootstrap, V11 inbox; serviços não executam migrations |
| Eventos e recomendações | IMPLEMENTADO | schemas canônicos em infra/contracts; enum gerado em Java, Python e JS; versões desconhecidas ficam para DLQ |
| Leituras HTTP e idempotência | IMPLEMENTADO | GET sem persistência/publicação; inbox e efeitos na mesma transação; ACK posterior ao commit |
| Verificação desta entrega | EM ANDAMENTO | Resultados registrados em infra/VERIFICACAO-2026-09-27.md; não representa deploy no banco em uso |

## Plano LAC: 9 lacunas de assertividade (proposta de 30-09-2026, EM AVALIAÇÃO)

Plano completo e a migração única **V16** em `infra-b3-ecossytem/SPEC.md`, seção Plano LAC. O gestor **só lê**: ETL e gerar-insights gravam. Nenhuma chamada nova à BRAPI.

| ID | Tarefa | Tabelas lidas |
|---|---|---|
| LAC-GES-1 | `GET /validacao/backtest?metodo=RANKING`: por versão de regra e janela, correlação de ranking média com intervalo, retorno por quintil e diferença entre o quintil 5 e o 1; `hipotese` e `numero_tentativa` da execução | `backtest_execucao`, `backtest_ranking_mes`, `backtest_ranking_quintil` |
| LAC-GES-2 | `GET /ativos/{simbolo}/fatores`: último valor de cada fator ativo, com percentil no universo e no setor | `fator_valor`, `fator_definicao` |
| LAC-GES-3 | `GET /ativos/{simbolo}/proventos-contabeis`: proventos por período (DVA), ao lado dos eventos de `/proventos/{simbolo}` | `provento_contabil` |
| LAC-GES-4 | `GET /validacao/saude-dados` ganha fontes: `EVENTOS_CORPORATIVOS`, `FATORES` (idade do último cálculo mensal) e cobertura de proventos da DVA | `etl_execucao`, `fator_valor`, `provento_contabil` |

- Acesso por JDBC (como `brapi_consumo` e `snapshot_fechamento_brapi`), sem entidade JPA: com `ddl-auto=validate` o gestor não sobe antes da V16 se houver entidade.
- Aceite: os 4 endpoints respondem com o banco sem a V16 (lista vazia e aviso no log, nunca 500) e com a V16 (dados reais); testes de contrato em `contracts/`.

---

## Plano OPR: APIs de leitura do sistema operável simulado (2026-10-08)

**Status:** PLANEJADO · **Contexto:** `infra-b3-ecossytem/SPEC.md`, Plano OPR. O gestor não decide operação e não calcula regra de entrada/saída; ele expõe ao painel e aos alertas a leitura consistente das tabelas operacionais gravadas pelo `gerar-insights`.

**Meta.** Oferecer endpoints HTTP sem efeito colateral para resumo operacional, operações simuladas, diário, bloqueios e saúde. Todas as rotas são leitura; geração/avaliação do diário fica no worker.

### Contratos HTTP propostos

| Rota | Descrição |
|---|---|
| `GET /operacional/resumo` | status atual (`NAO_OPERAVEL`, `EM_OBSERVACAO`, `PAPER_TRADING_ELEGIVEL`, `BLOQUEADO`), retorno líquido, CDI, excesso, drawdown, dias avaliados |
| `GET /operacional/operacoes?status=ABERTA|FECHADA&simbolo=` | lista operações simuladas com entrada, saída, tamanho, custos e motivo |
| `GET /operacional/diario?inicio=&fim=` | série diária de patrimônio teórico, retorno bruto/líquido, CDI, excesso e drawdown |
| `GET /operacional/bloqueios?data=` | sinais rejeitados por liquidez, dado ausente, evento, fundamento futuro ou regra de risco |
| `GET /operacional/saude` | prontidão dos dados, última avaliação, violações e status para alerta |

### Tarefas desta aplicação

| ID | Tarefa | Resolve | Critério de aceite | Depende de | Status |
|---|---|---|---|---|---|
| OPR-GES-1 | Criar repositórios JDBC de leitura para `operacao_simulada`, `diario_operacional`, `evento_operacional` e `regra_operacional`, sem entidades JPA novas | OPR leitura | Banco sem tabelas OPR responde lista vazia/aviso e nunca 500; banco com tabelas retorna dados reais | infra#OPR-INFRA-1 | IMPLEMENTADO (2026-10-11): `RepositorioOperacional` (só SELECT, JDBC) para as 4 tabelas da V23; sem as tabelas devolve vazio e avisa uma vez por tabela no log. `RepositorioOperacionalTest` |
| OPR-GES-2 | Implementar `GET /operacional/resumo` e `GET /operacional/diario` com DTOs em camelCase e valores monetários/percentuais consistentes | OPR painel | Teste de serviço cobre CDI, excesso líquido, drawdown e janela sem dados | OPR-GES-1 | IMPLEMENTADO (2026-10-11): `OperacionalController` + `ServicoOperacional.resumo/diario` no formato CTR-OPR-PAI do painel (`statusSistema`, `ultimoPregao`, `pregoesAvaliados`, `pregoesMinimos`, `motivosStatus [{codigo, descricao}]`, `dataInicio`, `impostoEstimadoMes`; diário em `itens`); excesso derivado quando falta. `ServicoOperacionalTest` |
| OPR-GES-3 | Implementar `GET /operacional/operacoes` e `GET /operacional/bloqueios` com filtros por status, símbolo, data e motivo | OPR painel | Filtros são combináveis; operação fechada traz motivo de saída e custos; bloqueio traz motivo legível | OPR-GES-1 | IMPLEMENTADO (2026-10-11): `operacoes` (status, símbolo, datas, motivo, limite; saída com motivo legível e custos totais) e `bloqueios` agrupado por ativo (`itens [{simbolo, faixa, motivos [{codigo, descricao, detalhe, valor, limite}]}]`; lê `payload_json.motivos[]` do gerar-insights 1b3fd77, e `bloqueios[]` do formato antigo), filtros por data, símbolo e código; `operacoes` devolve `itens` |
| OPR-GES-4 | Implementar `GET /operacional/saude` para consumo do painel e do alerta Telegram, com status, última execução e violações de regra | OPR alerta | Retorna `BLOQUEADO` quando dados do dia estão incompletos ou drawdown/liquidez violam limite | OPR-GES-2, OPR-GES-3 | IMPLEMENTADO (2026-10-11): `/operacional/saude` (`status`, `ultimaExecucao`, `dadosDoDiaCompletos`, `violacoes [{codigo, descricao}]`) bloqueia por diário que não cobre o último pregão, violação de liquidez, drawdown no limite e diário já BLOQUEADO. Limite de drawdown ainda não existe em `regra_operacional` (a regra inicial não o define): sem ele o drawdown só gera aviso |
| OPR-GES-5 | Testes de contrato para as rotas OPR e documentação no SPEC/README, sem introduzir efeito colateral em GET | NFR-02 | Chamar cada GET 5 vezes não altera nenhuma tabela nem publica mensagem | OPR-GES-2..4 | IMPLEMENTADO (2026-10-11): `OperacionalControllerTest` (camelCase, 400, 200 vazio sem tabelas, só rotas GET, 5 chamadas de cada rota só executam SELECT) e seção no README. Rodado com JDK 26 e `-Dnet.bytebuddy.experimental=true` (só há JDK 26 neste PC; o projeto usa 21) |

### Aceite local

- `./mvnw test` cobre os serviços OPR com fixtures de banco e sem depender de Docker real.
- O painel consegue renderizar a aba Operabilidade com dados vazios, parciais e completos.
- Nenhuma rota OPR inicia coleta, análise, operação ou mensagem; geração é responsabilidade do worker/agendamento.

---

## Plano OPERABILIDADE V2: APIs para cockpit operacional e ticker enriquecido (2026-10-11)

**Status:** EM ANDAMENTO · **Contexto:** `infra-b3-ecossytem/SPEC.md`, Plano OPERABILIDADE V2. O gestor continua sendo camada de leitura: junta tabelas e contratos em HTTP para o painel, sem decidir operação nem publicar eventos.

### Meta

Expor dados prontos para uma experiência de ticker: cards enriquecidos, aba Operabilidade, consenso de sinais, tese explicada, timeline, filtros e alertas. Todas as rotas são `GET` e sem efeito colateral.

### Rotas propostas

| Rota | Uso |
|---|---|
| `GET /operacional/tickers` | lista paginada de cards enriquecidos por status, liquidez, setor, sinal, confiança, evento e bloqueio |
| `GET /operacional/tickers/{simbolo}` | snapshot completo do ticker |
| `GET /operacional/tickers/{simbolo}/consenso` | consenso entre regra principal, técnico, IA, v2/ranking e operacional |
| `GET /operacional/tickers/{simbolo}/tese` | tese explicada, positivos/negativos/riscos/invalidadores |
| `GET /operacional/tickers/{simbolo}/timeline` | eventos operacionais e de mercado em ordem |
| `GET /operacional/filtros` | opções agregadas de filtros e contagens |
| `GET /operacional/alertas` | alertas UX ativos para painel |

### Tarefas desta aplicação

| ID | Tarefa | Resolve | Critério de aceite | Depende de | Status |
|---|---|---|---|---|---|
| OPRV2-GES-1 | Expor `GET /operacional/tickers` e `GET /operacional/tickers/{simbolo}` lendo `ticker_operacional_snapshot` | cards enriquecidos | Paginação, filtros por status/liquidez/setor/sinal/confiança; sem tabela V2 responde vazio com aviso | infra#OPRV2-INFRA-2, gerar-insights#OPRV2-INS-1 | IMPLEMENTADO (2026-10-11): snapshots atuais deduplicados por ticker (sem corte interno de 5.000), filtros e paginação; cards incluem sinal técnico estruturado e motivos de bloqueio; schema anterior tolerado quando campos estão no payload |
| OPRV2-GES-2 | Expor consenso e tese (`/consenso`, `/tese`) com DTOs estáveis em camelCase | explicabilidade | Sinal analítico e operabilidade aparecem separados; score não calibrado é explícito | gerar-insights#OPRV2-INS-2..3 | IMPLEMENTADO (2026-10-11): endpoints `/consenso` e `/tese` expõem os blocos materializados no snapshot; quando o snapshot existe sem esses blocos, respondem 200 com aviso |
| OPRV2-GES-3 | Expor `/timeline` com filtros por tipo/severidade/data e limite | timeline do ticker | Timeline ordenada, idempotente, com links para comunicado/documento quando houver | gerar-insights#OPRV2-INS-4, etl#OPRV2-ETL-2 | IMPLEMENTADO (2026-10-11): endpoint lê `ticker_operacional_timeline`, filtra por tipo, severidade e janela, e suporta tanto `data_evento/tipo` quanto `data_pregao/tipo_evento` |
| OPRV2-GES-4 | Expor `/filtros` com contagens agregadas: status operacional, liquidez, setor, risco, bloqueio, evento recente, operação aberta/fechada | filtros avançados | Painel monta chips sem varrer todos os tickers no cliente | OPRV2-GES-1 | IMPLEMENTADO (2026-10-11): contagens e opções derivadas dos snapshots atuais para status, liquidez, setor, sinal, confiança, posição, risco da tese, bloqueio, evento recente, operação e evidência |
| OPRV2-GES-5 | Expor `/alertas` com alertas UX ativos derivados do estado atual: sistema bloqueado, posição pendente/aberta, ticker bloqueado e dados incompletos | central de alertas | GET sem efeito colateral; alertas trazem severidade, categoria, ticker opcional e `createdAt` | infra#OPRV2-INFRA-4, OPR-GES-4 | IMPLEMENTADO (2026-10-11): alertas são reconstruídos do último snapshot por ticker; eventos históricos e operações fechadas não são apresentados como ativos. Drawdown “próximo do limite” foi omitido enquanto não houver limiar formal configurado |
| OPRV2-GES-6 | Testes de contrato e não-regressão GET-only para todas as rotas V2 | NFR-02 | Chamar cada GET repetidamente não grava tabela nem publica mensagem; testes cobrem ausência de tabelas V2 | OPRV2-GES-1..5 | IMPLEMENTADO NO CÓDIGO (2026-10-11): `OperacionalV2ControllerTest` cobre rotas, filtros, alertas derivados do estado atual, ausência de V2 e GET-only; testes Maven não executados conforme instrução do usuário, compilação principal concluída com JDK 26 |

### Dependências para o painel

| Tela/componente | API necessária |
|---|---|
| Cards enriquecidos | `GET /operacional/tickers` |
| Aba Operabilidade do ticker | `GET /operacional/tickers/{simbolo}` + OPR v1 |
| Consenso de sinais | `/consenso` |
| “Por que esse sinal existe?” | `/tese` |
| Timeline | `/timeline` |
| Filtros globais | `/filtros` |
| Central de alertas | `/alertas` |

---

## Plano OPT: APIs para cadeia de opções B3 confiável (2026-10-12)

**Status:** PLANEJADO · **Contexto comum:** ver `infra-b3-ecossytem/SPEC.md`, Plano OPT. A rota atual `GET /ativos/{simbolo}/opcoes` funciona, mas consulta por prefixo (`LIKE 'XXXX%'`) sobre uma tabela contaminada. No diagnóstico local, `BVMF3/B3SA3` responderam `opcoes: []`, enquanto `opcao_b3_diaria` contém muitos instrumentos suspeitos sem vencimento real. O gestor deve expor leitura conservadora e explicar ausência, não inferir cadeia.

### Regras para agentes

- Gestor não corrige COTAHIST nem decide se uma opção é real; consome campos validados pelo ETL/infra.
- Todas as rotas são `GET` e sem efeito colateral.
- IDs `OPT-GES-*` são reservados; não renomear.
- Enquanto `etl#OPT-ETL-2` e `infra#OPT-INFRA-1` não concluírem, a API deve retornar estado vazio/diagnóstico, não dado presumido.

### Tarefas desta aplicação

| ID | Tarefa | Resolve | Critério de aceite | Depende de | Status |
|---|---|---|---|---|---|
| OPT-GES-1 | Consultar opções por `ativo_objeto` e símbolo canônico, abandonando prefixo frágil `LIKE 'PETR%'` | cadeia correta por ativo | `/ativos/BVMF3/opcoes` normaliza para `B3SA3` quando houver identidade; `/ativos/{s}/opcoes` usa `ativo_objeto`; resposta informa `simboloSolicitado`, `simboloCanonico` e `aliasAplicado` | infra#OPT-INFRA-1, etl#OPT-ETL-2 | IMPLEMENTADO (2026-10-12): `OpcaoController` normaliza via `ativo_identidade`; `RepositorioOpcoes` consulta somente por `ativo_objeto` e bloqueia a leitura por prefixo quando a coluna ainda não existe |
| OPT-GES-2 | Expor estado de qualidade na resposta de opções | UX clara | Resposta vazia diferencia `SEM_CADEIA_VALIDA`, `CARGA_OPCOES_AUSENTE`, `BASE_OPCOES_CONTAMINADA`, `TICKER_NORMALIZADO` e inclui `motivo` legível | OPT-GES-1 | IMPLEMENTADO (2026-10-12): contrato `RespostaOpcoesDTO` inclui `simboloSolicitado`, `simboloCanonico`, `aliasAplicado`, `status`, `motivo`, `vencimentos`, `opcoes`; teste `OpcaoControllerTest` cobre base sem `ativo_objeto`, carga ausente e ticker normalizado |
| OPT-GES-3 | Criar endpoint de saúde da cadeia (`GET /opcoes/saude` ou equivalente) | diagnóstico e avaliação | Retorna totais obrigatórios do Plano OPT: válidos, inválidos, vencimento 9999, ativos monitorados com/sem cadeia, prefixos suspeitos e último pregão válido | infra#OPT-INFRA-3, etl#OPT-ETL-4 | IMPLEMENTADO NO CÓDIGO (2026-10-12): `GET /opcoes/saude` lê as views da infra, complementa contagens do cadastro monitorado, lista os 10 prefixos inválidos mais frequentes e responde `BASE_INDISPONIVEL` sem erro quando as migrations ainda não existem; compilação Maven validada com JDK 26, sem executar testes Maven conforme orientação do usuário |
| OPT-GES-4 | Testes de contrato GET-only para opções | não-regressão | Chamadas repetidas não gravam tabela nem publicam mensagem; cobre ticker antigo, ticker sem cadeia e base sem colunas novas | OPT-GES-1..3 | IMPLEMENTADO (Sessão 01, 2026-10-12): `OpcaoControllerTest` cobre baseSemAtivoObjeto (BVMF3→B3SA3, consultas=0), cargaAusente, cadeiaValida com alias, tickerSemCadeia sem inferência por prefixo e 5 chamadas repetidas sem gravação; `SaudeOpcoesControllerTest` cobre métricas obrigatórias e idempotência GET |

### Contrato desejado para vazio explicável

```json
{
  "simboloSolicitado": "BVMF3",
  "simboloCanonico": "B3SA3",
  "aliasAplicado": true,
  "status": "SEM_CADEIA_VALIDA",
  "motivo": "Nenhuma opção válida carregada para o ativo no último pregão.",
  "vencimentos": [],
  "opcoes": []
}
```
