# MACRO-GES-1 — IMPLEMENTADO (2026-10-20)

## Entregue
- Duas séries novas no mapa `SERIES` de `ServicoAtualizacaoIndicesMacro`:
  - `IPCA_12M` (SGS 13522);
  - `EURO` (SGS 21619, PTAX de venda).
- Elas entram no ciclo diário da manhã e no `completarHistorico` da subida (`CargaHistoricoIndicesMacro`, desde `indices.macro.historico.inicio`).

## Evidência
- Os dois códigos foram conferidos ao vivo na API do BCB em 2026-10-20:
  - 13522 devolveu 4,58 em 01/09/2026;
  - 21619 devolveu 5,6317 em 20/10/2026.
- Suíte do gestor: 242 testes. A única falha é a de `ServicoSimuladorOperacaoTest.historico_operacao_calcula_pnl_diario`, que já falha na `develop`.

## Desvios
- O IGP-M acumulado em 12 meses **não** entrou: o código 28655 não é essa série. O acumulado será composto a partir da série mensal 189 (MACRO-GES-3 e MACRO-PAI-2).
- `CargaHistoricoIndicesMacro` passou a completar também o histórico de `IPCA_12M` e `EURO`.
