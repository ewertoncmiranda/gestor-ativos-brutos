package br.com.miranda.gestor.ativos.brutos.exceptions;

/**
 * HTTP 429 da BRAPI: cota do plano esgotada ou limite de taxa. Quem esta
 * num ciclo de coleta interrompe o ciclo inteiro - insistir so gasta mais.
 */
public class ExcecaoCotaBrapiEsgotada extends ExcecaoIntegracaoBrapi {

    public ExcecaoCotaBrapiEsgotada(String recurso, Throwable causa) {
        super(recurso, "HTTP 429 (cota da BRAPI esgotada ou limite de taxa)", causa);
    }
}
