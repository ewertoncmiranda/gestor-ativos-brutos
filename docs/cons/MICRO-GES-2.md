# MICRO-GES-2 — IMPLEMENTADO (2026-10-20)

Calendário de datas ex: `GET /ativos/{simbolo}/datas-ex?desde=AAAA-MM-DD` (padrão: 24 meses; símbolo normalizado em maiúsculas; ativo sem eventos devolve lista vazia e 200; `desde` inválido devolve 400).

- Fonte primária: `data_ex_b3` (V45). Sem linhas para o símbolo, ou tabela ausente: cálculo ao vivo sobre `cotacao_b3_diaria` (`origemDados` = `DATA_EX_B3` ou `COTAHIST_AO_VIVO`).
- Achado nos dados reais: o COTAHIST repete `marca_ex` em cerca de 8 pregões seguidos. O fallback considera o dia ex o primeiro da janela (mesma marca, no máximo 5 dias corridos desde a linha anterior). O ETL (FONTES-ETL-8) deve seguir a mesma regra ao gravar `data_ex_b3`.
- Ajuste implícito = fechamento do pregão anterior − abertura do dia; percentual sobre o fechamento anterior.
- Valor declarado: `data_ex_b3.valor_declarado` quando houver; senão proventos do gestor (`provento_distribuido`, por emissor) cuja data com + 1 dia fica a até 3 dias da data ex, com tipo compatível (JCP/DIVIDENDO/RENDIMENTO); marca combinada soma os proventos da janela. `razaoAjuste` = ajuste / valorDeclarado; `yieldDoEvento` = valorDeclarado / fechamentoAnterior.
- Mapa de marcas (hipótese, DEC-MICRO-1): EB BONIFICACAO, ED DIVIDENDO, EG GRUPAMENTO, EJ JCP, ER RENDIMENTO, ES SUBSCRICAO, EX DESDOBRAMENTO; combinação usa a primeira letra reconhecida; desconhecida = OUTRO.
- Resumo: quantidade e soma de yield em 12 meses; `proximaDataEstimada` por sazonalidade (ocorrência mais recente do tipo com 12+ meses, projetada para o futuro; null sem esse histórico). Confiança MEDIA se o tipo se repete em outro ano (até 20 dias do mesmo dia do ano), senão BAIXA.

Arquivos: `DatasExController`, `ServicoDatasEx`, `RepositorioDatasEx`, `DatasExDTO`, `ServicoDatasExTest` (9 testes).

## Exemplo real do fallback (PETR4, `minha_base`, trecho)

Valor declarado ausente neste exemplo (SELECT direto, sem a junção com proventos).

```json
{
  "simbolo": "PETR4",
  "origemDados": "COTAHIST_AO_VIVO",
  "desde": "2024-10-21",
  "eventos": [
    {"dataEx": "2026-04-23", "marca": "ERJ", "tipo": "RENDIMENTO", "fechamentoAnterior": 47.6700, "aberturaEx": 47.3600,
     "ajusteImplicito": 0.3100, "ajustePercentual": 0.6503, "valorDeclarado": null, "origemValor": null, "razaoAjuste": null, "yieldDoEvento": null},
    {"dataEx": "2026-06-02", "marca": "EJ", "tipo": "JCP", "fechamentoAnterior": 42.3700, "aberturaEx": 41.9000,
     "ajusteImplicito": 0.4700, "ajustePercentual": 1.1093, "valorDeclarado": null, "origemValor": null, "razaoAjuste": null, "yieldDoEvento": null},
    {"dataEx": "2026-08-24", "marca": "EDJ", "tipo": "DIVIDENDO", "fechamentoAnterior": 44.3000, "aberturaEx": 42.6000,
     "ajusteImplicito": 1.7000, "ajustePercentual": 3.8375, "valorDeclarado": null, "origemValor": null, "razaoAjuste": null, "yieldDoEvento": null}
  ],
  "resumo": {"quantidade12Meses": 3, "somaYield12Meses": 0, "proximaDataEstimada": "2026-12-23", "proximoTipo": "DIVIDENDO",
             "confianca": "BAIXA", "leitura": "Estimativa por sazonalidade (apenas indicativo)."}
}
```

(`proximaDataEstimada`, `confianca` e `leitura` do exemplo são ilustrativos; os eventos e o ajuste vêm da consulta real.)

## Verificação

- Testes unitários: 9 novos passam; suíte completa 243 testes, 1 falha antiga (`ServicoSimuladorOperacaoTest.historico_operacao_calcula_pnl_diario`).
- SQL em base descartável `v45_ges5` (apagada): consultas dos dois caminhos retornam as linhas esperadas; fallback também conferido em leitura sobre `minha_base`.
