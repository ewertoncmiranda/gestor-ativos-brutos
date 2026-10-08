package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.PregoesDTO.Vela;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioPregoes.Pregao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Volume financeiro (R$) na vela, somado no grupo como volume e negocios. */
class ServicoPregoesAgruparTest {

    private static Pregao oficial(LocalDate d, String financeiro) {
        return new Pregao(d, "PETR4", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN,
                100L, 10, financeiro == null ? null : new BigDecimal(financeiro), "B3_COTAHIST");
    }

    @Test
    void somaVolumeFinanceiroNaSemana() {
        List<Vela> velas = ServicoPregoes.agrupar(List.of(
                oficial(LocalDate.of(2026, 10, 5), "1000.50"),
                oficial(LocalDate.of(2026, 10, 6), "2000.25")), "semana");
        assertEquals(1, velas.size());
        assertEquals(new BigDecimal("3000.75"), velas.get(0).volumeFinanceiro());
        assertEquals(20L, velas.get(0).numeroNegocios());
    }

    @Test
    void diaDaBrapiSemFinanceiroFicaNulo() {
        Pregao brapi = new Pregao(LocalDate.of(2026, 10, 7), "PETR4", BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, 100L, null, null, "BRAPI");
        List<Vela> velas = ServicoPregoes.agrupar(List.of(brapi), "dia");
        assertNull(velas.get(0).volumeFinanceiro());
    }
}
