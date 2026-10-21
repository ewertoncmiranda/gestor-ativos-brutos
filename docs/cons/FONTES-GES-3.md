# FONTES-GES-3 — IMPLEMENTADO (2026-10-20)

Sinal de insiders (VLMO) e recompras. Novos: `InsidersController`, `ServicoInsiders`, `RepositorioInsiders`, `InsidersDTO`, `RecomprasDTO`, `ServicoInsidersTest`.

## Resumo
- `GET /ativos/{simbolo}/insiders`: lê `cvm_negociacao_administrador` (V45) pelo CNPJ de `cvm_ticker` (símbolo canônico via `ativo_identidade`). Vale a última `versao` por (cnpj, data_referencia). Só `Compra à vista`, `Venda à vista`, `Compra à termo` com `Ações`/`Units`; comparação sem acentos, minúscula, e repara texto UTF-8 lido como latin-1. Janelas de 3, 6 e 12 meses por `data_movimentacao`, total e por cargo (Diretor, Conselho de Administração, Controlador, Conselho Fiscal, Órgão Estatutário). `dataEntregaMaisRecente` é a data point-in-time. Sem V45: 503.
- `GET /ativos/{simbolo}/recompras`: soma as contas de tesouraria do BPP em `fato_contabil` (2.03.02.05, 2.03.04.09, 2.07.01.02.05, 2.07.01.04.09, 2.08.02.05, 2.08.04.09; DFP/ITR; consolidado prevalece). Saldo é negativo; `recompraLiquida` = -(variação do saldo). Sem linhas: 200 com `disponivel=false` e motivo "contas de tesouraria não persistidas; depende de FONTES-ETL-8".

## Desvios
- Sinal: `ServicoInsiders.LIMIAR_SINAL` = 0,10 sobre a janela de 6 meses (`JANELA_SINAL_MESES`): compra líquida / (compras + vendas), em quantidade. >= 0,10 COMPRA_LIQUIDA; <= -0,10 VENDA_LIQUIDA; senão (ou sem movimento) NEUTRO.
- `compraLiquidaVolume` soma só linhas com volume > 0; null se nenhuma tem. Linhas sem cargo entram no total, não em `porCargo`.
- Recompras: ITR e DFP têm saldo (não acumulado), então a variação entre balanços consecutivos é o período; a primeira data da série não tem variação. `recompraLiquida12m` soma as datas-base dos últimos 12 meses. Não distingue cancelamento/entrega a plano de recompra (variação positiva aparece como recompra negativa).
- `fato_contabil` pode não conter as contas de tesouraria: nesse caso `disponivel=false`.

## Exemplo
```json
{"simbolo":"WEGE3","cnpj":"84429695000111","disponivel":true,"motivo":null,"sinal":"COMPRA_LIQUIDA",
 "leitura":"Nos últimos 6 meses, administradores e controladores compraram mais do que venderam (líquido de 1300 ações).",
 "dataUltimaMovimentacao":"2026-08-20","dataEntregaMaisRecente":"2026-10-01",
 "janelas":[{"meses":3,"total":{"quantidadeCompras":1000,"quantidadeVendas":200,"compraLiquidaQuantidade":800,
   "compraLiquidaVolume":8000,"movimentacoes":2},"porCargo":{"Diretor":{"compraLiquidaQuantidade":800},"Controlador":{}}}]}
```
```json
{"simbolo":"WEGE3","disponivel":false,"motivo":"contas de tesouraria não persistidas; depende de FONTES-ETL-8","recompraLiquida12m":null,"serie":[]}
```

## Verificação
Testes Mockito `ServicoInsidersTest` (8). SQL validado em base descartável `v45_ges3` (apagada).
