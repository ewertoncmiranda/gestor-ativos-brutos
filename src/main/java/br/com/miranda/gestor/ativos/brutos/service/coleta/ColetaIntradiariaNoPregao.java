package br.com.miranda.gestor.ativos.brutos.service.coleta;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.tools.SelecionadorAtivosDevidos;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Camada Favoritos: cotacao intradiaria so com o pregao aberto e no maximo a
 * cada {@link #INTERVALO_MINIMO_SEGUNDOS}. 10 favoritos x 34 consultas por
 * dia util x 21 dias ~ 7 mil chamadas/mes, metade da cota gratuita da BRAPI.
 */
public class ColetaIntradiariaNoPregao implements PoliticaDeColeta {

    public static final int INTERVALO_MINIMO_SEGUNDOS = 900;

    private final JanelaDePregao janela;

    public ColetaIntradiariaNoPregao(JanelaDePregao janela) {
        this.janela = janela;
    }

    @Override
    public boolean usaBrapi() {
        return true;
    }

    @Override
    public boolean devida(AtivoMonitoradoEntity ativo, LocalDateTime ultimaAtualizacao, ZonedDateTime agora) {
        long intervalo = Math.max(ativo.getIntervaloSegundos(), INTERVALO_MINIMO_SEGUNDOS);
        // atualizadoEm e gravado com LocalDateTime.now() do proprio servico:
        // compara no mesmo fuso, com o mesmo instante usado para a janela.
        LocalDateTime agoraLocal = agora.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        return janela.aberta(agora) && SelecionadorAtivosDevidos.estaDevido(ultimaAtualizacao, intervalo, agoraLocal);
    }
}
