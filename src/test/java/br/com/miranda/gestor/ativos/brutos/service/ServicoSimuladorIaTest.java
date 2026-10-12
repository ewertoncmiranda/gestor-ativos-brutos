package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import br.com.miranda.gestor.ativos.brutos.external.SimuladorIaAnaliseEntity;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorAtivo;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSimuladorIaAnalise;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServicoSimuladorIaTest {

    private final RepositorioSimuladorIaAnalise repositorio = mock(RepositorioSimuladorIaAnalise.class);
    private final RepositorioSimuladorAtivo repositorioAtivo = mock(RepositorioSimuladorAtivo.class);
    private final ServicoSimuladorIa servico = new ServicoSimuladorIa(repositorio, repositorioAtivo);

    private SimuladorAtivoEntity ativoFake(String simbolo) {
        SimuladorAtivoEntity a = new SimuladorAtivoEntity();
        a.setId(1);
        a.setSimbolo(simbolo);
        a.setAtivo(Boolean.TRUE);
        return a;
    }

    @Test
    void analisar_simbolo_retorna_do_cache() {
        var ativo = ativoFake("PETR4");
        when(repositorioAtivo.findBySimbolo("PETR4")).thenReturn(Optional.of(ativo));

        var emCacheEntity = new SimuladorIaAnaliseEntity();
        emCacheEntity.setAtivoId(1);
        emCacheEntity.setDataAnalise(LocalDateTime.now());
        emCacheEntity.setAnaliseTexto("analise cached");
        emCacheEntity.setModeloIa("llama-real");
        emCacheEntity.setTokensUsados(120);
        emCacheEntity.setAtivo(ativo);

        when(repositorio.findBySimboloAndDataAnalise("PETR4", LocalDate.now()))
                .thenReturn(Optional.of(emCacheEntity));

        var resultado = servico.analisarSimbolo("PETR4");

        assertNotNull(resultado);
        assertEquals("PETR4", resultado.getSimbolo());
        assertEquals("DISPONIVEL", resultado.getStatus());
        assertNull(resultado.getErro());
        assertTrue(resultado.getEmCache());
        verify(repositorio).findBySimboloAndDataAnalise("PETR4", LocalDate.now());
        verify(repositorio, never()).save(any());
    }

    @Test
    void analisar_simbolo_nao_em_cache_retorna_indisponivel_sem_salvar_stub() {
        var ativo = ativoFake("PETR4");
        when(repositorioAtivo.findBySimbolo("PETR4")).thenReturn(Optional.of(ativo));
        when(repositorio.findBySimboloAndDataAnalise("PETR4", LocalDate.now()))
                .thenReturn(Optional.empty());

        var resultado = servico.analisarSimbolo("PETR4");

        assertNotNull(resultado);
        assertEquals("PETR4", resultado.getSimbolo());
        assertFalse(resultado.getEmCache());
        assertEquals("INDISPONIVEL", resultado.getStatus());
        assertEquals("Integração insider-ia não configurada.", resultado.getErro());
        assertNull(resultado.getAnaliseTexto());
        assertNull(resultado.getModeloIa());
        assertNull(resultado.getTokensUsados());
        verify(repositorio, never()).save(any());
    }

    @Test
    void analisar_simbolo_ignora_stub_legado_no_cache() {
        var ativo = ativoFake("PETR4");
        when(repositorioAtivo.findBySimbolo("PETR4")).thenReturn(Optional.of(ativo));

        var stubLegado = new SimuladorIaAnaliseEntity();
        stubLegado.setAtivoId(1);
        stubLegado.setDataAnalise(LocalDateTime.now());
        stubLegado.setAnaliseTexto("analise sintetica");
        stubLegado.setModeloIa("stub");
        stubLegado.setAtivo(ativo);
        when(repositorio.findBySimboloAndDataAnalise("PETR4", LocalDate.now()))
                .thenReturn(Optional.of(stubLegado));

        var resultado = servico.analisarSimbolo("PETR4");

        assertEquals("INDISPONIVEL", resultado.getStatus());
        assertFalse(resultado.getEmCache());
        assertNull(resultado.getAnaliseTexto());
        verify(repositorio, never()).save(any());
    }
}
