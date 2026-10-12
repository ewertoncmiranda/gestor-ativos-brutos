package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAnalise;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicoSimuladorCenarioTest {

    @Test
    void calculaPnlComCotahistECdiComposto() {
        var repositorio = mock(RepositorioSimuladorAnalise.class);
        LocalDate d1 = LocalDate.of(2026, 1, 2);
        LocalDate d2 = d1.plusDays(3);
        when(repositorio.cotacoes("PETR4", d1)).thenReturn(List.of(cotacao(d1, "10"), cotacao(d2, "11")));
        when(repositorio.sinaisNaData("PETR4", d1)).thenReturn(List.of(
                new RepositorioSimuladorAnalise.Sinal(d1, 5, "MOMENTO_PURO_v1")));
        when(repositorio.cdi(d1, d2)).thenReturn(List.of(cdi(d1, "0.10"), cdi(d2, "0.10")));

        var resposta = new ServicoSimuladorCenario(repositorio)
                .simular("PETR4", d1, 1, new BigDecimal("10000"));

        assertEquals(new BigDecimal("10.0000"), resposta.getRetornoPorEstrategia().get("MOMENTO_PURO_v1"));
        assertEquals(new BigDecimal("1000.00"), resposta.getReaisGanhoPerdaPorEstrategia().get("MOMENTO_PURO_v1"));
        assertEquals(new BigDecimal("0.2001"), resposta.getCdiPercentual());
        assertEquals(new BigDecimal("20.01"), resposta.getCdiReais());
    }

    @Test
    void cdiIncompletoPermaneceNuloNuncaZero() {
        var repositorio = mock(RepositorioSimuladorAnalise.class);
        LocalDate d1 = LocalDate.of(2026, 1, 2);
        LocalDate d2 = d1.plusDays(3);
        when(repositorio.cotacoes("PETR4", d1)).thenReturn(List.of(cotacao(d1, "10"), cotacao(d2, "11")));
        when(repositorio.sinaisNaData("PETR4", d1)).thenReturn(List.of(
                new RepositorioSimuladorAnalise.Sinal(d1, 5, "MOMENTO_PURO_v1")));
        when(repositorio.cdi(d1, d2)).thenReturn(List.of(cdi(d1, "0.10")));

        var resposta = new ServicoSimuladorCenario(repositorio)
                .simular("PETR4", d1, 1, new BigDecimal("10000"));

        assertNull(resposta.getCdiPercentual());
        assertNull(resposta.getCdiReais());
    }

    private static RepositorioSimuladorAnalise.Cotacao cotacao(LocalDate data, String fechamento) {
        return new RepositorioSimuladorAnalise.Cotacao(data, new BigDecimal(fechamento));
    }

    private static RepositorioSimuladorAnalise.PontoCdi cdi(LocalDate data, String valor) {
        return new RepositorioSimuladorAnalise.PontoCdi(data, new BigDecimal(valor));
    }
}
