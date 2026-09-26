package br.com.miranda.gestor.ativos.brutos.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Deriva múltiplos de mercado cruzando fundamento com preço.
 *
 * <p>P/L e P/VP não são gravados em lugar nenhum de propósito: LPA e VPA mudam
 * por trimestre, o preço muda em segundos. Calcular na leitura mantém o
 * múltiplo sempre atual sem reexecutar o ETL.
 *
 * <p>Classe sem estado, só métodos estáticos, seguindo o padrão de
 * {@code tools/}.
 */
public final class CalculadoraMultiplos {

    private static final int CASAS = 4;

    private CalculadoraMultiplos() {
    }

    /**
     * Preço sobre lucro por ação.
     *
     * <p>Devolve null quando o LPA é nulo ou não positivo: empresa que dá
     * prejuízo não tem P/L com significado, e publicar um número negativo
     * sugeriria que é comparável com o das outras.
     */
    public static BigDecimal precoLucro(BigDecimal preco, BigDecimal lpa) {
        return dividir(preco, lpa);
    }

    /** Preço sobre valor patrimonial por ação. */
    public static BigDecimal precoValorPatrimonial(BigDecimal preco, BigDecimal vpa) {
        return dividir(preco, vpa);
    }

    /**
     * Dias entre o fechamento do exercício e hoje.
     *
     * <p>Vai na resposta porque cruzar preço de hoje com balanço de dezembro
     * sem dizer a distância entre eles produz número sem contexto.
     */
    public static Long defasagemEmDias(LocalDate periodo, LocalDate hoje) {
        if (periodo == null || hoje == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(periodo, hoje);
    }

    private static BigDecimal dividir(BigDecimal numerador, BigDecimal denominador) {
        if (numerador == null || denominador == null
                || denominador.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return numerador.divide(denominador, CASAS, RoundingMode.HALF_UP);
    }
}
