package br.com.miranda.gestor.ativos.brutos.entrypoint.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgendadorCacheAtivosTest {

    private static final ZoneId BRASIL = ZoneId.of(AgendadorCacheAtivos.ZONA);

    private static List<LocalTime> disparosNoDia(String cron, ZonedDateTime dia) {
        CronExpression expressao = CronExpression.parse(cron);
        List<LocalTime> horas = new ArrayList<>();
        ZonedDateTime cursor = dia.withHour(0).withMinute(0);
        ZonedDateTime fim = cursor.plusDays(1);
        while ((cursor = expressao.next(cursor)) != null && cursor.isBefore(fim)) {
            horas.add(cursor.toLocalTime());
        }
        return horas;
    }

    @Test
    void intradiario_tem_16_ciclos_de_10h05_a_17h35_so_em_dia_util() {
        ZonedDateTime segunda = ZonedDateTime.of(2026, 9, 28, 0, 0, 0, 0, BRASIL);
        List<LocalTime> ciclos = disparosNoDia(AgendadorCacheAtivos.CRON_INTRADIARIO, segunda);
        assertEquals(16, ciclos.size());
        assertEquals(LocalTime.of(10, 5), ciclos.getFirst());
        assertEquals(LocalTime.of(17, 35), ciclos.getLast());
        assertTrue(disparosNoDia(AgendadorCacheAtivos.CRON_INTRADIARIO, segunda.minusDays(1)).isEmpty());
    }

    @Test
    void manha_e_foto_uma_vez_por_dia_util() {
        ZonedDateTime segunda = ZonedDateTime.of(2026, 9, 28, 0, 0, 0, 0, BRASIL);
        assertEquals(List.of(LocalTime.of(8, 30)), disparosNoDia(AgendadorCacheAtivos.CRON_MANHA, segunda));
        assertEquals(List.of(LocalTime.of(17, 40)), disparosNoDia(AgendadorCacheAtivos.CRON_FOTO_FECHAMENTO, segunda));
        assertTrue(disparosNoDia(AgendadorCacheAtivos.CRON_MANHA, segunda.minusDays(2)).isEmpty());
    }
}
