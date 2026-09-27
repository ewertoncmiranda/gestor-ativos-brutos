package br.com.miranda.gestor.ativos.brutos.tools;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class IntervaloConfiancaTest {

    @Test
    void wilson_de_14_em_24_confere_com_a_formula() {
        IntervaloConfianca.Intervalo ic = IntervaloConfianca.wilson(14, 24);
        assertEquals(new BigDecimal("0.3883"), ic.inferior());
        assertEquals(new BigDecimal("0.7553"), ic.superior());
    }

    @Test
    void wilson_nao_sai_de_zero_a_um_nos_extremos() {
        IntervaloConfianca.Intervalo nenhum = IntervaloConfianca.wilson(0, 5);
        IntervaloConfianca.Intervalo todos = IntervaloConfianca.wilson(5, 5);
        assertEquals(0, nenhum.inferior().signum());
        assertTrue(nenhum.superior().compareTo(BigDecimal.ONE) < 0);
        assertEquals(0, todos.superior().compareTo(BigDecimal.ONE));
        assertTrue(todos.inferior().signum() > 0);
    }

    @Test
    void sem_amostra_nao_ha_intervalo() {
        assertNull(IntervaloConfianca.wilson(0, 0));
        assertNull(IntervaloConfianca.media(new BigDecimal("0.03"), null, 10L));
        assertNull(IntervaloConfianca.media(new BigDecimal("0.03"), new BigDecimal("0.1"), 1L));
    }

    @Test
    void media_mais_ou_menos_erro_padrao() {
        // media 3%, desvio 10%, n 100: erro 1% -> 3% +- 1,96%
        IntervaloConfianca.Intervalo ic = IntervaloConfianca.media(new BigDecimal("0.03"), new BigDecimal("0.10"), 100L);
        assertEquals(new BigDecimal("0.0104"), ic.inferior());
        assertEquals(new BigDecimal("0.0496"), ic.superior());
    }
}
