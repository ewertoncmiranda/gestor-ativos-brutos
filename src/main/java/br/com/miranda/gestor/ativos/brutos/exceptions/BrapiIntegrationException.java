package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

public class BrapiIntegrationException extends ApplicationRuntimeException {

    public BrapiIntegrationException(String symbol, String detail, Throwable cause) {
        super(
                "BRAPI_INTEGRATION_ERROR",
                "Falha ao consultar BRAPI para o ativo " + symbol + ": " + detail,
                HttpStatus.BAD_GATEWAY,
                cause
        );
    }
}
