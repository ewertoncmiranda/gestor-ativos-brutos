package br.com.miranda.gestor.ativos.brutos.exceptions.handler;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RespostaErroApi(
        OffsetDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path
) {
}
