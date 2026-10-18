# CARGA-GES-1 — IMPLEMENTADO

Leitura do controle de cargas (V44) em camelCase para o painel:

- `GET /cargas?limite=&status=&desde=`: lista as execuções mais recentes. O limite padrão é 20 e o máximo 200; `desde` é ISO date-time.
- `GET /cargas/ultimas`: devolve `vw_carga_ultima`, com a última tentativa e o último sucesso de cada etapa.
- `GET /cargas/{id}`: devolve a execução com as etapas (métricas, alertas e erros) e os arquivos de `etl_execucao`.

Códigos de resposta: 503 quando a base ainda não tem a V44 (`RepositorioCargas.schemaDisponivel`); 400 para limite ou status inválido; 404 para um id inexistente.

Evidência: `ServicoCargasTest` (leitura) e `RepositorioCargasTest`. As consultas SQL foram executadas contra a base local v44 e todas rodaram sem erro.
