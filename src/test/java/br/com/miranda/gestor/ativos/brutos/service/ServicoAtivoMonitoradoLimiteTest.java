package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoLimiteFavoritos;
import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAtivoMonitorado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioIdentidadeAtivo;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ServicoAtivoMonitoradoLimiteTest {

    private final RepositorioAtivoMonitorado repositorio = mock(RepositorioAtivoMonitorado.class);
    private final RepositorioIdentidadeAtivo identidade = mock(RepositorioIdentidadeAtivo.class);
    private final ServicoAtivoMonitorado servico = new ServicoAtivoMonitorado(repositorio, identidade);

    {
        when(identidade.canonico(anyString())).thenAnswer(i -> i.getArgument(0));
        when(repositorio.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repositorio.findByTipoColetaAndAtivoTrueOrderBySimboloAsc(TipoColeta.COTACAO_E_HISTORICO))
                .thenReturn(Collections.nCopies(35, new AtivoMonitoradoEntity()));
    }

    @Test
    void trigesimo_sexto_favorito_e_recusado() {
        when(repositorio.findBySimbolo("WEGE3")).thenReturn(Optional.empty());
        ExcecaoLimiteFavoritos erro = assertThrows(ExcecaoLimiteFavoritos.class, () -> servico.registrar("WEGE3"));
        assertTrue(erro.getMessage().contains("35"));
        verify(repositorio, never()).save(any());
    }

    @Test
    void refavoritar_quem_ja_e_favorito_nao_conta_no_limite() {
        AtivoMonitoradoEntity raiz = new AtivoMonitoradoEntity();
        raiz.setSimbolo("RAIZ4");
        raiz.setTipoColeta(TipoColeta.COTACAO_E_HISTORICO);
        raiz.setAtivo(true);
        when(repositorio.findBySimbolo("RAIZ4")).thenReturn(Optional.of(raiz));
        assertEquals("RAIZ4", servico.registrar("RAIZ4").getSimbolo());
    }

    @Test
    void marcar_processado_so_atualiza_a_data_sem_save() {
        AtivoMonitoradoEntity raiz = new AtivoMonitoradoEntity();
        raiz.setId(7L);
        servico.marcarProcessado(raiz);
        verify(repositorio).marcarAtualizado(eq(7L), any());
        verify(repositorio, never()).save(any());
    }
}
