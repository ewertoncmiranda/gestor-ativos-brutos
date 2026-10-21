# PANORAMA-POS-CARGA — IMPLEMENTADO

## Entregue

Panorama seleciona a última data comum aos preços e à liquidez do mesmo ativo. O ETL grava as métricas de cada pregão em uma transação. A data usada continua explícita no contrato e no rodapé já existente. Verificação da data comum antecede o cache: assim que a liquidez avança, a próxima consulta usa a nova data.

Cache válido por30segundos, inclusive para revisões no mesmo pregão. Resposta sem universo de ações não é armazenada mesmo que macro/eventos estejam disponíveis. Sem liquidez histórica, resposta permanece parcial e é recalculada na próxima consulta.

Agregados SQL nulos em CDI/IPCA e valores macro ausentes não provocam NullPointerException. Ausência continua null; zero continua zero.

## Evidência

Testes sintéticos de repositório e serviço: transição pós-carga, expiração por relógio injetado sem espera real, resposta vazia com macro, ausência de histórico de liquidez, agregados nulos, listas vazias, zero legítimo e regressões existentes. Nenhum acesso ao banco ou alteração nos containers.

## Desvios e limites

Sem alteração de ETL, schema, CLI ou frontend. Não reinicia a carga nem os serviços. Após merge, a correção precisa da nova imagem do gestor; a próxima consulta à API recupera os dados automaticamente. A tela aberta pode precisar ser atualizada para emitir nova consulta. A escolha da data comum garante presença das métricas, não certifica a qualidade integral da carga; os filtros de elegibilidade existentes continuam aplicados.
