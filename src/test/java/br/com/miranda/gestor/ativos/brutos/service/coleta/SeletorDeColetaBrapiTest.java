package br.com.miranda.gestor.ativos.brutos.service.coleta;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.TipoColeta;
import br.com.miranda.gestor.ativos.brutos.external.http.ClienteBrApi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeletorDeColetaBrapiTest {

    private static final ZoneId BRASIL = ZoneId.of("America/Sao_Paulo");
    // Segunda-feira, 28/09/2026
    private static final ZonedDateTime SEGUNDA_11H = ZonedDateTime.of(2026, 9, 28, 11, 0, 0, 0, BRASIL);

    private static AtivoMonitoradoEntity ativo(String simbolo, TipoColeta tipo, int intervalo) {
        AtivoMonitoradoEntity a = new AtivoMonitoradoEntity();
        a.setSimbolo(simbolo);
        a.setTipoColeta(tipo);
        a.setIntervaloSegundos(intervalo);
        return a;
    }

    private static SeletorDeColetaBrapi seletor(boolean comChave, ZonedDateTime agora) {
        ClienteBrApi cliente = mock(ClienteBrApi.class);
        when(cliente.habilitado()).thenReturn(comChave);
        return new SeletorDeColetaBrapi(cliente, Clock.fixed(agora.toInstant(), BRASIL),
                SeletorDeColetaBrapi.politicasPadrao(JanelaDePregao.B3));
    }

    private final List<AtivoMonitoradoEntity> universo = List.of(
            ativo("RAIZ4", TipoColeta.COTACAO_E_HISTORICO, 30),
            ativo("PETR4", TipoColeta.REFERENCIA_DIARIA, 3600),
            ativo("VALE3", TipoColeta.COTACAO, 3600));

    @Test
    void so_favoritos_usam_a_brapi_e_referencias_nunca() {
        assertEquals(List.of("RAIZ4"), seletor(true, SEGUNDA_11H).elegiveis(universo).stream()
                .map(AtivoMonitoradoEntity::getSimbolo).toList());
    }

    @Test
    void sem_chave_da_brapi_ninguem_e_consultado() {
        SeletorDeColetaBrapi semChave = seletor(false, SEGUNDA_11H);
        assertTrue(semChave.elegiveis(universo).isEmpty());
        assertTrue(semChave.devidos(universo, Map.of()).isEmpty());
    }

    @Test
    void favorito_respeita_o_minimo_de_15_min_mesmo_cadastrado_com_30_s() {
        SeletorDeColetaBrapi s = seletor(true, SEGUNDA_11H);
        LocalDateTime agoraLocal = SEGUNDA_11H.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime ha10min = agoraLocal.minusMinutes(10);
        LocalDateTime ha16min = agoraLocal.minusMinutes(16);
        assertTrue(s.devidos(universo, Map.of("RAIZ4", ha10min)).isEmpty());
        assertEquals(1, s.devidos(universo, Map.of("RAIZ4", ha16min)).size());
    }

    @Test
    void fora_do_pregao_nada_e_consultado() {
        ZonedDateTime sabado = ZonedDateTime.of(2026, 9, 26, 11, 0, 0, 0, BRASIL);
        ZonedDateTime segunda20h = ZonedDateTime.of(2026, 9, 28, 20, 0, 0, 0, BRASIL);
        assertTrue(seletor(true, sabado).devidos(universo, Map.of()).isEmpty());
        assertTrue(seletor(true, segunda20h).devidos(universo, Map.of()).isEmpty());
        assertFalse(seletor(true, sabado).pregaoAberto());
    }

    @Test
    void janela_do_pregao_nas_bordas() {
        JanelaDePregao b3 = JanelaDePregao.B3;
        assertFalse(b3.aberta(SEGUNDA_11H.withHour(9).withMinute(59)));
        assertTrue(b3.aberta(SEGUNDA_11H.withHour(10).withMinute(0)));
        assertTrue(b3.aberta(SEGUNDA_11H.withHour(18).withMinute(29)));
        assertFalse(b3.aberta(SEGUNDA_11H.withHour(18).withMinute(30)));
    }
}
