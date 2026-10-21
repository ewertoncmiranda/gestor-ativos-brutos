# FONTES-GES-1 — IMPLEMENTADO (2026-10-20)

Prêmio de risco do ativo contra a NTN-B de referência (DEC-FONTES-3).

## Entregue

- `GET /indices-macro/curva-real` (`CurvaRealController`, rota literal; tem precedência sobre `/indices-macro/{codigo}`): NTN-B ("Tesouro IPCA+" e "Tesouro IPCA+ com Juros Semestrais") da data-base mais recente de `titulo_publico_taxa` (V45), lista por vencimento, `referencia` e `dataBase`.
- Referência: alvo = data-base + 5 anos; vence o vencimento mais próximo do alvo, empate pelo vencimento menor (mesma regra do ETL). Pontos sem `taxa_compra` não concorrem.
- `GET /ativos/{simbolo}/premio-risco` (`PremioRiscoController`): `earningsYield` (último insight, `MAX(id)`, `$.valuation.earnings_yield_percent`), `juroReal` (taxa de compra da referência), `premioRisco = earningsYield − juroReal` (p.p.), `veredito` (ACIMA_DA_RENDA_FIXA / ABAIXO_DA_RENDA_FIXA / IGUAL_A_RENDA_FIXA), `leitura` determinística em pt-BR, `selicMeta` (último ponto SELIC de `indice_macro`) e `premioSobreSelic = earningsYield − selicMeta`.
- Dado ausente (sem insight, tabela sem linhas): campos nulos com `motivo`. Sem a V45: HTTP 503 (`ResponseStatusException`, padrão do `ServicoCargas`), checagem por `information_schema` em `RepositorioTitulosPublicos.schemaDisponivel()`.
- Arquivos novos: `PremioRiscoController`, `CurvaRealController`, `ServicoPremioRisco`, `RepositorioTitulosPublicos`, `PremioRiscoDTO`, `CurvaRealDTO`, `ServicoPremioRiscoTest`. Nenhum arquivo existente alterado.

## Evidência

- `ServicoPremioRiscoTest`: 7 testes (referência de 5 anos, desempate, prêmio positivo e negativo, sem insight, tabela vazia, sem V45 = 503), todos verdes.
- Suíte completa: 241 testes, 1 falha antiga (`ServicoSimuladorOperacaoTest.historico_operacao_calcula_pnl_diario`), ignorada por instrução.
- SQL validado em base descartável `v45_ges1` (V1..V45 no container `mysql`, apagada ao final): existência da tabela = 1; `MAX(data_base)` = 2026-10-16 (linha de Tesouro Prefixado ignorada); lista por vencimento ordenada; Selic 14,5 (último ponto); earnings yield do `MAX(id)` = 12,5 com dois insights; símbolo sem insight = nenhuma linha.

## Desvios

- Sem teste de controller/integração com MVC e sem execução do repositório via Spring (apenas as consultas rodadas direto no MySQL); a precedência da rota literal decorre da regra do Spring, não foi exercitada em teste.
- `veredito` ganhou o valor IGUAL_A_RENDA_FIXA para prêmio exatamente zero (contrato só definia > 0 e < 0).
- `premioRisco` e `premioSobreSelic` arredondados a 2 casas; `leitura` usa 2 casas para taxas e 1 para o prêmio.
- Primeira rodada das migrations no `v45_ges1` colidiu com um comando concorrente (erros "already exists"); a base foi usada só após conferir a tabela da V45.
