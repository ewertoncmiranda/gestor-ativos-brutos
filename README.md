# Gestor de Ativos Brutos

API backend em Java 21 e Spring Boot 3.3.0 que funciona como porta de entrada do ecossistema de ativos B3. O serviço consulta cotações e séries históricas na BRAPI, publica dados brutos em duas filas SQS e lê análises persistidas no MySQL para produzir uma decisão consolidada por regras determinísticas.

> Estado mapeado diretamente da implementação atual em 25/09/2026. O comportamento documentado abaixo inclui efeitos colaterais e limitações observados no código.

## Visão geral

```text
Cliente HTTP
  ├─ cotação ───────────────> BRAPI /api/quote/{symbol}
  │                              └─> SQS tratar-ativos
  ├─ cotação robusta ────────> BRAPI quote + historical
  │                              ├─> SQS tratar-ativos
  │                              └─> SQS sqs-registrar-series-historicas
  ├─ histórico OHLCV ────────> BRAPI /api/v2/stocks/historical
  └─ análise consolidada ─────> MySQL insight_acao
                                  └─> regras determinísticas
```

## Stack e organização

- Java 21, Spring Boot 3.3.0, Spring MVC e Actuator.
- Spring Data JPA/Hibernate e MySQL Connector/J.
- AWS SDK v2 para SQS.
- `RestTemplate` para BRAPI e ModelMapper para mapear a cotação.
- Micrometer/Prometheus e Logback/Logstash para observabilidade.
- Lombok e Jackson.

| Camada/pacote | Responsabilidade |
| --- | --- |
| `entrypoint/controller` | Rotas REST e validação básica da entrada. |
| `entrypoint/schedule` | `AgendadorAtivos`: reprocessa a carteira persistida em `ativo_monitorado` a cada ciclo devido. |
| `service` | Orquestra BRAPI, SQS, leitura das análises e a carteira de monitoramento (`ServicoAtivoMonitorado`). |
| `external/http` | Cliente HTTP da BRAPI. |
| `external/queue` | Adaptador de publicação no SQS. |
| `external` e `external/dto` | Entidades JPA (`AnaliseAcaoEntity`, `AtivoMonitoradoEntity`) e contratos de entrada/saída. |
| `repository` | Acesso às tabelas `insight_acao` e `ativo_monitorado`. |
| `tools` | Serialização, consolidação e decisão determinística. |
| `exceptions` | Exceções da aplicação e contrato global de erro. |
| `config` | Configuração do cliente SQS e propriedades. |

WebFlux e OpenFeign estão declarados no `pom.xml`, mas não são usados pela implementação: as rotas são Spring MVC e a BRAPI é acessada com `RestTemplate`.

## Rotas HTTP e controllers

| Método | Rota | Controller | Resposta de sucesso | Dependências e efeitos colaterais |
| --- | --- | --- | --- | --- |
| `GET` | `/ativos/{ativo}` | `AtivoController.buscarPorSimbolo` | `200` com `Ativo` | Consulta a cotação na BRAPI e publica o mesmo ativo em `tratar-ativos`. É um GET com efeito colateral. |
| `GET` | `/ativos/robusto/{ativo}` | `AtivoController.buscarPorSimboloComSerieHistorica` | `200` com `Ativo` | Consulta cotação e histórico fixo de 1 ano/1 dia; publica a cotação em `tratar-ativos` e o histórico em `sqs-registrar-series-historicas`. |
| `POST` | `/ativos/registrar/{ativo}` | `AtivoController.registrarAtivo` | `202`, sem corpo | Normaliza o símbolo e faz upsert em `ativo_monitorado` (`COTACAO_E_HISTORICO`, 30s); dispara `processarRobusto` na hora (falha aqui é só logada); o agendador reprocessa o ativo automaticamente a cada 30s a partir daí. |
| `GET` | `/ativos/registrados` | `AtivoController.listarRegistrados` | `200` com `AtivoMonitoradoDTO[]` | Lista a carteira monitorada, ordenada por símbolo. |
| `GET` | `/analises/{simbolo}/analise` | `AnaliseAcaoController.buscarPorSimbolo` | `200` com `RespostaAnaliseIaDTO` | Lê todo o histórico do símbolo em `insight_acao`, consolida os dados e aplica regras determinísticas. |
| `GET` | `/api/v2/stocks/historical` | `HistoricoAcoesController.buscarHistorico` | `200` com `RespostaHistoricoAcoesDTO` | Proxy autenticado para o histórico da BRAPI; não publica em SQS. |
| `GET` | `/actuator` | Spring Boot Actuator | `200` com links dos endpoints expostos | Disponível conforme a exposição do perfil ativo. |
| `GET` | `/actuator/health` | Spring Boot Actuator | `200` ou `503` com o estado de saúde | No perfil `dev`, inclui detalhes de saúde. |
| `GET` | `/actuator/metrics` e `/actuator/metrics/{name}` | Spring Boot Actuator | `200` com nomes ou valores de métricas | Exposto no perfil `dev`. |
| `GET` | `/actuator/prometheus` | Spring Boot Actuator | `200` no formato Prometheus | Exposto no perfil `dev`. |

### `GET /ativos/{ativo}`

O símbolo é repassado à BRAPI sem normalização. Depois da consulta, a aplicação converte o primeiro item de `results` para `Ativo`, serializa-o, publica a mensagem e só então responde.

```bash
curl "http://localhost:9090/ativos/PETR4"
```

Resposta HTTP e mensagem publicada em `tratar-ativos`:

```json
{
  "id": null,
  "symbol": "PETR4",
  "currency": "BRL",
  "shortName": "PETROBRAS PN N2",
  "longName": "Petróleo Brasileiro S.A. - Petrobras",
  "marketCap": 0,
  "regularMarketChange": 0,
  "regularMarketChangePercent": 0,
  "regularMarketTime": null,
  "regularMarketPrice": 0,
  "regularMarketDayHigh": 0,
  "regularMarketDayLow": 0,
  "regularMarketDayRange": "0 - 0",
  "regularMarketVolume": 0,
  "regularMarketPreviousClose": 0,
  "regularMarketOpen": 0,
  "fiftyTwoWeekRange": "0 - 0",
  "fiftyTwoWeekLow": 0,
  "fiftyTwoWeekHigh": 0,
  "priceEarnings": 0,
  "earningsPerShare": 0,
  "logoUrl": "https://..."
}
```

Os valores são ilustrativos; campos ausentes na BRAPI podem permanecer nulos. Valores monetários e indicadores são números JSON.

### `GET /ativos/robusto/{ativo}`

Executa, nesta ordem:

1. `GET https://brapi.dev/api/quote/{ativo}`;
2. `GET https://brapi.dev/api/v2/stocks/historical?symbols={ativo}&range=1y&interval=1d&sortOrder=asc`;
3. publica o contrato `Ativo` em `tratar-ativos`;
4. publica o contrato histórico em `sqs-registrar-series-historicas`;
5. retorna somente o `Ativo` da cotação no corpo HTTP.

```bash
curl "http://localhost:9090/ativos/robusto/VALE3"
```

O histórico publicado em `sqs-registrar-series-historicas` é consumido pelo `gerar-insights`, que persiste os candles em `serie_historica` e calcula sobre eles o sinal técnico (média móvel, z-score, score de volume) usado em `GET /analises/{simbolo}/analise`.

### `POST /ativos/registrar/{ativo}`

```bash
curl -i -X POST "http://localhost:9090/ativos/registrar/vale3"
```

O código é convertido para `VALE3` e persistido em `ativo_monitorado` (upsert por símbolo: se já existe, só reativa `ativo=true`; se é novo, `tipo_coleta=COTACAO_E_HISTORICO` e `intervalo_segundos=30`). Na mesma requisição, `processarRobusto(ativo)` é chamado imediatamente (best-effort — se a BRAPI falhar aqui, o registro em si já foi salvo e o log só avisa; a resposta continua `202`).

A partir daí, `AgendadorAtivos` (`@Scheduled(fixedDelay=5000)`) verifica a cada 5s quais ativos ativos estão "devidos" (`atualizado_em + intervalo_segundos` no passado) e reprocessa cada um — efetivamente a cada ~30s por ativo, sem sincronizar todos no mesmo instante. É recorrente de verdade e sobrevive a restart, porque a carteira mora no banco, não em memória. Deduplicação é garantida pela `UNIQUE KEY` do símbolo. Falhas (ex.: BRAPI fora do ar) não avançam `atualizado_em`, então o ativo continua "devido" e é retentado no próximo tick de 5s, sem backoff.

### `GET /ativos/registrados`

```bash
curl "http://localhost:9090/ativos/registrados"
```

```json
[
  {
    "simbolo": "VALE3",
    "ativo": true,
    "tipoColeta": "COTACAO_E_HISTORICO",
    "intervaloSegundos": 30,
    "criadoEm": "2026-09-25T19:24:48",
    "atualizadoEm": "2026-09-25T19:53:46"
  }
]
```

Lista todos os registros de `ativo_monitorado`, ordenados por símbolo. `atualizadoEm` marca o último processamento **bem-sucedido**; se estiver estagnado enquanto `criadoEm` avança, o ativo está falhando nas tentativas (ver logs do `AgendadorAtivos`).

### `GET /analises/{simbolo}/analise`

```bash
curl "http://localhost:9090/analises/PETR4/analise"
```

Resposta quando existem análises:

```json
{
  "ativo": "PETR4",
  "sentimento": "POSITIVO",
  "forca_sinal": "FORTE",
  "risco": "BAIXO",
  "confianca_analise": 0.5,
  "resumo": "Ativo PETR4: 10 analises entre ...",
  "analise_tecnica": "Sinal predominante: COMPRA ...",
  "analise_fundamentalista": "Margem de seguranca media no periodo: ...",
  "possivel_cenario": "...",
  "recomendacao": "COMPRA"
}
```

Regras aplicadas sobre todas as linhas encontradas, sem filtro temporal:

- sinal predominante: recomendação mais frequente;
- percentual de venda: ocorrências exatamente iguais a `VENDA` divididas pelo total;
- margem média: média de `margem_seguranca_percent`;
- indicadores: média de cada campo numérico de primeiro nível de `detalhes_json`;
- sentimento: `NEGATIVO` a partir de 60% de venda, `POSITIVO` até 40% e `NEUTRO` entre esses limites;
- força: `FORTE` para dominância compra/venda de pelo menos 40 pontos, `MODERADA` a partir de 15 e `FRACA` abaixo disso;
- risco: `ALTO` para margem negativa, `MEDIO` abaixo de 10 e `BAIXO` a partir de 10;
- confiança: `min(quantidadeRegistros / 20, 1)`, entre 0 e 1.

Quando não há registros, a rota continua respondendo `200`; `resumo` informa que não há análise e os demais campos são nulos. Falhas de leitura do banco também são convertidas pelo serviço em lista vazia e, portanto, têm a mesma resposta.

### `GET /api/v2/stocks/historical`

| Query parameter | Obrigatório | Encaminhamento |
| --- | --- | --- |
| `symbols` | Sim, não vazio | Um ou mais tickers, separados por vírgula. |
| `range` | Não | Janela aceita pela BRAPI, por exemplo `1mo` ou `1y`. |
| `interval` | Não | Intervalo dos candles, por exemplo `1d`. |
| `startDate` | Não | Data inicial aceita pela BRAPI. |
| `endDate` | Não | Data final aceita pela BRAPI. |
| `sortOrder` | Não | Ordenação, como `asc` ou `desc`. |

```bash
curl "http://localhost:9090/api/v2/stocks/historical?symbols=PETR4,VALE3&range=1mo&interval=1d&sortOrder=asc"
```

Contrato de resposta:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": {
        "usedInterval": "1d",
        "usedRange": "1mo",
        "historicalDataPrice": [
          {
            "date": 1758758400,
            "open": 0,
            "high": 0,
            "low": 0,
            "close": 0,
            "volume": 0,
            "adjustedClose": 0,
            "dataFormatada": "24/09/2025"
          }
        ]
      }
    }
  ],
  "requestedAt": "...",
  "took": 0
}
```

`date` é um timestamp Unix em segundos. `dataFormatada` é calculada pela aplicação em `dd/MM/yyyy`, no fuso `America/Sao_Paulo`.

## Contratos entre serviços

### SQS: cotação bruta

- Direção: esta aplicação produz a mensagem.
- Fila: `AWS_SQS_QUEUE_URL`, por padrão `tratar-ativos`.
- Corpo: JSON do objeto `Ativo`, em camelCase, com os mesmos campos retornados por `GET /ativos/{ativo}`.
- Publicadores: consulta simples, consulta robusta e processamento do agendador.

### SQS: série histórica

- Direção: esta aplicação produz a mensagem.
- Fila: `AWS_SQS_HISTORICAL_SERIES_QUEUE_URL`, por padrão `sqs-registrar-series-historicas`.
- Corpo: JSON de `RespostaHistoricoAcoesDTO`, com a mesma estrutura da rota de histórico.
- Publicador: somente `GET /ativos/robusto/{ativo}`.

O adaptador SQS faz uma tentativa inicial e até três novas tentativas, sem espera ou backoff. Se todas falharem, a requisição síncrona recebe `503`.

### MySQL: análises de ativos

- Direção: esta aplicação lê os registros pelo fluxo HTTP de análise.
- Tabela: `insight_acao`.
- Consulta usada: `findBySimbolo`, sem ordenação ou limite.

| Coluna | Tipo Java | Observação |
| --- | --- | --- |
| `id` | `Long` | Chave autoincremental. |
| `simbolo` | `String(10)` | Obrigatória e indexada. |
| `data_analise` | `LocalDateTime` | Obrigatória e indexada. |
| `preco_justo_graham` | `BigDecimal(12,4)` | Disponível na entidade, não entra diretamente na decisão. |
| `margem_seguranca_percent` | `BigDecimal(10,4)` | Usada no cálculo da margem média e do risco. |
| `recomendacao` | `String(20)` | Usada no sinal predominante e na contagem literal de `VENDA`. |
| `detalhes_json` | JSON | Só campos numéricos no primeiro nível entram nas médias. |

Com `spring.jpa.hibernate.ddl-auto=update`, a aplicação pode alterar o schema durante a inicialização.

### MySQL: carteira de monitoramento

- Direção: esta aplicação lê e escreve (é a única leitora/escritora).
- Tabela: `ativo_monitorado` (schema definido em `infra-b3-ecossytem/mysql-init`, mapeada por `AtivoMonitoradoEntity`).

| Coluna | Tipo Java | Observação |
| --- | --- | --- |
| `id` | `Long` | Chave autoincremental. |
| `simbolo` | `String(10)` | `UNIQUE`, usada como chave de upsert. |
| `ativo` | `Boolean` | `true` por padrão; não há endpoint para desativar ainda. |
| `tipoColeta` | enum `COTACAO` / `COTACAO_E_HISTORICO` | Cadastros via API entram como `COTACAO_E_HISTORICO`. |
| `intervaloSegundos` | `Integer` | Mínimo 30 (`CHECK` no schema); 30 por padrão. |
| `versao` | `Long` | `@Version`, optimistic locking. |
| `criadoEm` / `atualizadoEm` | `LocalDateTime` | `atualizadoEm` avança só em processamento bem-sucedido. |

### Contrato de erro HTTP

Erros tratados usam o formato:

```json
{
  "timestamp": "2026-09-25T10:30:00-03:00",
  "status": 502,
  "error": "Bad Gateway",
  "code": "BRAPI_INTEGRATION_ERROR",
  "message": "Falha ao consultar BRAPI para o ativo ...",
  "path": "/ativos/PETR4"
}
```

| Status | Código | Situação |
| --- | --- | --- |
| `400` | `BAD_REQUEST` | Parâmetro obrigatório ausente, tipo inválido ou argumento inválido. |
| `404` | `ATIVO_NAO_ENCONTRADO` | A BRAPI não devolveu cotação para o ativo. |
| `502` | `BRAPI_INTEGRATION_ERROR` | Erro HTTP não-404, resposta vazia ou JSON inválido da BRAPI. |
| `503` | `FILA_UNAVAILABLE` | Falha depois das tentativas de publicação no SQS. |
| `500` | `JSON_CONVERSION_ERROR` | Falha ao serializar um payload para a fila. |
| `500` | `INTERNAL_SERVER_ERROR` | Falha não mapeada, sem detalhes internos no corpo. |

## Integrações externas

### BRAPI

- Base fixa: `https://brapi.dev`.
- Recursos usados: `/api/quote/{symbol}` e `/api/v2/stocks/historical`.
- Autenticação: valor de `BRAPI_API_KEY` enviado diretamente no header `Authorization`.
- Cliente: `RestTemplate`, sem timeout ou política de retry configurados.
- Um `404` externo é interpretado como ausência de dados; outros erros HTTP viram `502`.

### AWS SQS ou LocalStack

- Endpoint configurável por `AWS_SQS_ENDPOINT_BASE`.
- Região fixa no código: `sa-east-1`.
- O cliente usa credenciais estáticas `test`/`test`. As propriedades `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` existem, mas não são aplicadas por `ConfigSqs`.
- Filas esperadas: `tratar-ativos` e `sqs-registrar-series-historicas`.

### MySQL

- Configurado por `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.
- Usado para ler `insight_acao`; o Hibernate também está configurado com `ddl-auto=update`.

### Observabilidade

- Actuator/Micrometer expõe, no perfil `dev`, `/actuator/health`, `/actuator/metrics` e `/actuator/prometheus`.
- O Logback escreve no console, em `/app/logs/application.log` e tenta enviar logs para `logstash:5000` via TCP.
- `prometheus.yml` aponta o scraping de métricas; `logstash.conf` define o pipeline local de logs.

### CI/CD e imagem

- Pushes em branches `feature**` disparam a criação automática de PR para `develop`.
- Push em `develop` constrói e publica a imagem no Docker Hub.
- O Dockerfile compila para Java 21 usando uma imagem Maven/Temurin 24 e executa em Temurin 24.
- A imagem define `SPRING_PROFILES_ACTIVE=docker`, mas o repositório não contém `application-docker.properties`. Ao executar a imagem, forneça um perfil existente e todas as propriedades necessárias.

## Configuração

| Variável | Padrão `dev` | Uso efetivo |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` quando informado externamente | Seleciona o arquivo de configuração. `application.properties` está vazio. |
| `SERVER_PORT` | `9090` | Porta HTTP. |
| `BRAPI_API_KEY` | Sem padrão | Header `Authorization` da BRAPI. |
| `DB_URL` | `jdbc:mysql://mysql:3306/minha_base?...` | Conexão MySQL. |
| `DB_USERNAME` | `spring` | Usuário MySQL. |
| `DB_PASSWORD` | `spring123` | Senha MySQL. |
| `AWS_SQS_ENDPOINT_BASE` | `http://localstack:4566` | Endpoint override do cliente SQS. |
| `AWS_SQS_QUEUE_URL` | `http://localstack:4566/000000000000/tratar-ativos` | Fila de cotações. |
| `AWS_SQS_HISTORICAL_SERIES_QUEUE_URL` | `http://localstack:4566/000000000000/sqs-registrar-series-historicas` | Fila de séries históricas. |
| `AWS_ACCESS_KEY_ID` | `teste` | Declarada nas properties, mas ignorada pelo cliente SQS atual. |
| `AWS_SECRET_ACCESS_KEY` | `teste` | Declarada nas properties, mas ignorada pelo cliente SQS atual. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8080,http://localhost:5173,http://localhost:3000` | Origens liberadas em `ConfigCors` para todas as rotas (`/**`). Lista separada por vírgula. |

Exemplo:

```env
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=9090
BRAPI_API_KEY=<sua-chave-brapi>
DB_URL=jdbc:mysql://localhost:3306/minha_base?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=spring
DB_PASSWORD=<senha-local>
AWS_SQS_ENDPOINT_BASE=http://localhost:4566
AWS_SQS_QUEUE_URL=http://localhost:4566/000000000000/tratar-ativos
AWS_SQS_HISTORICAL_SERIES_QUEUE_URL=http://localhost:4566/000000000000/sqs-registrar-series-historicas
```

Não versione chaves ou senhas. O perfil `test` contém atualmente um valor padrão para `BRAPI_API_KEY`; remova-o e revogue a chave antes de usar o repositório fora de um ambiente controlado.

## Build e execução

Pré-requisitos para o funcionamento completo:

- JDK 21 e Maven Wrapper;
- MySQL acessível;
- LocalStack ou SQS com as duas filas criadas;
- chave válida da BRAPI.

No Windows:

```powershell
./mvnw.cmd clean package
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:BRAPI_API_KEY = "<sua-chave-brapi>"
java -jar target/gestor-ativos-brutos-0.0.1-SNAPSHOT.jar
```

Em Linux/macOS:

```bash
./mvnw clean package
SPRING_PROFILES_ACTIVE=dev BRAPI_API_KEY='<sua-chave-brapi>' \
  java -jar target/gestor-ativos-brutos-0.0.1-SNAPSHOT.jar
```

## Divergências corrigidas após confronto com a implementação

- incluída a rota `GET /ativos/robusto/{ativo}`;
- incluída a segunda fila e a variável `AWS_SQS_HISTORICAL_SERIES_QUEUE_URL`;
- usados os nomes reais dos controllers: `AtivoController`, `AnaliseAcaoController` e `HistoricoAcoesController`;
- corrigido o fluxo do agendador: cada registro é processado uma vez e a fila existe somente em memória;
- detalhados os contratos HTTP, SQS, MySQL e de erro;
- mapeadas as integrações efetivamente presentes: BRAPI, SQS/LocalStack, MySQL, Prometheus, Logstash e Docker Hub;
- diferenciadas dependências apenas declaradas de componentes realmente usados pelo código.

## Limitações conhecidas

- `GET /ativos/{ativo}` e a variante robusta publicam mensagens e têm efeitos colaterais em métodos GET.
- A contagem de venda reconhece apenas a recomendação literal `VENDA`.
- A serialização para SQS cria um `ObjectMapper` próprio, sem módulos explícitos para tipos de data/hora.
- O cliente BRAPI não configura timeouts e as retentativas SQS não têm backoff.
- O scheduler roda 24/7, sem restringir ao horário de pregão; ativos que falham na coleta (ex.: BRAPI fora do ar) são retentados a cada 5s, sem backoff.
- Não há autenticação ou autorização nas rotas da aplicação.
- CORS libera todos os métodos e headers para as origens configuradas em `CORS_ALLOWED_ORIGINS`; não usa `allowCredentials`, então o padrão serve para desenvolvimento local do front, mas a lista de origens deve ser revisada antes de qualquer deploy real.
- A suíte atual contém apenas um teste trivial, sem cobertura dos contratos ou integrações.
