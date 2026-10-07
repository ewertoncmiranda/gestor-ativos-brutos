package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.tools.IntervaloConfianca;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Backtest por ranking (Plano LAC, LAC-GES-1; {@code backtest_execucao.metodo
 * = 'RANKING'}, infra V16) - metodo alternativo ao de classes (COMPRA_FORTE,
 * VENDA_VALUATION...) em {@link BacktestDTO}: mede se a regra ordena melhor
 * do que pior, nao so se acerta uma classe.
 */
public record RankingBacktestDTO(Execucao execucao, List<Janela> janelas, String aviso) {

    public record Execucao(long id, LocalDateTime finalizadoEm, String hipotese, Integer numeroTentativa,
                           String esquemaValidacao) {
    }

    /** correlacaoRankingMedia: media do Spearman mensal, com IC 95% quando ha >= 2 meses. */
    public record Janela(String versaoRegra, String janela, int horizonte,
                         BigDecimal correlacaoRankingMedia, IntervaloConfianca.Intervalo icCorrelacao,
                         int meses, Integer ativosPorMes, List<Quintil> quintis,
                         BigDecimal diferencaQuintil5Menos1) {
    }

    public record Quintil(int quintil, BigDecimal retornoMedio, int ativos) {
    }
}
