package br.com.miranda.gestor.ativos.brutos.service.coleta;

/**
 * Se uma camada de monitoramento consulta a BRAPI (Strategy: uma
 * implementacao por camada, escolhida pelo tipo de coleta). A cadencia nao e
 * mais por ativo: e o cron do ciclo intradiario (:05 e :35) ajustado pelo
 * orcamento de cota (OrcamentoBrapi).
 */
public interface PoliticaDeColeta {

    /** Se a camada consulta a BRAPI em algum momento. */
    boolean usaBrapi();
}
