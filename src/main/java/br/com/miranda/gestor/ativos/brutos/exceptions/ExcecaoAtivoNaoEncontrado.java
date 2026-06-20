package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

public class ExcecaoAtivoNaoEncontrado extends ExcecaoAplicacao {

    public ExcecaoAtivoNaoEncontrado(String simbolo) {
        super(
                "ATIVO_NAO_ENCONTRADO",
                "Nenhum dado encontrado para o ativo: " + simbolo,
                HttpStatus.NOT_FOUND
        );
    }
}
