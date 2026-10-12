package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import br.com.miranda.gestor.ativos.brutos.external.SimuladorOperacaoEntity;
import br.com.miranda.gestor.ativos.brutos.external.dto.SimuladorOperacaoHistoricoDTO;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAnalise;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorOperacao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorOperacaoDiario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServicoSimuladorOperacaoTest {

    private final RepositorioSimuladorOperacao repositorio = mock(RepositorioSimuladorOperacao.class);
    private final RepositorioSimuladorOperacaoDiario repositorioDiario = mock(RepositorioSimuladorOperacaoDiario.class);
    private final RepositorioSimuladorAtivo repositorioAtivo = mock(RepositorioSimuladorAtivo.class);
    private final RepositorioSimuladorAnalise repositorioAnalise = mock(RepositorioSimuladorAnalise.class);
    private final ServicoSimuladorOperacao servico =
            new ServicoSimuladorOperacao(repositorio, repositorioDiario, repositorioAtivo, repositorioAnalise);

    private SimuladorAtivoEntity ativoFake(String simbolo) {
        var a = new SimuladorAtivoEntity();
        a.setId(1);
        a.setSimbolo(simbolo);
        a.setAtivo(Boolean.TRUE);
        return a;
    }

    @Test
    void criar_operacao_com_status_pendente() {
        when(repositorioAtivo.findBySimbolo("PETR4")).thenReturn(Optional.of(ativoFake("PETR4")));
        when(repositorio.save(any())).thenAnswer(i -> {
            var op = (SimuladorOperacaoEntity) i.getArgument(0);
            op.setId(1L);
            op.setAtivo(ativoFake("PETR4"));
            return op;
        });

        var dto = servico.criar("PETR4", LocalDate.of(2026, 10, 12),
                new BigDecimal("10000"), "MOMENTO_PURO");

        assertNotNull(dto);
        assertEquals("PETR4", dto.getSimbolo());
        assertEquals("PENDENTE", dto.getStatus());
        assertEquals(1L, dto.getId());
        verify(repositorio).save(any());
    }

    @Test
    void deletar_operacao_pendente_sucesso() {
        var op = new SimuladorOperacaoEntity();
        op.setId(1L);
        op.setStatus(SimuladorOperacaoEntity.StatusOperacao.PENDENTE);

        when(repositorio.findById(1L)).thenReturn(Optional.of(op));

        assertDoesNotThrow(() -> servico.deletar(1L));
        verify(repositorio).delete(op);
    }

    @Test
    void deletar_operacao_aberta_falha() {
        var op = new SimuladorOperacaoEntity();
        op.setId(1L);
        op.setStatus(SimuladorOperacaoEntity.StatusOperacao.ABERTA);

        when(repositorio.findById(1L)).thenReturn(Optional.of(op));

        assertThrows(IllegalStateException.class, () -> servico.deletar(1L));
        verify(repositorio, never()).delete(any());
    }

    @Test
    void historico_operacao_calcula_pnl_diario() {
        var ativo = ativoFake("PETR4");
        var op = new SimuladorOperacaoEntity();
        op.setId(1L);
        op.setAtivoId(1);
        op.setStatus(SimuladorOperacaoEntity.StatusOperacao.ABERTA);
        op.setDataEntrada(LocalDate.of(2026, 10, 1));
        op.setCapital(new BigDecimal("10000"));
        op.setVersaoEstrategia("MOMENTO_PURO_v1");

        when(repositorio.findById(1L)).thenReturn(Optional.of(op));
        when(repositorioAtivo.findById(1)).thenReturn(Optional.of(ativo));
        when(repositorioAnalise.cotacoes("PETR4", LocalDate.of(2026, 10, 1))).thenReturn(List.of(
                new RepositorioSimuladorAnalise.Cotacao(LocalDate.of(2026, 10, 1), new BigDecimal("32.00")),
                new RepositorioSimuladorAnalise.Cotacao(LocalDate.of(2026, 10, 2), new BigDecimal("33.00")),
                new RepositorioSimuladorAnalise.Cotacao(LocalDate.of(2026, 10, 3), new BigDecimal("31.00"))
        ));
        when(repositorioAnalise.sinais("PETR4", LocalDate.of(2026, 10, 1))).thenReturn(List.of());

        SimuladorOperacaoHistoricoDTO hist = servico.historicoOperacao(1L);

        assertNotNull(hist);
        assertEquals(new BigDecimal("32.00"), hist.getPrecoEntrada());
        assertEquals(3, hist.getPontos().size());

        // dia 1: retorno = 0
        assertEquals(BigDecimal.ZERO, hist.getPontos().get(0).getRetornoDiaPct());
        assertEquals(BigDecimal.ZERO, hist.getPontos().get(0).getRetornoAcumPct());

        // dia 2: +3.125% acumulado
        var ponto2 = hist.getPontos().get(1);
        assertTrue(ponto2.getRetornoAcumPct().compareTo(BigDecimal.ZERO) > 0);

        // dia 3: -3.125% acumulado (preço abaixo da entrada)
        var ponto3 = hist.getPontos().get(2);
        assertTrue(ponto3.getRetornoAcumPct().compareTo(BigDecimal.ZERO) < 0);

        // totalPnlRs = pnlRs do último ponto
        assertEquals(hist.getTotalPnlRs(), ponto3.getPnlRs());
    }

    @Test
    void historico_operacao_sem_cotacoes_retorna_vazio() {
        var ativo = ativoFake("PETR4");
        var op = new SimuladorOperacaoEntity();
        op.setId(2L);
        op.setAtivoId(1);
        op.setStatus(SimuladorOperacaoEntity.StatusOperacao.PENDENTE);
        op.setDataEntrada(LocalDate.of(2026, 10, 1));
        op.setCapital(new BigDecimal("5000"));
        op.setVersaoEstrategia("BAIXO_SPREAD_v1");

        when(repositorio.findById(2L)).thenReturn(Optional.of(op));
        when(repositorioAtivo.findById(1)).thenReturn(Optional.of(ativo));
        when(repositorioAnalise.cotacoes("PETR4", LocalDate.of(2026, 10, 1))).thenReturn(List.of());

        SimuladorOperacaoHistoricoDTO hist = servico.historicoOperacao(2L);

        assertNotNull(hist);
        assertNull(hist.getPrecoEntrada());
        assertTrue(hist.getPontos().isEmpty());
    }

    @Test
    void rotina_matinal_abre_operacoes_com_data_passada() {
        var op = new SimuladorOperacaoEntity();
        op.setId(1L);
        op.setStatus(SimuladorOperacaoEntity.StatusOperacao.PENDENTE);
        op.setDataEntrada(LocalDate.of(2026, 10, 10));

        when(repositorio.findByStatus(SimuladorOperacaoEntity.StatusOperacao.PENDENTE))
                .thenReturn(java.util.List.of(op));
        when(repositorio.findByStatus(SimuladorOperacaoEntity.StatusOperacao.ABERTA))
                .thenReturn(java.util.List.of());
        when(repositorio.save(any())).thenReturn(op);

        servico.rotinaMatinal(LocalDate.of(2026, 10, 12));

        verify(repositorio).save(any());
        assertEquals(SimuladorOperacaoEntity.StatusOperacao.ABERTA, op.getStatus());
    }
}
