package br.com.miranda.gestor.ativos.brutos.exceptions;

public class ExcecaoFilaIndisponivel extends ExcecaoFilaIndisponivelBase {

    public ExcecaoFilaIndisponivel(String queueUrl, Exception e) {
        super(queueUrl, e);
    }
}
