# FONTES-GES-2 — IMPLEMENTADO (2026-10-20)

`GET /ativos/{simbolo}/banco`: indicadores de banco (DEC-FONTES-1: bancos são avaliados por P/VP × ROE e Basileia, não por Graham).

- Ticker→CNPJ por `cvm_ticker` (mesma ligação do gestor); setor em `cvm_cadastro.setor_cvm`. `ehBanco` = setor contém "banco" (sem acento/caixa): na CVM, "Bancos" e "Emp. Adm. Part. - Bancos".
- Série dos últimos 8 trimestres (cronológica) de `banco_indicador_trimestral`; `ultimoTrimestre`.
- `pvp` reaproveita `ServicoFundamentosCvm` (preço / VPA); sem ele, `pvp` null com `pvpMotivo`. `roeSobrePvp` = ROE / P/VP.
- Selos: BASILEIA, INADIMPLENCIA, ROE, com `leitura` por selo e geral.
- Não banco: `ehBanco=false`, demais campos null, 200. Banco sem IF.data: `motivo`, campos null. V45 ausente: 503.
- Arquivos novos: `BancoController`, `ServicoBanco`, `RepositorioBancoIndicadores`, `BancoDTO`, `ServicoBancoTest`.

## Desvios (revisar)

- `BASILEIA_MINIMA` = 10,5% (mínimo incluindo adicional de conservação; único valor regulatório).
- `INADIMPLENCIA_ALTA_PP` = 0,5 p.p. em 4 trimestres: escolha nossa, não regulatória. Compara o último com o 5º registro mais recente (4 trimestres antes, assumindo série sem buracos).
- Custo de capital do selo ROE = Selic meta (`indice_macro` SELIC, % a.a.); ROE >= Selic = ACIMA. Sem Selic: SEM_DADO.
- Valores do IF.data tratados como percentuais (ex.: 15,2 = 15,2%).
- O P/VP herda a limitação de `ServicoFundamentosCvm` (VPA do período anual).

## Exemplo

```json
{
  "simbolo": "ITUB4", "ehBanco": true, "setorCvm": "Emp. Adm. Part. - Bancos", "motivo": null,
  "ultimoTrimestre": {"dataBase": "2026-06-30", "basileia": 15.2, "capitalPrincipal": 13.1, "inadimplencia": 3.4, "roe": 21.5},
  "serie": [{"dataBase": "2025-09-30", "basileia": 14.9, "capitalPrincipal": 12.8, "inadimplencia": 2.8, "roe": 20.0}],
  "pvp": 2.00, "pvpMotivo": null, "roeSobrePvp": 10.7500,
  "selos": [
    {"tipo": "BASILEIA", "status": "OK", "valor": 15.2, "referencia": 10.5, "leitura": "Basileia de 15.2% acima do mínimo regulatório de 10.5%."},
    {"tipo": "INADIMPLENCIA", "status": "ALERTA", "valor": 0.6, "referencia": 0.5, "leitura": "Inadimplência subiu 0.6 p.p. em 4 trimestres (limite 0.5 p.p.)."},
    {"tipo": "ROE", "status": "ACIMA", "valor": 21.5, "referencia": 14.75, "leitura": "ROE de 21.5% acima do custo de capital de referência (Selic meta 14.75%)."}
  ],
  "leitura": "1 selo exige atenção. ROE/P/VP de 10.7500 (maior indica mais retorno por unidade de preço pago sobre o patrimônio)."
}
```

## Verificação

Testes unitários (5, Mockito); suíte completa: 239 testes, 1 falha antiga (`ServicoSimuladorOperacaoTest.historico_operacao_calcula_pnl_diario`). Consultas SQL validadas numa base descartável (removida).
