package br.com.miranda.gestor.ativos.brutos.service.coleta;

/**
 * Camada Favoritos: cotacao intradiaria da BRAPI nos ciclos das :05 e :35,
 * 10h-17h, em dia de pregao (AgendadorCacheAtivos + ServicoColetaIntradiaria).
 * 35 favoritos x 16 ciclos x 23 pregoes cabe no orcamento de 13.500/mes.
 */
public class ColetaIntradiariaNoPregao implements PoliticaDeColeta {

    @Override
    public boolean usaBrapi() {
        return true;
    }
}
