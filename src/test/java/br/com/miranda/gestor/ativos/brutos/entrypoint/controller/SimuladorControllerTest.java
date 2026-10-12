package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorAtivoDTO;
import br.com.miranda.gestor.ativos.brutos.service.ServicoSimuladorAtivo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimuladorControllerTest {

    private static SimuladorController controller(ServicoSimuladorAtivo servico) {
        return new SimuladorController(servico);
    }


    @Test
    void postCriaNovoAtivoComSimbolo() {
        var servico = new FakeServicoSimuladorAtivo(true);
        var controller = controller(servico);

        var resposta = controller.registrarAtivo("PETR4").getBody();

        assertNotNull(resposta);
        assertEquals("PETR4", resposta.getSimbolo());
        assertTrue(resposta.getAtivo());
        assertEquals(1, servico.registrosChamados);
    }

    @Test
    void getListaAtivosCadastrados() {
        var ativo1 = SimuladorAtivoDTO.builder().simbolo("PETR4").ativo(true).build();
        var ativo2 = SimuladorAtivoDTO.builder().simbolo("VALE3").ativo(true).build();
        var servico = new FakeServicoSimuladorAtivo(List.of(ativo1, ativo2));
        var controller = controller(servico);

        var resposta = controller.listarAtivos().getBody();

        assertNotNull(resposta);
        assertEquals(2, resposta.size());
        assertEquals("PETR4", resposta.get(0).getSimbolo());
        assertEquals("VALE3", resposta.get(1).getSimbolo());
    }

    @Test
    void deleteDesativaAtivoExistente() {
        var servico = new FakeServicoSimuladorAtivo(false);
        var controller = controller(servico);

        var resposta = controller.desativarAtivo("PETR4");

        assertEquals(204, resposta.getStatusCodeValue());
        assertEquals(1, servico.desativosChamados);
    }

    @Test
    void chamadasRepetidasDeListagemSaoSomenteLeituras() {
        var ativo = SimuladorAtivoDTO.builder().simbolo("PETR4").ativo(true).build();
        var servico = new FakeServicoSimuladorAtivo(List.of(ativo));
        var controller = controller(servico);

        for (int i = 0; i < 5; i++) {
            var resposta = controller.listarAtivos().getBody();
            assertNotNull(resposta);
            assertEquals(1, resposta.size());
        }

        assertEquals(5, servico.listagensChamadas);
        assertEquals(0, servico.registrosChamados);
        assertEquals(0, servico.desativosChamados);
    }

    static class FakeServicoSimuladorAtivo extends ServicoSimuladorAtivo {
        List<SimuladorAtivoDTO> listagemRetorno;
        boolean registroLanca;
        int listagensChamadas;
        int registrosChamados;
        int desativosChamados;

        FakeServicoSimuladorAtivo(List<SimuladorAtivoDTO> listagemRetorno) {
            super(null, null);
            this.listagemRetorno = listagemRetorno;
        }

        FakeServicoSimuladorAtivo(boolean lancar) {
            super(null, null);
            this.registroLanca = lancar;
        }

        @Override
        public List<SimuladorAtivoDTO> listarAtivos() {
            listagensChamadas++;
            return listagemRetorno;
        }

        @Override
        public SimuladorAtivoDTO registrar(String codigoAtivo) {
            registrosChamados++;
            if (registroLanca) {
                return SimuladorAtivoDTO.builder().simbolo(codigoAtivo).ativo(true).build();
            }
            throw new IllegalArgumentException("Símbolo inválido");
        }

        @Override
        public void desativar(String codigoAtivo) {
            desativosChamados++;
        }
    }
}

