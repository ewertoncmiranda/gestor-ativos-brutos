package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

public class ExcecaoFilaIndisponivelBase extends ExcecaoAplicacao {

    public ExcecaoFilaIndisponivelBase(String queueUrl, Throwable cause) {
        super(
                "FILA_UNAVAILABLE",
                "Fila indisponivel para envio de mensagem: " + queueUrl,
                HttpStatus.SERVICE_UNAVAILABLE,
                cause
        );
    }
}
