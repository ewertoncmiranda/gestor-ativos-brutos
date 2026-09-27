package br.com.miranda.gestor.ativos.brutos.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Intervalos de confianca de 95% para o placar (infra#TASK-30): o que separa
 * "58% de acerto" de "58% de acerto, mas pode ser 46% ou 69%".
 *
 * <p>Acerto: intervalo de Wilson - ao contrario do intervalo normal simples,
 * nao sai de [0, 1] nem colapsa com poucas janelas ou taxa perto de 0/100%.
 * Excesso medio: media +- 1,96 x desvio / raiz(n), com n de quem tem o valor.
 */
public final class IntervaloConfianca {

    public record Intervalo(BigDecimal inferior, BigDecimal superior) {
    }

    static final double Z_95 = 1.959964;

    private IntervaloConfianca() {
    }

    /** Wilson 95% para {@code acertos} em {@code n}; null sem amostra. */
    public static Intervalo wilson(long acertos, long n) {
        if (n <= 0) {
            return null;
        }
        double p = (double) acertos / n;
        double z2 = Z_95 * Z_95;
        double centro = (p + z2 / (2 * n)) / (1 + z2 / n);
        double margem = Z_95 * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / (1 + z2 / n);
        return new Intervalo(arredondar(Math.max(0, centro - margem)), arredondar(Math.min(1, centro + margem)));
    }

    /** media +- 1,96 x erro-padrao; null com menos de 2 valores ou sem desvio. */
    public static Intervalo media(BigDecimal media, BigDecimal desvio, Long n) {
        if (media == null || desvio == null || n == null || n < 2) {
            return null;
        }
        double erro = desvio.doubleValue() / Math.sqrt(n);
        double m = media.doubleValue();
        return new Intervalo(arredondar(m - Z_95 * erro), arredondar(m + Z_95 * erro));
    }

    private static BigDecimal arredondar(double valor) {
        return BigDecimal.valueOf(valor).setScale(4, RoundingMode.HALF_UP);
    }
}
