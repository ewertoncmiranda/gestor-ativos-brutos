# MACRO-GES-4 — IMPLEMENTADO (2026-10-20)

## Entregue
- `GET /indices-macro/{codigo}?desde=AAAA-MM-DD`:
  - sem `desde`, a resposta é idêntica à de antes;
  - com `desde`, só os pontos a partir da data, do mais recente ao mais antigo;
  - data inválida responde 400.
- Consulta nova em `RepositorioIndiceMacro`: `findByCodigoSerieAndDataGreaterThanEqualOrderByDataDesc`.

## Evidência
- `IndiceMacroControllerTest`: 3 casos (chamada antiga, recorte e 400), todos passaram.

## Desvios
- Nenhum.
