package br.com.miranda.gestor.ativos.brutos.service.coleta;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Horario em que a cotacao intradiaria muda: dia util, das 10h as 18h30 de
 * Brasilia (pregao regular e leilao de fechamento). Fora dele a BRAPI so
 * repete o ultimo preco - consultar seria gastar cota por nada.
 *
 * <p>Feriado nao e tratado: no pior caso gasta as consultas de um dia, e o
 * preco devolvido e o mesmo.
 */
public record JanelaDePregao(LocalTime abertura, LocalTime fechamento, ZoneId zona) {

    public static final JanelaDePregao B3 =
            new JanelaDePregao(LocalTime.of(10, 0), LocalTime.of(18, 30), ZoneId.of("America/Sao_Paulo"));

    public boolean aberta(ZonedDateTime agora) {
        ZonedDateTime local = agora.withZoneSameInstant(zona);
        DayOfWeek dia = local.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime hora = local.toLocalTime();
        return !hora.isBefore(abertura) && hora.isBefore(fechamento);
    }
}
