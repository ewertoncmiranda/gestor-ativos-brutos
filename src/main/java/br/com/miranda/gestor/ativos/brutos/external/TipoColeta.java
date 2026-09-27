package br.com.miranda.gestor.ativos.brutos.external;

/**
 * Camada de monitoramento do ativo (infra V13). A politica de cada uma fica em
 * service/coleta ({@code SeletorDeColetaBrapi}).
 */
public enum TipoColeta {
    /** Legado: tratado como {@link #REFERENCIA_DIARIA}. */
    COTACAO,
    /** Favorito do usuario: cotacao intradiaria da BRAPI no pregao, a cada 15 min. */
    COTACAO_E_HISTORICO,
    /** Referencia de setor: so o preco diario oficial (COTAHIST), sem BRAPI. */
    REFERENCIA_DIARIA
}
