package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAnalise;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServicoSimuladorRetroativoTest {

    @Test
    void cruzaQuintilComPregoesReaisECalculaResumo() {
        var repositorio = mock(RepositorioSimuladorAnalise.class);
        LocalDate d1 = LocalDate.of(2026, 1, 2);
        when(repositorio.sinais("PETR4", d1)).thenReturn(List.of(
                new RepositorioSimuladorAnalise.Sinal(d1, 5, "MOMENTO_PURO_v1")));
        when(repositorio.cotacoes("PETR4", d1)).thenReturn(List.of(
                cotacao(d1, "10.00"), cotacao(d1.plusDays(3), "11.00")));

        var resposta = new ServicoSimuladorRetroativo(repositorio)
                .analisar("PETR4", "MOMENTO_PURO", "v1", 1, d1);

        assertEquals(1, resposta.getTotalEntradas());
        assertEquals(1, resposta.getGanhos());
        assertEquals(new BigDecimal("100.0000"), resposta.getTaxaAcerto());
        assertEquals(new BigDecimal("10.0000"), resposta.getRetornoMedio());
        assertEquals(d1.plusDays(3), resposta.getEntradas().get(0).getDataJanela());
    }

    private static RepositorioSimuladorAnalise.Cotacao cotacao(LocalDate data, String fechamento) {
        return new RepositorioSimuladorAnalise.Cotacao(data, new BigDecimal(fechamento));
    }
}
