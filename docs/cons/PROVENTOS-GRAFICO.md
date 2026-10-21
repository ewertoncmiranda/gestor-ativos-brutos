# Proventos no gráfico — IMPLEMENTADO

## Entregue

`GET /ativos/{simbolo}/proventos-resumo?de=AAAA-MM-DD&ate=AAAA-MM-DD`, usando o start HTTP existente. Sem migração, escrita, chamada externa na tela ou comando operacional.

Eventos consolidados com contexto diário desde 2016, preços anterior/ex, ajuste em reais e percentual, valores oficiais por ação ligados pelo ISIN e pelo próximo pregão após a data-com, parcelas e suas datas de pagamento. A data-com oficial prevalece sobre o começo inferido de uma sequência de marcas; classes ON/PN não se misturam.

Agregados do período: totais oficiais identificados (brutos/parciais), JCP/dividendos, quantidade de datas ex monetárias, eventos com tipos associados a valores oficiais, variação de preço, ajuste percentual médio, meses com eventos, intervalo médio e última ocorrência. Eventos societários não monetários permanecem visíveis mas não entram na regularidade de proventos. Síntese holística contextualiza remuneração e movimentos ex, sem alterar automaticamente recomendação ou pontuação.

## Evidência

Testes sintéticos de serviço e MockMvc standalone: consolidação e borda da janela, parcelas, ON/PN, próximo pregão após feriado, combinações EJED/EJ+ED/EJD, precedência da data-com oficial, valores parciais, ajuste negativo, societário, identidade ausente, preços ausentes, validação HTTP e repasse da janela. Regressões existentes de DatasEx/Pregoes incluídas na execução. Nenhum banco acessado.

Execução JDK 26: `mvnw.cmd test -Dtest=ServicoProventosResumoTest,ProventosResumoControllerTest,ServicoDatasExTest,ServicoPregoesAgruparTest -DargLine=-Dnet.bytebuddy.experimental=true` concluída sem falhas. Foram 11 testes do novo serviço, 2 do controller e as regressões citadas; o ambiente utilizou o cache Maven existente do usuário.

## Desvios e limites explícitos

O cache existente não registra integralidade das coletas nem distingue uma coleta vazia de ausência/falha. Por isso `coberturaCompleta=false`, `retornoComProventosPercentual=null` e `contribuicaoProventosPontosPercentuais=null`: não se inventa retorno total a partir de uma cobertura não comprovada. Totais disponíveis são parciais, nunca recebimentos da carteira. A fonte fornece janela móvel de cerca de 12 meses por coleta; não há garantia de histórico anterior. Pagamento ausente permanece nulo.

Uma evolução que comprove cobertura deve registrar as coletas e conciliar calendário/quantidade societária antes de habilitar retorno total. Sem essa evidência, o contrato já entrega a limitação para os cards da interface.
