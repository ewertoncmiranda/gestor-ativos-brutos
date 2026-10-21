# MACRO-GES-2 — IMPLEMENTADO (2026-10-20)

## Entregue
- `ClienteFocusBcb`: busca as expectativas de mercado na API OData aberta `ExpectativasMercadoAnuais`.
  - Usa `baseCalculo = 0`, que considera todos os respondentes.
  - Filtra por indicador e pelo ano projetado.
- `ServicoAtualizacaoFocus`: grava as medianas de Selic e IPCA do ano corrente e do seguinte em `indice_macro`.
  - Os códigos são `FOCUS_SELIC_{ano}` e `FOCUS_IPCA_{ano}`, com a data da pesquisa.
  - É idempotente por (código, data).
  - A falha de um indicador não interrompe o outro.
- Agendamento: `AgendadorCacheAtivos.atualizarFocus`, no mesmo horário da manhã dos outros índices.
- `ExpectativaFocusDTO`.

## Evidência
- A API foi conferida ao vivo em 2026-10-20 (pesquisa de 16/10):
  - Selic esperada: 13,25% para 2026 e 12,00% para 2027;
  - IPCA esperado: 5,219% para 2026 e 4,3206% para 2027.
- Testes `ServicoAtualizacaoFocusTest` (3 casos) e `ClienteFocusBcbTest` (2 casos): todos passaram.

## Desvios
- Nenhum. Não precisou de migration: os códigos cabem nos 20 caracteres de `codigo_serie`.
