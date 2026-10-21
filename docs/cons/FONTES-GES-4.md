# FONTES-GES-4 — IMPLEMENTADO (2026-10-20)

`GET /ativos/{simbolo}/confiabilidade` junta as fontes da V45 (e o payout já existente) em selos
`{id, nivel, titulo, detalhe}` com `nivel` em `OK | ALERTA | CRITICO | SEM_DADO`, mais um `resumo`.
Ticker → CNPJ via `cvm_ticker`. Símbolo desconhecido = 404; nenhuma tabela da V45 = 503
(`ResponseStatusException`, como `ServicoCargas`); tabela individual ausente ou vazia = aquele selo `SEM_DADO` com motivo.

Arquivos (todos novos): `ConfiabilidadeController`, `ServicoConfiabilidade`, `RepositorioConfiabilidade`,
`ConfiabilidadeDTO`, `ServicoConfiabilidadeTest` (15 testes Mockito).

| Selo | Fonte | Regra |
|---|---|---|
| CADASTRO | `cvm_cadastro` | ATIVO = OK; SUSPENSO(A)... e CANCELADA = CRITICO; outra situação = ALERTA; detalhe com data da situação, cancelamento e motivo |
| AUDITOR | `cvm_auditor` | troca (auditor atual com CNPJ diferente do anterior) iniciada nos últimos 24 meses = ALERTA (atual, anterior, data, justificativa); senão OK |
| DIVIDA | `cvm_divida_valor_mobiliario` | referência/versão mais recente; saldo devedor vencendo em 12 meses / saldo total; ALERTA se > 50% |
| FUNDOS | `fundo_posicao_acao` | fundos na competência mais recente, entraram/saíram e variação de fundos e quantidade contra a anterior, defasagem em meses; sempre OK |
| DIVIDENDO | `provento_contabil` + `indicador_fundamentalista` | payout = (JCP+dividendos do último DFP) / lucro líquido anual do mesmo exercício; ALERTA se > 100% |

`resumo.nivel` = pior nível entre os selos; `leitura` em português lista os selos críticos/em alerta.

## Exemplo

```json
{
  "simbolo": "PETR4",
  "cnpj": "33000167000101",
  "resumo": {"nivel": "ALERTA", "leitura": "Pontos de atenção em AUDITOR, DIVIDA, DIVIDENDO."},
  "selos": [
    {"id": "CADASTRO", "nivel": "OK", "titulo": "Cadastro na CVM", "detalhe": "Situação: ATIVO desde 2005-01-01"},
    {"id": "AUDITOR", "nivel": "ALERTA", "titulo": "Auditor", "detalhe": "Troca de auditor em 2026-03-01: atual Beta Audit, anterior Alfa Audit; justificativa: Rodizio obrigatorio"},
    {"id": "DIVIDA", "nivel": "ALERTA", "titulo": "Vencimento da dívida", "detalhe": "60.0% do saldo devedor (600.00 de 1000.00) vence em até 12 meses; 2 título(s), referência 2026-06-30"},
    {"id": "FUNDOS", "nivel": "OK", "titulo": "Fundos com o ativo", "detalhe": "3 fundo(s) em 2026-08-01 (defasagem de 2 mês(es)); quantidade 310; contra 2026-07-01: 2 entraram, 1 saíram, fundos +1, quantidade +10"},
    {"id": "DIVIDENDO", "nivel": "ALERTA", "titulo": "Sustentabilidade do dividendo", "detalhe": "Payout de 120.0% do lucro líquido no exercício 2025-12-31"}
  ]
}
```

## Verificação

- Testes: `ServicoConfiabilidadeTest` 15/15. Suíte completa: 249 testes, única falha a antiga
  `ServicoSimuladorOperacaoTest.historico_operacao_calcula_pnl_diario`.
- SQL: base descartável `v45_ges4` (V1..V45), linhas de exemplo; as consultas do repositório retornaram o esperado
  (dívida 600/1000, fundos 3 vs 2 com 2 entraram/1 saíram, payout 120/100). Base apagada.

## Desvios

- Limiares são constantes nomeadas em `ServicoConfiabilidade`: `LIMIAR_DIVIDA_12M_PCT = 50`, `LIMIAR_PAYOUT_PCT = 100`, `MESES_TROCA_AUDITOR = 24`.
- DIVIDENDO: o gestor não tem payout pronto; calculado aqui reaproveitando as tabelas lidas por `/proventos-contabeis` (`provento_contabil`) e pelos fundamentos CVM (`indicador_fundamentalista`, ANUAL, `lucro_liquido`, não o do controlador), sem alterar arquivos existentes. Lucro não positivo com proventos = ALERTA; sem DFP ou sem lucro do mesmo exercício = SEM_DADO. Dividend yield não entra (exigiria cotação).
- Resumo: SEM_DADO não conta como "pior" (ordem CRITICO > ALERTA > OK); só é o resumo quando todos os selos são SEM_DADO, e a leitura informa quantos selos estão sem dado.
- 503 só quando nenhuma das quatro tabelas da V45 existe; DIVIDA sem linhas ou com saldo zero = SEM_DADO.
- Troca de auditor: comparada por `cnpj_auditor` entre os registros mais recentes por `data_inicio`.
