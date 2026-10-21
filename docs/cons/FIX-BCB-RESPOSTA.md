# FIX-BCB-RESPOSTA — IMPLEMENTADO

## Entregue

ClienteBancoCentral valida status, corpo e formato de lista antes de desserializar. HTML (mesmo HTTP200 ou Content-Type incorreto) não é entregue ao Jackson. Accept JSON explícito e limites de conexão (8s) e leitura (20s). Uma única repetição após300ms para formato inválido, resposta vazia, falha de transporte e HTTP5xx. HTTP4xx, inclusive429, não repete imediatamente. Interrupção respeitada. Falha persistente gera exceção descritiva com série e motivo; nunca retorna lista vazia fingindo sucesso nem registra corpo HTML.

RepositorioPanoramaMercado.volumeAnormal corrigido para volume_financeiro_medio_63d, coluna definida na V23 e escrita pelo ETL. Mantidos corte de R$1milhão, presença80% e datas da consulta. Sem alteração de schema.

## Evidência

16 testes passaram com mocks (9 cliente,1 repositório,6 regressões do serviço de atualização macro). Casos: JSON válido, HTML transitório/persistente200,502 recuperável,429 sem retry, vazio, truncado, transporte e URL histórica. Sem acesso a banco ou fonte externa nos testes.

## Desvios e limites

Nenhuma alteração em ServicoAtualizacaoIndicesMacro, RepositorioIndiceMacro, controller macro ou entrypoint. A correção não torna a fonte externa sempre disponível; falha final permanece explícita para os mecanismos já existentes de carga/agendamento. O log recebido prova HTML no caminho HTTP, mas não identifica se foi emitido pelo BCB, proxy ou gateway. Publicação apenas na feature-fix-bcb-resposta; exige merge e nova imagem do gestor para chegar à stack.
