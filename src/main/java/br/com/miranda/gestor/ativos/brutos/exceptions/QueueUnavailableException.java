package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

public class QueueUnavailableException extends ApplicationRuntimeException {

    public QueueUnavailableException(String queueUrl, Throwable cause) {
        super(
                "QUEUE_UNAVAILABLE",
                "Fila indisponivel para envio de mensagem: " + queueUrl,
                HttpStatus.SERVICE_UNAVAILABLE,
                cause
        );
    }
}
