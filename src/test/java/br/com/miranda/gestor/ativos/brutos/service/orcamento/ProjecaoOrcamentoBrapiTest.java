package br.com.miranda.gestor.ativos.brutos.service.orcamento;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProjecaoOrcamentoBrapiTest {

    private static final ZoneId BRASIL = ZoneId.of("America/Sao_Paulo");
    // Quinta, 01/10/2026: 22 dias uteis no mes, 21 depois de hoje.
    private static final ZonedDateTime PRIMEIRO_CICLO_DO_MES = ZonedDateTime.of(2026, 10, 1, 10, 5, 0, 0, BRASIL);

    @Test
    void ciclos_do_dia_contam_a_partir_do_atual() {
        assertEquals(16, ProjecaoOrcamentoBrapi.ciclosRestantesHoje(ModoColeta.A_CADA_30_MIN, PRIMEIRO_CICLO_DO_MES));
        assertEquals(8, ProjecaoOrcamentoBrapi.ciclosRestantesHoje(ModoColeta.A_CADA_60_MIN, PRIMEIRO_CICLO_DO_MES));
        assertEquals(1, ProjecaoOrcamentoBrapi.ciclosRestantesHoje(ModoColeta.A_CADA_30_MIN, PRIMEIRO_CICLO_DO_MES.withHour(17).withMinute(35)));
        assertEquals(0, ProjecaoOrcamentoBrapi.ciclosRestantesHoje(ModoColeta.A_CADA_30_MIN, PRIMEIRO_CICLO_DO_MES.withHour(18)));
        assertEquals(0, ProjecaoOrcamentoBrapi.ciclosRestantesHoje(ModoColeta.A_CADA_30_MIN,
                ZonedDateTime.of(2026, 10, 3, 11, 5, 0, 0, BRASIL))); // sabado
    }

    @Test
    void dias_uteis_ate_o_fim_do_mes() {
        assertEquals(21, ProjecaoOrcamentoBrapi.diasUteisDepoisDe(LocalDate.of(2026, 10, 1)));
        assertEquals(0, ProjecaoOrcamentoBrapi.diasUteisDepoisDe(LocalDate.of(2026, 10, 30)));
    }

    @Test
    void trinta_e_cinco_favoritos_cabem_a_cada_30_min_num_mes_de_22_pregoes() {
        // 35 x 16 x 22 = 12.320 <= 13.500
        assertEquals(ModoColeta.A_CADA_30_MIN, ProjecaoOrcamentoBrapi.modo(0, 35, 13_500, PRIMEIRO_CICLO_DO_MES));
    }

    @Test
    void consumo_alto_degrada_para_60_min_e_depois_desliga() {
        // 30 min: 5.000 + 35x352 = 17.320 > 13.500; 60 min: 5.000 + 35x176 = 11.160 cabe.
        assertEquals(ModoColeta.A_CADA_60_MIN, ProjecaoOrcamentoBrapi.modo(5_000, 35, 13_500, PRIMEIRO_CICLO_DO_MES));
        assertEquals(ModoColeta.DESLIGADO, ProjecaoOrcamentoBrapi.modo(10_000, 35, 13_500, PRIMEIRO_CICLO_DO_MES));
        assertEquals(ModoColeta.DESLIGADO, ProjecaoOrcamentoBrapi.modo(13_500, 1, 13_500, PRIMEIRO_CICLO_DO_MES.withDayOfMonth(30)));
    }

    @Test
    void sem_favoritos_nao_ha_o_que_projetar() {
        assertEquals(ModoColeta.A_CADA_30_MIN, ProjecaoOrcamentoBrapi.modo(20_000, 0, 13_500, PRIMEIRO_CICLO_DO_MES));
    }

    @Test
    void modo_60_min_so_roda_nas_05() {
        assertTrue(ModoColeta.A_CADA_60_MIN.rodaNoMinuto(5));
        assertFalse(ModoColeta.A_CADA_60_MIN.rodaNoMinuto(35));
        assertTrue(ModoColeta.A_CADA_30_MIN.rodaNoMinuto(35));
        assertFalse(ModoColeta.DESLIGADO.rodaNoMinuto(5));
    }
}
