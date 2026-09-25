# Gestor de Ativos Brutos

Aplicacao backend em **Java 21** com **Spring Boot** para consulta, processamento e armazenamento de dados de ativos B3. A aplicacao integra BRAPI, SQS e MySQL para coletar dados brutos, disparar processamento assincrono e gerar analises quantitativas.

## Responsabilidades

- Consultar dados de ativos e series historicas OHLCV na BRAPI.
- Publicar payloads brutos de ativos em fila SQS.
- Consolidar analises historicas persistidas em MySQL.
- Derivar a decisao consolidada (sentimento, risco, recomendacao) por regras deterministicas sobre os indicadores persistidos.

## Principais tecnologias

- **Spring Boot / Spring Web** para API HTTP.
- **Spring Data JPA / Hibernate** para persistencia MySQL.
- **AWS SDK SQS** para mensageria.
- **Lombok** para reduzir boilerplate.
- **Micrometer / Prometheus** para metricas.

## Variaveis de ambiente

| Variavel | Padrao local | Descricao |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil ativo do Spring. |
| `SERVER_PORT` | `9090` | Porta HTTP da aplicacao. |
| `BRAPI_API_KEY` | - | Chave de acesso da BRAPI. |
| `DB_URL` | `jdbc:mysql://mysql:3306/minha_base?...` | URL JDBC do MySQL. |
| `DB_USERNAME` | `spring` | Usuario do MySQL. |
| `DB_PASSWORD` | `spring123` | Senha do MySQL. |
| `AWS_SQS_ENDPOINT_BASE` | `http://localstack:4566` | Endpoint SQS ou LocalStack. |
| `AWS_SQS_QUEUE_URL` | `http://localstack:4566/000000000000/tratar-ativos` | Fila SQS de processamento. |
| `AWS_ACCESS_KEY_ID` | `teste` | Access key AWS. |
| `AWS_SECRET_ACCESS_KEY` | `teste` | Secret key AWS. |

## Endpoints disponiveis

### Consultar ativo

`GET /ativos/{ativo}`

Consulta a cotacao atual do ativo na BRAPI, publica o payload bruto em SQS e devolve os dados recebidos.

Exemplo:

```bash
curl "http://localhost:9090/ativos/PETR4"
```

### Registrar ativo para processamento

`POST /ativos/registrar/{ativo}`

Registra o ativo na fila em memoria do agendador interno. O processamento periodico consulta a BRAPI, publica o payload em SQS, consolida analises existentes e salva a analise gerada em S3 quando houver dados historicos.

Exemplo:

```bash
curl -X POST "http://localhost:9090/ativos/registrar/VALE3"
```

### Gerar analise de ativo

`GET /analises/{simbolo}/analise`

Busca analises persistidas no MySQL, consolida os sinais e deriva por regras deterministicas uma resposta estruturada com sentimento, forca do sinal, risco, confianca, resumo, analise tecnica, analise fundamentalista, possivel cenario e recomendacao.

Exemplo:

```bash
curl "http://localhost:9090/analises/PETR4/analise"
```

### Historico OHLCV de acoes B3

`GET /api/v2/stocks/historical`

Proxy interno para `https://brapi.dev/api/v2/stocks/historical`. Use para buscar series historicas OHLCV de acoes, BDRs, ETFs, FIIs, units e indices B3.

Parametros:

| Parametro | Obrigatorio | Descricao |
| --- | --- | --- |
| `symbols` | sim | Um ou mais tickers separados por virgula. |
| `range` | nao | Janela historica aceita pela BRAPI. |
| `interval` | nao | Intervalo dos candles historicos. |
| `startDate` | nao | Data inicial para consulta por periodo explicito. |
| `endDate` | nao | Data final para consulta por periodo explicito. |
| `sortOrder` | nao | Ordenacao da serie, como `asc` ou `desc`. |

Exemplo:

```bash
curl "http://localhost:9090/api/v2/stocks/historical?symbols=PETR4,VALE3&range=1mo&interval=1d&sortOrder=asc"
```

## Estrutura principal

- `ControladorAtivo`: endpoints de consulta e registro de ativos.
- `ControladorAnaliseAcao`: endpoint de analise consolidada por regras deterministicas.
- `ControladorHistoricoAcoes`: endpoint de historico OHLCV.
- `ClienteBrApi`: cliente HTTP centralizado para consultas BRAPI.
- `ServicoAtivo`: orquestra BRAPI e publicacao na fila SQS.
- `AgendadorAtivos`: processa periodicamente ativos registrados.
- `MontadorDecisaoDeterministica`: deriva sentimento/risco/recomendacao a partir dos indicadores consolidados em MySQL.

## Execucao local

Exemplo de variaveis:

```env
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=9090
DB_URL=jdbc:mysql://mysql:3306/minha_base?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=spring
DB_PASSWORD=spring123
AWS_SQS_ENDPOINT_BASE=http://localstack:4566
AWS_SQS_QUEUE_URL=http://localstack:4566/000000000000/tratar-ativos
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
BRAPI_API_KEY=<sua-chave-brapi>
```

Build:

```bash
./mvnw clean package
```

Execucao:

```bash
java -jar target/gestor-ativos-brutos-0.0.1-SNAPSHOT.jar
```

## Observabilidade

Quando habilitado no perfil ativo, o Actuator expoe:

- `GET /actuator/health`
- `GET /actuator/metrics`
- `GET /actuator/prometheus`
