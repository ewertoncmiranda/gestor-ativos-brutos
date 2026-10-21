# MACRO-GES-3 — IMPLEMENTADO (2026-10-20)

## Entregue
- `GET /indices-macro/painel` em `IndiceMacroController` (rota literal, precede `/{codigo}`); servico injetado por campo para manter o construtor existente.
- `service/ServicoPainelMacro`: le so o cache `indice_macro` (via `RepositorioIndiceMacro.findByCodigoSerieOrderByDataDesc`) e o ultimo insight por simbolo.
  - Series SELIC, CDI, IPCA, IGPM, IPCA_12M, DOLAR, EURO, IBCBR, DESEMPREGO: valor, data, variacao 1/3/12 meses contra o ultimo ponto em ou antes da data-alvo (p.p.; DOLAR/EURO/IBCBR em %); IGPM traz `acumulado12m` composto, null + motivo se faltar mes.
  - Juro real = (1+Selic/100)/(1+IPCA esperado/100)-1; FOCUS_IPCA do ano seguinte apos junho, senao do ano corrente; fallback IPCA_12M; `fonteIpca` informa a serie usada.
  - Ciclo da Selic: ALTA/QUEDA/ESTAVEL contra o ultimo ponto em ou antes de 90 dias (tolerancia 0,01).
  - Barreira: earnings yield minimo = Selic meta; mediana da base e ate 20 ativos acima, decrescente.
  - Cada bloco com `leitura` deterministica; ausencia/falha vira null + `motivo` (sem 500).
- `external/dto/PainelMacroDTO` (records, camelCase).

## Evidencia
- `ServicoPainelMacroTest`: 9 testes (juro real e fontes, fallback IPCA_12M, ciclo, variacoes p.p./%, IGP-M composto e com mes faltando, series ausentes, barreira, falha de leitura do EY). `IndiceMacroControllerTest`: 3 testes. Todos verdes.
- Suite completa: 251 testes, 1 falha — `ServicoSimuladorOperacaoTest.historico_operacao_calcula_pnl_diario`, anterior a esta tarefa.

## Desvios
- Fonte do earnings yield: `insight_acao.detalhes_json -> $.valuation.earnings_yield_percent` (gravado pelo gerar-insights como LPA/preco*100) do ultimo insight (MAX(id)) por simbolo, mesmo criterio de `RepositorioListagemAtivos`. Nao ha repositorio que exponha EY para a base inteira; a consulta JDBC fica no proprio servico (lista de arquivos permitidos nao incluia repositorio novo). Recalcular com `indicador_fundamentalista.lpa` + cotacao exigiria N consultas por simbolo.
- Universo = simbolos com insight (nao toda cvm_ticker). Simbolos sem EY numerico sao ignorados.
- Variacao em "meses" usa a data do ultimo ponto da serie (nao a data de hoje) como base.
