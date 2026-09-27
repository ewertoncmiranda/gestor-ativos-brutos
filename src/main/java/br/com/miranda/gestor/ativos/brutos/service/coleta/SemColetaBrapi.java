package br.com.miranda.gestor.ativos.brutos.service.coleta;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Camada Base / referencia de setor: preco diario oficial do COTAHIST, que o
 * ETL carrega toda noite. Nenhuma chamada a BRAPI.
 */
public class SemColetaBrapi implements PoliticaDeColeta {

    @Override
    public boolean usaBrapi() {
        return false;
    }

    @Override
    public boolean devida(AtivoMonitoradoEntity ativo, LocalDateTime ultimaAtualizacao, ZonedDateTime agora) {
        return false;
    }
}
