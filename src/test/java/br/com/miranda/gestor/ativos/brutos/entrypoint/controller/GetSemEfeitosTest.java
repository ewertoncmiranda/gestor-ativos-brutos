package br.com.miranda.gestor.ativos.brutos.entrypoint.controller;

import br.com.miranda.gestor.ativos.brutos.external.Ativo;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBrApi;
import br.com.miranda.gestor.ativos.brutos.repository.*;
import br.com.miranda.gestor.ativos.brutos.service.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetSemEfeitosTest {
    @Test void cotacaoSemCacheConsultaSemPublicarOuPersistir() {
        var servico = mock(ServicoAtivo.class);
        var monitorados = mock(ServicoAtivoMonitorado.class);
        var repositorio = mock(RepositorioCotacaoAtual.class);
        var cache = mock(ServicoAtualizacaoCache.class);
        when(repositorio.findBySimbolo("PETR4")).thenReturn(Optional.empty());
        var ativo = new Ativo();
        when(servico.consultarAtivoAPI("PETR4")).thenReturn(ativo);
        var controller = new AtivoController(servico, monitorados, repositorio, cache);
        assertSame(ativo, controller.buscarPorSimbolo("PETR4").getBody());
        assertSame(ativo, controller.buscarPorSimboloComSerieHistorica("PETR4").getBody());
        verify(servico, times(2)).consultarAtivoAPI("PETR4");
        verifyNoMoreInteractions(servico);
        verifyNoInteractions(cache, monitorados);
        verify(repositorio, never()).save(any());
    }

    @Test void historicoSemCacheNaoPersisteFallback() {
        var api = mock(ClienteBrApi.class);
        var repositorio = mock(RepositorioCandleDiario.class);
        var cache = mock(ServicoAtualizacaoCache.class);
        when(repositorio.findBySimboloOrderByDataAsc("PETR4")).thenReturn(List.of());
        when(api.consultarHistorico(any())).thenReturn(new RespostaHistoricoAcoesDTO(List.of(), null, null));
        var controller = new HistoricoAcoesController(api, repositorio, cache);
        assertEquals(200, controller.buscarHistorico("PETR4", null, null, null, null, null).getStatusCode().value());
        verifyNoInteractions(cache);
        verify(repositorio, never()).save(any());
    }
}
