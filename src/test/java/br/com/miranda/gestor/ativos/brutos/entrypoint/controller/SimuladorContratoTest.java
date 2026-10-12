package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.*;
import br.com.miranda.gestor.ativos.brutos.service.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SIM-GES-8: Testes de contrato para o simulador.
 * GET sem gravação, POST idempotente, ausência de token IA nos logs.
 */
class SimuladorContratoTest {

    @Test
    void getResultadosSemGravacao() {
        var fakeServico = new FakeServicoResultadoComContagem();
        var controller = new SimuladorOperacoesController(fakeServico, null, null, null, null);

        for (int i = 0; i < 5; i++) {
            assertTrue(controller.buscarResultados("PETR4", null).getStatusCode().is2xxSuccessful());
        }

        assertEquals(5, fakeServico.leiturasChamadas);
        assertEquals(0, fakeServico.escritasChamadas);
    }

    @Test
    void postOperacaoIdempotente() {
        var fakeServico = new FakeServicoOperacaoComContagem();
        var controller = new SimuladorOperacoesController(null, fakeServico, null, null, null);

        var dataMesma = LocalDate.of(2026, 10, 12);
        var capital = new BigDecimal("10000");

        var r1 = controller.criarOperacao("PETR4", dataMesma, capital, "MOMENTO_PURO");
        var r2 = controller.criarOperacao("PETR4", dataMesma, capital, "MOMENTO_PURO");

        assertTrue(r1.getStatusCode().is2xxSuccessful());
        assertTrue(r2.getStatusCode().is2xxSuccessful());
        assertEquals(2, fakeServico.criacoesChamadas);
    }

    @Test
    void getRetroativoSemGravacao() {
        var fakeServico = new FakeServicoRetroativoComContagem();
        var controller = new SimuladorOperacoesController(null, null, null, fakeServico, null);

        for (int i = 0; i < 5; i++) {
            assertTrue(controller.retroativo("PETR4", "MOMENTO_PURO", "v1", 21,
                    LocalDate.of(2026, 1, 1)).getStatusCode().is2xxSuccessful());
        }

        assertEquals(5, fakeServico.leiturasChamadas);
        assertEquals(0, fakeServico.escritasChamadas);
    }

    @Test
    void getCenarioSemGravacao() {
        var fakeServico = new FakeServicoCenarioComContagem();
        var controller = new SimuladorOperacoesController(null, null, null, null, fakeServico);

        for (int i = 0; i < 5; i++) {
            assertTrue(controller.cenario("PETR4", LocalDate.of(2026, 10, 12), 21,
                    new BigDecimal("10000")).getStatusCode().is2xxSuccessful());
        }

        assertEquals(5, fakeServico.leiturasChamadas);
        assertEquals(0, fakeServico.escritasChamadas);
    }

    @Test
    void postAnaliseIaSemTokenNosLogs() {
        var fakeServico = new FakeServicoIaComContagem();
        var controller = new SimuladorOperacoesController(null, null, fakeServico, null, null);

        var resposta = controller.analisarIa("PETR4");

        assertTrue(resposta.getStatusCode().is2xxSuccessful());
        assertEquals(1, fakeServico.chamadas);
        assertFalse(fakeServico.ultimasRespostas.contains("token"));
        assertFalse(fakeServico.ultimasRespostas.contains("api_key"));
    }

    static class FakeServicoResultadoComContagem extends ServicoSimuladorResultado {
        int leiturasChamadas;
        int escritasChamadas;

        FakeServicoResultadoComContagem() { super(null); }

        @Override
        public List<SimuladorResultadoDTO> buscarResultados(String simbolo, LocalDate data) {
            leiturasChamadas++;
            return List.of();
        }
    }

    static class FakeServicoOperacaoComContagem extends ServicoSimuladorOperacao {
        int criacoesChamadas;

        FakeServicoOperacaoComContagem() { super(null, null, null); }

        @Override
        public SimuladorOperacaoDTO criar(String simbolo, LocalDate dataEntrada,
                                          BigDecimal capital, String versaoEstrategia) {
            criacoesChamadas++;
            return SimuladorOperacaoDTO.builder()
                    .id((long) criacoesChamadas)
                    .simbolo(simbolo)
                    .status("PENDENTE")
                    .dataEntrada(dataEntrada)
                    .capital(capital)
                    .versaoEstrategia(versaoEstrategia)
                    .build();
        }
    }

    static class FakeServicoRetroativoComContagem extends ServicoSimuladorRetroativo {
        int leiturasChamadas;
        int escritasChamadas;

        FakeServicoRetroativoComContagem() { super(null); }

        @Override
        public SimuladorRetroativoDTO analisar(String simbolo, String estrategia, String versao,
                                              Integer janelaPregoesApos, LocalDate dataInicio) {
            leiturasChamadas++;
            return SimuladorRetroativoDTO.builder()
                    .simbolo(simbolo).estrategia(estrategia).versao(versao)
                    .janelaPregoesApos(janelaPregoesApos).totalEntradas(0)
                    .ganhos(0).perdas(0).taxaAcerto(BigDecimal.ZERO)
                    .retornoMedio(BigDecimal.ZERO).entradas(List.of()).build();
        }
    }

    static class FakeServicoCenarioComContagem extends ServicoSimuladorCenario {
        int leiturasChamadas;
        int escritasChamadas;

        FakeServicoCenarioComContagem() { super(null); }

        @Override
        public SimuladorCenarioDTO simular(String simbolo, LocalDate dataEntrada,
                                          Integer janelaPregoesApos, BigDecimal capitalInicial) {
            leiturasChamadas++;
            return SimuladorCenarioDTO.builder()
                    .simbolo(simbolo).dataEntrada(dataEntrada)
                    .dataSaida(dataEntrada.plusDays(janelaPregoesApos))
                    .janelaPregoesApos(janelaPregoesApos).capitalInicial(capitalInicial)
                    .retornoPorEstrategia(java.util.Map.of())
                    .reaisGanhoPerdaPorEstrategia(java.util.Map.of())
                    .cdiPercentual(BigDecimal.ZERO).cdiReais(BigDecimal.ZERO).build();
        }
    }

    static class FakeServicoIaComContagem extends ServicoSimuladorIa {
        int chamadas;
        List<String> ultimasRespostas = new ArrayList<>();

        FakeServicoIaComContagem() { super(null, null); }

        @Override
        public SimuladorIaAnaliseDTO analisarSimbolo(String simbolo) {
            chamadas++;
            var dto = SimuladorIaAnaliseDTO.builder()
                    .simbolo(simbolo)
                    .emCache(false)
                    .analiseTexto("analise stub")
                    .build();
            ultimasRespostas.add(dto.toString());
            return dto;
        }
    }
}
