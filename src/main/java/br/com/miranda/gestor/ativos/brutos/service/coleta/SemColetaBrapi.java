package br.com.miranda.gestor.ativos.brutos.service.coleta;

/**
 * Camada Base / referencia de setor: preco diario oficial do COTAHIST, que o
 * ETL carrega toda manha. Nenhuma chamada a BRAPI.
 */
public class SemColetaBrapi implements PoliticaDeColeta {

    @Override
    public boolean usaBrapi() {
        return false;
    }
}
