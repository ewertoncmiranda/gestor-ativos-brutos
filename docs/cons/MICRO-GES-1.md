# MICRO-GES-1

Status: **IMPLEMENTADO** (2026-10-20).

## Entregue

- Branch `feature-cons-micro-ges-1`, worktree `.worktrees/micro-ges-1`, criada de `origin/develop` (`72a4786`).
- `PregoesDTO.Vela` e `RepositorioPregoes.Pregao` incluem `precoMedio`, `melhorOfertaCompra`, `melhorOfertaVenda`, `marcaEx`; a leitura oficial seleciona as colunas existentes. BRAPI mantém campos novos nulos.
- `ServicoPregoes` repassa campos às velas. Semana/mês: PREMED ponderado por quantidade quando todos os dias têm dados, ofertas do último pregão e marcas distintas em ordem cronológica separadas por vírgula. Não publica média parcial.
- `OpcaoB3DTO`/`RepositorioOpcoes` expõem ofertas e `indiceCorrecao`; `precoJusto` é o meio do spread e `spreadPercentual` é 100 × (venda − compra) / meio, com oito casas. Oferta ausente/não positiva ou cruzada produz derivados nulos.
- Campos JSON existentes e construtores antigos preservados; novos campos opcionais e derivados serializados em camelCase.

## Evidência

- `mvnw test -DargLine=-Dnet.bytebuddy.experimental=true`, JDK 26: **242 executados, 241 passaram, 1 falha preexistente**.
- **15 testes relacionados passaram**: `OpcaoB3DTOMicroTest` (3), `MicroMercadoRepositoryTest` (2), `ServicoPregoesAgruparTest` (5), `OpcaoControllerTest` (5). ResultSet/HTTP/repositórios sintéticos; nenhum banco iniciado.
- A única falha é `ServicoSimuladorOperacaoTest.historico_operacao_calcula_pnl_diario`, linha 110: comparação sensível à escala entre `0` e `0.000000`.
- Reproduzida a mesma falha em cópia intacta de **origin/develop 72a4786** extraída em `target/cons-base`: teste do simulador executou 6 casos, com a mesma falha. Arquivos fora do escopo não foram corrigidos.

## Desvios

- **Ampliação de escopo autorizada explicitamente pelo usuário nesta conversa:** `ServicoPregoes.java`, necessário porque `agrupar()` reconstruía `Vela` apenas com campos antigos e descartaria os novos dados.
- Nenhuma alteração em `IndiceMacroController`, `RepositorioIndiceMacro`, `ServicoAtualizacaoIndicesMacro`, migrations ou SPEC. Nenhuma consulta a banco durante implementação/validação.
- Suíte global não está verde por falha preexistente do simulador; a evidência acima distingue essa limitação dos testes da entrega.
