package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

/**
 * A tela pediu um dado ao vivo da BRAPI, mas o teto mensal das chamadas da
 * tela (ou o total do mes) ja foi atingido. 503 com mensagem clara em vez do
 * 500 generico: o painel segue com o que ja esta gravado (COTAHIST, cache).
 */
public class ExcecaoOrcamentoBrapiTela extends ExcecaoAplicacao {

    public ExcecaoOrcamentoBrapiTela(String recurso) {
        super(
                "BRAPI_ORCAMENTO_TELA_ESGOTADO",
                "Cota mensal da BRAPI para consultas pela tela esgotada (" + recurso
                        + "); os dados exibidos sao os ja gravados, sem consulta ao vivo ate o proximo mes.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
