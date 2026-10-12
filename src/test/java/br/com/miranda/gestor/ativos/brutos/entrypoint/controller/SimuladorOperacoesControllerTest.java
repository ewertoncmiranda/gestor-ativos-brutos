package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorOperacaoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorResultadoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoSimuladorOperacao;
import br.com.miranda.gestor.ativos.brutos.service.ServicoSimuladorResultado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimuladorOperacoesControllerTest {

    private static SimuladorOperacoesController controller(
            ServicoSimuladorResultado servicoResultado,
            ServicoSimuladorOperacao servicoOperacao) {
        return new SimuladorOperacoesController(servicoResultado, servicoOperacao, null, null, null);
    }

    @Test
    void getResultadosRetorna7Estrategias() {
        var dto1 = SimuladorResultadoDTO.builder()
                .simbolo("PETR4").versaoEstrategia("MOMENTO_PURO")
                .retornoPct(new BigDecimal("2.50")).build();
        var dto2 = SimuladorResultadoDTO.builder()
                .simbolo("PETR4").versaoEstrategia("BAIXO_SPREAD")
                .retornoPct(new BigDecimal("1.50")).build();

        var fakeServico = new FakeServicoResultado(List.of(dto1, dto2));
        var controller = controller(fakeServico, null);

        var resposta = controller.buscarResultados("PETR4", null).getBody();

        assertNotNull(resposta);
        assertEquals(2, resposta.size());
        assertEquals("MOMENTO_PURO", resposta.get(0).getVersaoEstrategia());
    }

    @Test
    void getOperacoesListaAtivas() {
        var op1 = SimuladorOperacaoDTO.builder()
                .id(1L).simbolo("PETR4").status("PENDENTE")
                .dataEntrada(LocalDate.of(2026, 10, 12))
                .capital(new BigDecimal("10000")).versaoEstrategia("MOMENTO_PURO").build();

        var fakeServico = new FakeServicoOperacao(List.of(op1), null);
        var controller = controller(null, fakeServico);

        var resposta = controller.listarOperacoes().getBody();

        assertNotNull(resposta);
        assertEquals(1, resposta.size());
        assertEquals("PENDENTE", resposta.get(0).getStatus());
    }

    @Test
    void postCriaOperacaoComStatus201() {
        var opCriada = SimuladorOperacaoDTO.builder()
                .id(1L).simbolo("PETR4").status("PENDENTE")
                .dataEntrada(LocalDate.of(2026, 10, 12))
                .capital(new BigDecimal("10000")).versaoEstrategia("MOMENTO_PURO").build();

        var fakeServico = new FakeServicoOperacao(null, opCriada);
        var controller = controller(null, fakeServico);

        var resposta = controller.criarOperacao("PETR4", LocalDate.of(2026, 10, 12),
                new BigDecimal("10000"), "MOMENTO_PURO");

        assertEquals(201, resposta.getStatusCodeValue());
        assertNotNull(resposta.getBody());
        assertEquals(1L, resposta.getBody().getId());
    }

    @Test
    void deleteDeveriaRemoverOperacaoPendente() {
        var fakeServico = new FakeServicoOperacao(null, null);
        var controller = controller(null, fakeServico);

        var resposta = controller.deletarOperacao(1L);

        assertEquals(204, resposta.getStatusCodeValue());
        assertEquals(1, fakeServico.deletosChamados);
    }

    @Test
    void chamadasRepetidasSaoSomenteLeituras() {
        var op = SimuladorOperacaoDTO.builder()
                .id(1L).simbolo("PETR4").status("PENDENTE")
                .build();

        var fakeServico = new FakeServicoOperacao(List.of(op), null);
        var controller = controller(null, fakeServico);

        for (int i = 0; i < 5; i++) {
            var resposta = controller.listarOperacoes().getBody();
            assertNotNull(resposta);
            assertEquals(1, resposta.size());
        }

        assertEquals(5, fakeServico.listagensChamadas);
        assertEquals(0, fakeServico.criacoesChamadas);
        assertEquals(0, fakeServico.deletosChamados);
    }

    static class FakeServicoResultado extends ServicoSimuladorResultado {
        private final List<SimuladorResultadoDTO> retorno;

        FakeServicoResultado(List<SimuladorResultadoDTO> retorno) {
            super(null);
            this.retorno = retorno;
        }

        @Override
        public List<SimuladorResultadoDTO> buscarResultados(String simbolo, LocalDate data) {
            return retorno;
        }
    }

    static class FakeServicoOperacao extends ServicoSimuladorOperacao {
        private final List<SimuladorOperacaoDTO> listagemRetorno;
        private final SimuladorOperacaoDTO criacaoRetorno;
        int listagensChamadas;
        int criacoesChamadas;
        int deletosChamados;

        FakeServicoOperacao(List<SimuladorOperacaoDTO> listagemRetorno, SimuladorOperacaoDTO criacaoRetorno) {
            super(null, null, null);
            this.listagemRetorno = listagemRetorno;
            this.criacaoRetorno = criacaoRetorno;
        }

        @Override
        public List<SimuladorOperacaoDTO> listarOperacoes() {
            listagensChamadas++;
            return listagemRetorno;
        }

        @Override
        public SimuladorOperacaoDTO criar(String simbolo, LocalDate dataEntrada,
                                          BigDecimal capital, String versaoEstrategia) {
            criacoesChamadas++;
            return criacaoRetorno;
        }

        @Override
        public void deletar(Long operacaoId) {
            deletosChamados++;
        }
    }
}
