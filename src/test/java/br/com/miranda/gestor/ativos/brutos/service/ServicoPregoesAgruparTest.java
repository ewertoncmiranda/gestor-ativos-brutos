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

    private static Pregao enriquecido(int dia, long volume, String medio, String marca) {
        return new Pregao(LocalDate.of(2026, 10, dia), "PETR4", BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, volume, 1, BigDecimal.TEN,
                "B3_COTAHIST", medio == null ? null : new BigDecimal(medio),
                BigDecimal.valueOf(dia), BigDecimal.valueOf(dia + 1), marca);
    }

    @Test
    void diarioRepassaCamposNovosEJsonMantemContratoAntigo() {
        Vela v = ServicoPregoes.agrupar(List.of(enriquecido(5, 100, "12.34", "EDJ")), "dia").getFirst();
        assertEquals(new BigDecimal("12.34"), v.precoMedio());
        assertEquals(BigDecimal.valueOf(5), v.melhorOfertaCompra());
        assertEquals(BigDecimal.valueOf(6), v.melhorOfertaVenda());
        assertEquals("EDJ", v.marcaEx());
        var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(v);
        assertEquals(10, json.get("fechamento").asInt());
        assertEquals("EDJ", json.get("marcaEx").asText());
        assertEquals(0, json.get("precoMedio").decimalValue().compareTo(new BigDecimal("12.34")));
    }

    @Test
    void semanaEMesPonderamMediaPreservamMarcasEUsamUltimasOfertas() {
        for (String intervalo : List.of("semana", "mes")) {
            Vela v = ServicoPregoes.agrupar(List.of(enriquecido(5, 100, "10", "ED"),
                    enriquecido(6, 300, "20", "EJ")), intervalo).getFirst();
            assertEquals(0, new BigDecimal("17.5").compareTo(v.precoMedio()));
            assertEquals("ED,EJ", v.marcaEx());
            assertEquals(BigDecimal.valueOf(6), v.melhorOfertaCompra());
        }
    }

    @Test
    void mediaParcialNaoEPublicadaEConstrutoresLegadosContinuamValidos() {
        Vela v = ServicoPregoes.agrupar(List.of(enriquecido(5, 100, "10", null),
                enriquecido(6, 300, null, null)), "semana").getFirst();
        assertNull(v.precoMedio());
        assertNull(v.marcaEx());
        Vela antiga = new Vela(null, null, "X", null, null, null, null,
                null, null, null, 1, "BRAPI");
        assertNull(antiga.melhorOfertaVenda());
        assertNull(oficial(LocalDate.of(2026, 10, 5), "1").precoMedio());
    }

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
