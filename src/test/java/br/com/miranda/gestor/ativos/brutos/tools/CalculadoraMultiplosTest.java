package br.com.miranda.gestor.ativos.brutos.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CalculadoraMultiplosTest {

    @Nested
    @DisplayName("preco sobre lucro")
    class PrecoLucro {

        @Test
        void divide_preco_pelo_lpa() {
            BigDecimal resultado = CalculadoraMultiplos.precoLucro(
                    new BigDecimal("50.00"), new BigDecimal("2.00"));

            assertEquals(new BigDecimal("25.0000"), resultado);
        }

        @Test
        void lpa_zero_devolve_nulo_em_vez_de_estourar() {
            assertNull(CalculadoraMultiplos.precoLucro(
                    new BigDecimal("50.00"), BigDecimal.ZERO));
        }

        @Test
        void lpa_negativo_devolve_nulo_porque_pl_de_prejuizo_nao_tem_significado() {
            // Publicar um P/L negativo sugeriria que ele e comparavel com o
            // das empresas lucrativas, e nao e.
            assertNull(CalculadoraMultiplos.precoLucro(
                    new BigDecimal("50.00"), new BigDecimal("-2.00")));
        }

        @Test
        void preco_ausente_devolve_nulo() {
            assertNull(CalculadoraMultiplos.precoLucro(null, new BigDecimal("2.00")));
        }

        @Test
        void lpa_ausente_devolve_nulo() {
            assertNull(CalculadoraMultiplos.precoLucro(new BigDecimal("50.00"), null));
        }
    }

    @Nested
    @DisplayName("preco sobre valor patrimonial")
    class PrecoValorPatrimonial {

        @Test
        void divide_preco_pelo_vpa() {
            BigDecimal resultado = CalculadoraMultiplos.precoValorPatrimonial(
                    new BigDecimal("8.30"), new BigDecimal("4.1512"));

            assertEquals(new BigDecimal("1.9994"), resultado);
        }

        @Test
        void vpa_zero_devolve_nulo() {
            assertNull(CalculadoraMultiplos.precoValorPatrimonial(
                    new BigDecimal("8.30"), BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("defasagem")
    class Defasagem {

        @Test
        void conta_os_dias_entre_o_fechamento_e_hoje() {
            Long dias = CalculadoraMultiplos.defasagemEmDias(
                    LocalDate.of(2025, 12, 31), LocalDate.of(2026, 9, 26));

            assertEquals(269L, dias);
        }

        @Test
        void periodo_ausente_devolve_nulo() {
            assertNull(CalculadoraMultiplos.defasagemEmDias(null, LocalDate.now()));
        }
    }
}
