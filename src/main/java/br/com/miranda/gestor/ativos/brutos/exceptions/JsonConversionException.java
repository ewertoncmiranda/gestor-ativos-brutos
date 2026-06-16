package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

public class JsonConversionException extends ApplicationRuntimeException {

    public JsonConversionException(String detail, Throwable cause) {
        super(
                "JSON_CONVERSION_ERROR",
                "Falha ao converter JSON: " + detail,
                HttpStatus.INTERNAL_SERVER_ERROR,
                cause
        );
    }
}
