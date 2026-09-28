package br.com.miranda.gestor.ativos.brutos.service.orcamento;

import br.com.miranda.gestor.ativos.brutos.repository.RepositorioExecucaoEtl;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrcamentoBrapiTest {

    private static final ZonedDateTime AGORA = ZonedDateTime.of(2026, 10, 1, 10, 5, 0, 0, ZoneId.of("America/Sao_Paulo"));

    private final RegistroConsumoBrapi consumo = mock(RegistroConsumoBrapi.class);
    private final RepositorioExecucaoEtl execucoes = mock(RepositorioExecucaoEtl.class);
    private final OrcamentoBrapi orcamento = new OrcamentoBrapi(consumo, execucoes, 13_500);

    @Test
    void le_o_consumo_do_mes_para_escolher_o_modo() {
        when(consumo.consumidoNoMes(YearMonth.of(2026, 10))).thenReturn(5_000L);
        assertEquals(ModoColeta.A_CADA_60_MIN, orcamento.modoPara(AGORA, 35));
    }

    @Test
    void cota_esgotada_vira_uma_linha_de_erro_por_dia() {
        orcamento.registrarCotaEsgotada(AGORA, "HTTP 429");
        orcamento.registrarCotaEsgotada(AGORA.plusMinutes(30), "HTTP 429");
        verify(execucoes, times(1)).registrarErro(eq("BRAPI_ORCAMENTO"), eq("2026-10"), eq("brapi"), anyString());
        orcamento.registrarCotaEsgotada(AGORA.plusDays(1), "HTTP 429");
        verify(execucoes, times(2)).registrarErro(eq("BRAPI_ORCAMENTO"), eq("2026-10"), eq("brapi"), anyString());
    }

    @Test
    void chamada_avulsa_so_com_saldo() {
        when(consumo.consumidoNoMes(any())).thenReturn(13_499L, 13_500L);
        assertTrue(orcamento.cabeChamadaAvulsa(AGORA));
        assertFalse(orcamento.cabeChamadaAvulsa(AGORA));
    }
}
