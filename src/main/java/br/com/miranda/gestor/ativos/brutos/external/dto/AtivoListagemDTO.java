package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Uma linha da tabela unica de ativos do painel (TASK-UX-5 / REQ-UX-8):
 * tudo que a linha mostra numa resposta so, para o painel nao fazer 3
 * chamadas por ativo. Somente leitura, sem BRAPI - preco e o fechamento
 * oficial (cotacao_b3_diaria). Valor ausente e {@code null}, nunca 0.
 *
 * @param serieFechamentos ultimos fechamentos oficiais, do mais antigo ao
 *                         mais recente (sparkline); vazia sem pregao recente
 * @param sinal            ultimo insight do ativo; {@code null} se nunca houve.
 *                         Regra experimental: o painel exibe o aviso.
 * @param ultimoComunicado comunicado CVM mais recente da emissora
 */
public record AtivoListagemDTO(
        String simbolo,
        String nome,
        String setor,
        BigDecimal ultimoFechamento,
        LocalDate dataUltimoFechamento,
        BigDecimal variacaoPercentual,
        List<BigDecimal> serieFechamentos,
        Sinal sinal,
        Comunicado ultimoComunicado,
        Selos selos) {

    public record Sinal(String recomendacao, String versaoRegra, LocalDateTime dataAnalise) {
    }

    public record Comunicado(String categoria, String assunto, LocalDate dataEntrega, String link) {
    }

    /**
     * Flags de qualidade da linha.
     *
     * @param favorito         coleta intradiaria BRAPI (COTACAO_E_HISTORICO)
     * @param monitorado       qualquer cadastro ativo em ativo_monitorado
     * @param temFundamento    ha indicador fundamentalista carregado da CVM
     * @param pregaoDefasado   ultimo fechamento do ativo e anterior ao ultimo
     *                         pregao do mercado (ou nao ha fechamento)
     */
    public record Selos(boolean favorito, boolean monitorado, boolean temFundamento, boolean pregaoDefasado) {
    }
}
