package br.com.miranda.gestor.ativos.brutos.service.coleta;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.CotacaoAtualEntity;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioCotacaoAtual;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSnapshotFechamentoBrapi;
import br.com.miranda.gestor.ativos.brutos.repository.RepositorioSnapshotFechamentoBrapi.Foto;
import br.com.miranda.gestor.ativos.brutos.service.ServicoAtivoMonitorado;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServicoSnapshotFechamentoTest {

    private static final ZoneId BRASIL = ZoneId.of("America/Sao_Paulo");
    private static final ZonedDateTime HOJE_17H40 = ZonedDateTime.of(2026, 9, 28, 17, 40, 0, 0, BRASIL);

    private final ServicoAtivoMonitorado favoritos = mock(ServicoAtivoMonitorado.class);
    private final RepositorioCotacaoAtual cotacoes = mock(RepositorioCotacaoAtual.class);
    private final RepositorioSnapshotFechamentoBrapi fotos = mock(RepositorioSnapshotFechamentoBrapi.class);
    private final ServicoSnapshotFechamento servico = new ServicoSnapshotFechamento(favoritos, cotacoes, fotos,
            Clock.fixed(HOJE_17H40.toInstant(), BRASIL));

    private static AtivoMonitoradoEntity favorito(String simbolo) {
        AtivoMonitoradoEntity a = new AtivoMonitoradoEntity();
        a.setSimbolo(simbolo);
        return a;
    }

    private static CotacaoAtualEntity cotacao(String preco, String regularMarketTime) {
        CotacaoAtualEntity c = new CotacaoAtualEntity();
        c.setRegularMarketPrice(new BigDecimal(preco));
        c.setRegularMarketOpen(new BigDecimal("10.00"));
        c.setRegularMarketDayHigh(new BigDecimal("11.00"));
        c.setRegularMarketDayLow(new BigDecimal("9.50"));
        c.setRegularMarketVolume(new BigDecimal("123456"));
        c.setRegularMarketTime(regularMarketTime);
        return c;
    }

    @Test
    void grava_ok_com_o_dado_do_dia_e_sem_dado_para_quem_ficou_sem_cotacao() {
        when(favoritos.listarFavoritos()).thenReturn(List.of(favorito("RAIZ4"), favorito("PRIO3")));
        // 20:35 UTC = 17:35 em Sao Paulo, ciclo das 17:35
        when(cotacoes.findBySimbolo("RAIZ4")).thenReturn(Optional.of(cotacao("1.15", "2026-09-28T20:35:00.000Z")));
        // cotacao ainda da sexta
        when(cotacoes.findBySimbolo("PRIO3")).thenReturn(Optional.of(cotacao("40.10", "2026-09-25T20:07:00.000Z")));
        when(fotos.gravar(any())).thenReturn(1);

        assertEquals(2, servico.fotografar());

        ArgumentCaptor<Foto> captor = ArgumentCaptor.forClass(Foto.class);
        verify(fotos, times(2)).gravar(captor.capture());
        Foto raiz = captor.getAllValues().get(0);
        assertEquals("OK", raiz.status());
        assertEquals(new BigDecimal("1.15"), raiz.fechamento());
        assertEquals(123456L, raiz.volume());
        assertEquals(LocalDateTime.of(2026, 9, 28, 17, 35), raiz.horarioDadoBrapi());
        Foto prio = captor.getAllValues().get(1);
        assertEquals("SEM_DADO", prio.status());
        assertNull(prio.fechamento());
    }

    @Test
    void sem_nenhuma_cotacao_do_dia_nao_grava_nada() {
        when(favoritos.listarFavoritos()).thenReturn(List.of(favorito("RAIZ4")));
        when(cotacoes.findBySimbolo("RAIZ4")).thenReturn(Optional.of(cotacao("1.15", "2026-09-25T20:35:00.000Z")));
        assertEquals(0, servico.fotografar());
        verifyNoInteractions(fotos);
    }
}
