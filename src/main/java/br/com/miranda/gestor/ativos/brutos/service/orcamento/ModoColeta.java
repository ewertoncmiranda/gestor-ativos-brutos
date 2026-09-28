package br.com.miranda.gestor.ativos.brutos.service.orcamento;

/**
 * Cadencia da cotacao intradiaria dos favoritos, escolhida pelo orcamento de
 * cota da BRAPI (plano infra PLANO-ATUALIZACAO-DIARIA, B4). O cron dispara
 * as :05 e as :35 de 10h a 17h; o modo decide em quais desses minutos o
 * ciclo roda de fato.
 */
public enum ModoColeta {

    /** Os 16 ciclos do dia: 10:05, 10:35, ..., 17:35. */
    A_CADA_30_MIN(16),
    /** So os ciclos das :05 (8 por dia) - metade do consumo. */
    A_CADA_60_MIN(8),
    /** Cota nao comporta mais nada no mes: favoritos ficam no COTAHIST do dia anterior. */
    DESLIGADO(0);

    private final int ciclosPorDia;

    ModoColeta(int ciclosPorDia) {
        this.ciclosPorDia = ciclosPorDia;
    }

    public int ciclosPorDia() {
        return ciclosPorDia;
    }

    /** Se o ciclo disparado neste minuto da hora (5 ou 35) roda neste modo. */
    public boolean rodaNoMinuto(int minuto) {
        return switch (this) {
            case A_CADA_30_MIN -> true;
            case A_CADA_60_MIN -> minuto < 30;
            case DESLIGADO -> false;
        };
    }
}
