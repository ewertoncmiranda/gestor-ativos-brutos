package br.com.miranda.gestor.ativos.brutos.service.coleta;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoCotaBrapiEsgotada;
import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.CotacaoAtualEntity;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioAtivoMonitorado;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCotacaoAtual;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtualizacaoCache;
import br.com.miranda.gestor.ativos.brutos.service.orcamento.ModoColeta;
import br.com.miranda.gestor.ativos.brutos.service.orcamento.OrcamentoBrapi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ServicoColetaIntradiariaTest {

    private static final ZoneId BRASIL = ZoneId.of("America/Sao_Paulo");

    private final ServicoAtualizacaoCache cache = mock(ServicoAtualizacaoCache.class);
    private final SeletorDeColetaBrapi seletor = mock(SeletorDeColetaBrapi.class);
    private final OrcamentoBrapi orcamento = mock(OrcamentoBrapi.class);
    private final RepositorioAtivoMonitorado ativos = mock(RepositorioAtivoMonitorado.class);
    private final RepositorioCotacaoAtual cotacoes = mock(RepositorioCotacaoAtual.class);
    private final List<AtivoMonitoradoEntity> favoritos = List.of(new AtivoMonitoradoEntity());
    private ZonedDateTime agora;

    private ServicoColetaIntradiaria servico(int hora, int minuto) {
        agora = ZonedDateTime.of(2026, 9, 28, hora, minuto, 0, 0, BRASIL);
        when(seletor.pregaoAberto()).thenReturn(true);
        when(seletor.habilitada()).thenReturn(true);
        when(seletor.elegiveis(any())).thenReturn(favoritos);
        return new ServicoColetaIntradiaria(cache, seletor, orcamento, ativos, cotacoes,
                Clock.fixed(agora.toInstant(), BRASIL));
    }

    /** Cotacao gravada ha `minutos`, no fuso em que o gestor grava (o do sistema). */
    private void cotacaoBuscadaHa(String simbolo, int minutos) {
        CotacaoAtualEntity cotacao = new CotacaoAtualEntity();
        cotacao.setAtualizadoEm(LocalDateTime.ofInstant(agora.toInstant(), ZoneId.systemDefault()).minusMinutes(minutos));
        when(cotacoes.findBySimbolo(simbolo)).thenReturn(Optional.of(cotacao));
    }

    @Test
    void ciclo_no_modo_normal_coleta_os_favoritos() {
        ServicoColetaIntradiaria s = servico(10, 35);
        when(orcamento.modoPara(any(), eq(1))).thenReturn(ModoColeta.A_CADA_30_MIN);
        s.executarCiclo();
        verify(cache).coletarCotacoes(favoritos);
    }

    @Test
    void modo_60_min_pula_o_ciclo_das_35() {
        ServicoColetaIntradiaria s = servico(10, 35);
        when(orcamento.modoPara(any(), anyInt())).thenReturn(ModoColeta.A_CADA_60_MIN);
        s.executarCiclo();
        verify(cache, never()).coletarCotacoes(any());
    }

    @Test
    void pregao_fechado_nao_consulta_nem_o_orcamento() {
        ServicoColetaIntradiaria s = servico(10, 5);
        when(seletor.pregaoAberto()).thenReturn(false);
        s.executarCiclo();
        verifyNoInteractions(cache, orcamento);
    }

    @Test
    void erro_429_interrompe_e_registra_brapi_orcamento() {
        ServicoColetaIntradiaria s = servico(11, 5);
        when(orcamento.modoPara(any(), anyInt())).thenReturn(ModoColeta.A_CADA_30_MIN);
        doThrow(new ExcecaoCotaBrapiEsgotada("quote/RAIZ4", null)).when(cache).coletarCotacoes(any());
        s.executarCiclo();
        verify(orcamento).registrarCotaEsgotada(any(), anyString());
    }

    @Test
    void favoritar_sem_cota_nao_chama_a_brapi() {
        ServicoColetaIntradiaria s = servico(12, 0);
        when(orcamento.cabeChamadaAvulsa(any())).thenReturn(false);
        s.coletarAoFavoritar("WEGE3");
        verifyNoInteractions(cache);
    }

    @Test
    void favoritar_coleta_cotacao_e_historico_inicial() {
        ServicoColetaIntradiaria s = servico(12, 0);
        when(orcamento.cabeChamadaAvulsa(any())).thenReturn(true);
        s.coletarAoFavoritar("WEGE3");
        // Historico antes da cotacao: a cotacao grava o candle do dia e o
        // historico inicial so roda para ativo sem candle nenhum.
        var ordem = inOrder(cache);
        ordem.verify(cache).preencherHistoricoInicial("WEGE3");
        ordem.verify(cache).coletarCotacao("WEGE3");
    }

    @Test
    void refavoritar_com_cotacao_de_menos_de_30_min_nao_chama_a_brapi() {
        ServicoColetaIntradiaria s = servico(12, 0);
        when(orcamento.cabeChamadaAvulsa(any())).thenReturn(true);
        cotacaoBuscadaHa("WEGE3", 10);
        s.coletarAoFavoritar("WEGE3");
        verify(cache).preencherHistoricoInicial("WEGE3");
        verify(cache, never()).coletarCotacao(anyString());
    }

    @Test
    void refavoritar_com_cotacao_de_30_min_ou_mais_busca_de_novo() {
        ServicoColetaIntradiaria s = servico(12, 0);
        when(orcamento.cabeChamadaAvulsa(any())).thenReturn(true);
        cotacaoBuscadaHa("WEGE3", 30);
        s.coletarAoFavoritar("WEGE3");
        verify(cache).coletarCotacao("WEGE3");
    }
}
