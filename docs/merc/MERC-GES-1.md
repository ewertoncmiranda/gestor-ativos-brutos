# MERC-GES-1 — IMPLEMENTADO

`GET /mercado/panorama?janela=1d|5d|21d|ano` (padrão `5d`), contrato do hub (Plano MERC), camelCase e frações. Blocos implementados: `setores`, `altas`, `quedas`, `liquidez`, `negociacao` e `amplitude`. `volumeAnormal`, `macro` e `eventos` ficam para a MERC-GES-2: vêm vazios (`[]`, ou `macro` com campos nulos) e sempre aparecem em `indisponivel`.

- `MercadoController` (`/mercado`, `GET /panorama`) → `ServicoPanoramaMercado` → `RepositorioPanoramaMercado` (JdbcTemplate, só SELECT) → `PanoramaMercadoDTO` (records).
- O SQL só agrega por pregão: fechamentos dos extremos ajustados por `fator_cotacao`, soma de volume e de negócios no período, média de 21 pregões e evento corporativo no período. Os universos, a variação, a mediana, a coesão, o desvio, a ordenação com desempate e o corte em 10 ficam no serviço.
- Cache em memória por `(janela, dataPregao)`. Quando muda o pregão, as entradas antigas saem. Resposta com bloco indisponível (fora os da MERC-GES-2) não vai para o cache, então a liquidez calculada mais tarde no mesmo pregão aparece na chamada seguinte.
- `janela` fora do catálogo → 400 `JANELA_INVALIDA`. Sem cotação → 200 com os 9 blocos em `indisponivel`. Sem liquidez no último pregão → 200 com as datas e os 6 blocos da MERC-GES-1 indisponíveis.

## Interpretações (o contrato não fixa estes pontos)

1. **Período:** são os N últimos pregões, de `dataInicial` até `dataPregao`. A variação compara o fechamento de `dataPregao` com o do pregão imediatamente anterior a `dataInicial`. Assim, `5d` em 19/10 tem `dataInicial` = 13/10, como no exemplo do hub, e base em 09/10. `1d` é a variação do último pregão, e `ano` vai do último pregão do ano anterior até hoje. Volume e negócios são somados nos N pregões.
2. **Ativos:** são os de `ativo_liquidez_diaria` no último pregão. Nome e setor seguem a regra de `/base/ativos` (`cvm_ticker` ativo → `cvm_empresa.denominacao`/`setor`, e o grupo vem de `setor_grupo`). Um ativo sem cadastro CVM (por exemplo, units como BPAC11) entra em altas, quedas, liquidez e negociação com `nome`/`setor` nulos, mas fica fora do card de setores. O índice (`especificacao` IBO*, IBOV11) fica fora porque não é um ativo negociável.
3. **`empresas` do setor:** é o número de tickers do setor no universo (PETR3 e PETR4 contam 2). A mediana, a regra "< 3 → Outros" e os destaques usam a mesma unidade. "Outros" junta os tickers dos setores pequenos, tem `grupo` nulo e fica sempre por último.
4. **Melhores/piores:** um setor com 20 tickers ou mais mostra os 10 de maior e os 10 de menor desvio. Abaixo de 20, cada ticker aparece uma única vez: a metade de cima (arredondada para cima) em `melhores` e o resto em `piores`.
5. **Coesão:** é a fração de tickers com a mesma direção da mediana (subiu, caiu ou ficou estável; estável = variação igual a zero). Selo `JUNTO` ≥ 0,70, `MISTO` ≥ 0,50 e `DISPERSO` abaixo disso. Os setores são ordenados pela mediana (desc) e, no empate, pelo nome.
6. **Altas/quedas:** só entram variações > 0 e < 0, respectivamente. Desempate em todos os rankings: maior volume financeiro no período, depois o símbolo.
7. **Evento corporativo:** o gestor não ajusta a série por evento. Por isso, todo evento com `data_efeito` em (base, `dataPregao`], buscado pelo símbolo ou pelo CNPJ do ticker, tira o ativo de setores, altas, quedas e amplitude. O ativo continua contando em `universo.liquidos`, liquidez e negociação.
8. **Amplitude:** conta subiram/caíram/estáveis sobre os líquidos sem evento no período. `acimaMedia21` é a fração desses ativos cujo fechamento está acima da média de fechamento (ajustado) dos últimos 21 pregões. Quem teve evento dentro desses 21 pregões fica fora dessa fração.
9. **Bloco vazio:** todo bloco que sai vazio entra em `indisponivel`, inclusive `altas` num dia em que nada subiu. `amplitude` e `macro` sem dado vêm como objeto com campos nulos.
10. `presenca_63d` é uma fração de 0 a 1 (corte em 0,80). `fator_cotacao` é o lote da cotação: preço por ação = `fechamento / fator_cotacao`.

## Evidência

- `ServicoPanoramaMercadoTest` (18 testes) cobre mediana com n par e ímpar, coesão e selos (incluindo os limites 0,70 e 0,50), desvio, "Outros" por último, setor com 25 tickers (10 + 10), universos, exclusão por evento, desempate, limite de 10 com `posicao`, bloco vazio → `indisponivel`, sem cotação, sem liquidez (fora do cache), janela inválida, datas de cada janela, histórico curto e cache.
- `MercadoControllerTest` (4 testes, MockMvc standalone) cobre o formato JSON do contrato, o padrão `5d`, o 400 para `3m` e o 200 sem cotação.
- O SQL foi validado contra o MySQL local (pregão de 19/10/2026, `5d`). A consulta devolveu 380 ativos (o IBOV11 ficou fora): 294 com presença ≥ 80% e 192 líquidos, nenhum com evento no período. No servidor ela levou cerca de 0,5 s.
  - PETR4 conferida à mão: 58,00 / 56,00 − 1 = 0,035714 (fechamentos de 19/10 e 09/10). O volume de 13/10 a 19/10 é R$ 12.178.357.066, e são 345.227 negócios.
  - Em `ano`, a base é 30/12/2025 e o início 02/01/2026.
  - O EXISTS de evento marca COGN3 (bonificação em 26/12/2025) num período de 19/12 a 30/12/2025.
- Suíte inteira: 256 testes e 1 falha, a já conhecida e anterior `ServicoSimuladorOperacaoTest.historico_operacao_calcula_pnl_diario`.
