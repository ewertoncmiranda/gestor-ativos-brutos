package br.com.miranda.gestor.ativos.brutos.service.orcamento;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;

/**
 * Conta pura do orcamento (sem banco nem relogio): projeta o consumo ate o
 * fim do mes em cada modo e devolve o mais frequente que ainda cabe.
 *
 * Projecao conservadora: todo dia util restante conta como pregao (feriado
 * da B3 so sobra cota) e cada ciclo gasta uma requisicao por favorito.
 */
public final class ProjecaoOrcamentoBrapi {

    static final LocalTime PRIMEIRO_CICLO = LocalTime.of(10, 5);
    static final LocalTime ULTIMO_CICLO = LocalTime.of(17, 35);

    private ProjecaoOrcamentoBrapi() {
    }

    /**
     * @param consumidoNoMes requisicoes ja feitas no mes (brapi_consumo)
     * @param favoritos      favoritos ativos (uma requisicao cada por ciclo)
     * @param orcamentoMensal teto do mes (brapi.orcamento.mensal)
     * @param agora          instante do ciclo, no fuso de Sao Paulo
     */
    public static ModoColeta modo(long consumidoNoMes, int favoritos, long orcamentoMensal, ZonedDateTime agora) {
        if (favoritos <= 0) {
            return ModoColeta.A_CADA_30_MIN;
        }
        for (ModoColeta modo : new ModoColeta[]{ModoColeta.A_CADA_30_MIN, ModoColeta.A_CADA_60_MIN}) {
            long projetado = consumidoNoMes + (long) favoritos * ciclosRestantes(modo, agora);
            if (projetado <= orcamentoMensal) {
                return modo;
            }
        }
        return ModoColeta.DESLIGADO;
    }

    /** Ciclos deste modo de agora (inclusive) ate o ultimo pregao do mes. */
    static long ciclosRestantes(ModoColeta modo, ZonedDateTime agora) {
        return ciclosRestantesHoje(modo, agora) + (long) diasUteisDepoisDe(agora.toLocalDate()) * modo.ciclosPorDia();
    }

    static int ciclosRestantesHoje(ModoColeta modo, ZonedDateTime agora) {
        if (!diaUtil(agora.toLocalDate())) {
            return 0;
        }
        LocalTime hora = agora.toLocalTime().withSecond(0).withNano(0);
        int ciclos = 0;
        for (LocalTime ciclo = PRIMEIRO_CICLO; !ciclo.isAfter(ULTIMO_CICLO); ciclo = ciclo.plusMinutes(30)) {
            if (!ciclo.isBefore(hora) && modo.rodaNoMinuto(ciclo.getMinute())) {
                ciclos++;
            }
        }
        return ciclos;
    }

    static int diasUteisDepoisDe(LocalDate dia) {
        LocalDate fim = YearMonth.from(dia).atEndOfMonth();
        int dias = 0;
        for (LocalDate d = dia.plusDays(1); !d.isAfter(fim); d = d.plusDays(1)) {
            dias += diaUtil(d) ? 1 : 0;
        }
        return dias;
    }

    private static boolean diaUtil(LocalDate dia) {
        return dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
