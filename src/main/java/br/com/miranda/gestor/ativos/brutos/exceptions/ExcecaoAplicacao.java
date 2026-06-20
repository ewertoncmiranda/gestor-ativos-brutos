package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

public class ExcecaoAplicacao extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ExcecaoAplicacao(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ExcecaoAplicacao(String code, String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
