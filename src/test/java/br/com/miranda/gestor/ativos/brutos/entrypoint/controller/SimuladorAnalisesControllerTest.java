package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.*;
import br.com.miranda.gestor.ativos.brutos.service.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimuladorAnalisesControllerTest {

    private static SimuladorOperacoesController controller(
            ServicoSimuladorIa servicoIa,
            ServicoSimuladorRetroativo servicoRetroativo,
            ServicoSimuladorCenario servicoCenario) {
        return new SimuladorOperacoesController(null, null, servicoIa, servicoRetroativo, servicoCenario);
    }

    @Test
    void postAnaliseIaRetornaComCacheFlag() {
        var fakeServicoIa = new FakeServicoIa(true);
        var controller = controller(fakeServicoIa, null, null);

        var resposta = controller.analisarIa("PETR4").getBody();

        assertNotNull(resposta);
        assertEquals("PETR4", resposta.getSimbolo());
        assertTrue(resposta.getEmCache());
    }

    @Test
    void getRetroativoRetornaComTaxaDeAcerto() {
        var fakeServico = new FakeServicoRetroativo();
        var controller = controller(null, fakeServico, null);

        var resposta = controller.retroativo("PETR4", "MOMENTO_PURO", "v1", 21, LocalDate.of(2026, 1, 1))
                .getBody();

        assertNotNull(resposta);
        assertEquals("PETR4", resposta.getSimbolo());
        assertEquals("MOMENTO_PURO", resposta.getEstrategia());
        assertNotNull(resposta.getTaxaAcerto());
    }

    @Test
    void getCenarioRetornaP_LPorEstrategia() {
        var fakeServico = new FakeServicoCenario();
        var controller = controller(null, null, fakeServico);

        var resposta = controller.cenario("PETR4", LocalDate.of(2026, 10, 12), 21, new BigDecimal("10000"))
                .getBody();

        assertNotNull(resposta);
        assertEquals("PETR4", resposta.getSimbolo());
        assertNotNull(resposta.getRetornoPorEstrategia());
        assertNotNull(resposta.getReaisGanhoPerdaPorEstrategia());
    }

    @Test
    void chamadasRepetidasSaoSomenteLeituras() {
        var fakeServicoIa = new FakeServicoIa(false);
        var controller = controller(fakeServicoIa, null, null);

        for (int i = 0; i < 5; i++) {
            var resposta = controller.analisarIa("PETR4").getBody();
            assertNotNull(resposta);
        }

        assertEquals(5, fakeServicoIa.chamadas);
    }

    static class FakeServicoIa extends ServicoSimuladorIa {
        private boolean emCache;
        int chamadas;

        FakeServicoIa(boolean emCache) {
            super(null, null);
            this.emCache = emCache;
        }

        @Override
        public SimuladorIaAnaliseDTO analisarSimbolo(String simbolo) {
            chamadas++;
            return SimuladorIaAnaliseDTO.builder()
                    .simbolo(simbolo)
                    .emCache(emCache)
                    .analiseTexto("analise fake")
                    .build();
        }
    }

    static class FakeServicoRetroativo extends ServicoSimuladorRetroativo {
        FakeServicoRetroativo() {
            super(null);
        }

        @Override
        public SimuladorRetroativoDTO analisar(String simbolo, String estrategia, String versao,
                                              Integer janelaPregoesApos, LocalDate dataInicio) {
            return SimuladorRetroativoDTO.builder()
                    .simbolo(simbolo)
                    .estrategia(estrategia)
                    .versao(versao)
                    .janelaPregoesApos(janelaPregoesApos)
                    .totalEntradas(0)
                    .ganhos(0)
                    .perdas(0)
                    .taxaAcerto(BigDecimal.ZERO)
                    .retornoMedio(BigDecimal.ZERO)
                    .entradas(List.of())
                    .build();
        }
    }

    static class FakeServicoCenario extends ServicoSimuladorCenario {
        FakeServicoCenario() {
            super(null);
        }

        @Override
        public SimuladorCenarioDTO simular(String simbolo, LocalDate dataEntrada,
                                          Integer janelaPregoesApos, BigDecimal capitalInicial) {
            return SimuladorCenarioDTO.builder()
                    .simbolo(simbolo)
                    .dataEntrada(dataEntrada)
                    .dataSaida(dataEntrada.plusDays(janelaPregoesApos))
                    .janelaPregoesApos(janelaPregoesApos)
                    .capitalInicial(capitalInicial)
                    .retornoPorEstrategia(java.util.Map.of("MOMENTO_PURO", BigDecimal.ZERO))
                    .reaisGanhoPerdaPorEstrategia(java.util.Map.of("MOMENTO_PURO", BigDecimal.ZERO))
                    .cdiPercentual(BigDecimal.ZERO)
                    .cdiReais(BigDecimal.ZERO)
                    .build();
        }
    }
}


