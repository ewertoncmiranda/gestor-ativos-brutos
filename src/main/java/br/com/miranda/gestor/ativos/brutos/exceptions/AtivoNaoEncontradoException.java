package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

public class AtivoNaoEncontradoException extends ApplicationRuntimeException {

    public AtivoNaoEncontradoException(String simbolo) {
        super(
                "ATIVO_NAO_ENCONTRADO",
                "Nenhum dado encontrado para o ativo: " + simbolo,
                HttpStatus.NOT_FOUND
        );
    }
}
