# CARGA-GES-2 — IMPLEMENTADO

`POST /cargas` valida o pedido do painel e o repassa ao ETL (`POST {etl.url}/cargas`, cabeçalho `X-ETL-Token` quando `ETL_TOKEN` está definido). O corpo é convertido para snake_case e recebe `solicitada_por: "PAINEL"`.

Validação:
- `tipo` deve ser ROTINA, COMPLETA ou PARCIAL.
- `anos` vão de 2016 ao ano corrente.
- `simbolos` seguem o padrão de ticker, com no máximo 50.
- `etapas` vêm do catálogo do plano.
- PARCIAL exige etapas, anos ou símbolos.
- Listas são normalizadas e deduplicadas.

Respostas: 202 e 409 (`execucaoEmAndamento`) do ETL voltam com o mesmo status, com chaves em camelCase. Um ETL fora do ar (conexão recusada ou timeout de 3s/10s) vira 503.

Configuração: `etl.url=${ETL_URL:http://etl-fundamentos-cvm:8080}` e `etl.token=${ETL_TOKEN:}`; o perfil de teste usa `http://localhost:8097`.

Evidência: `ServicoCargasTest` (disparo) e `ClienteEtlTest` (MockRestServiceServer).
