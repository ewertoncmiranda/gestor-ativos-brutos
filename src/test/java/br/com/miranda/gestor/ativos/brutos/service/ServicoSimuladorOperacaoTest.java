package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import br.com.miranda.gestor.ativos.brutos.external.SimuladorOperacaoEntity;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorOperacao;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorOperacaoDiario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServicoSimuladorOperacaoTest {

    private final RepositorioSimuladorOperacao repositorio = mock(RepositorioSimuladorOperacao.class);
    private final RepositorioSimuladorOperacaoDiario repositorioDiario = mock(RepositorioSimuladorOperacaoDiario.class);
    private final RepositorioSimuladorAtivo repositorioAtivo = mock(RepositorioSimuladorAtivo.class);
    private final ServicoSimuladorOperacao servico =
            new ServicoSimuladorOperacao(repositorio, repositorioDiario, repositorioAtivo);

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
