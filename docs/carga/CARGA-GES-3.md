# CARGA-GES-3 — IMPLEMENTADO

A Saúde dos dados passa a considerar `vw_carga_ultima` (`RepositorioSaudeDados.ultimasCargas`):

- Mapeamento: COTAHIST→B3_COTAHIST, DFP→CVM_DFP, TTM→CVM_TTM, IPE→CVM_IPE, EVENTOS→EVENTOS_CORPORATIVOS.
- O último sucesso da carga substitui a data do histórico de arquivos quando for mais recente.
- Uma FALHA posterior ao último sucesso aparece como "Última carga falhou: <erro>", com o primeiro item de `erros_json` da etapa ou a `mensagem_erro` da execução. A idade continua sendo contada pelo último sucesso.
- As instruções de correção deixaram de citar flags de CLI do ETL e apontam para a rotina da manhã ou para "Rodar carga" no painel.

Sem a V44, o comportamento anterior é mantido.

Evidência: `ServicoSaudeDadosCargasTest` (7 casos).
