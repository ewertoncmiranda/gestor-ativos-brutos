package br.com.miranda.gestor.ativos.brutos.service.coleta;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Decide se um ativo deve ser consultado na BRAPI agora (Strategy: uma
 * implementacao por camada de monitoramento, escolhida pelo tipo de coleta).
 * Camada nova = implementacao nova, sem mexer em quem consulta a BRAPI.
 */
public interface PoliticaDeColeta {

    /** Se a camada consulta a BRAPI em algum momento. */
    boolean usaBrapi();

    /**
     * @param ultimaAtualizacao ultima vez que o dado do ativo foi gravado; null se nunca
     */
    boolean devida(AtivoMonitoradoEntity ativo, LocalDateTime ultimaAtualizacao, ZonedDateTime agora);
}
