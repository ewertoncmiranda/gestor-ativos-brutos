package br.com.miranda.gestor.ativos.brutos.entrypoint;

import br.com.miranda.gestor.ativos.brutos.exceptions.QueueUnavailableException;

public class FilaIndisponivelException extends QueueUnavailableException {

    public FilaIndisponivelException(String queueUrl, Exception e) {
        super(queueUrl, e);
    }
}
