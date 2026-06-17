package br.com.miranda.gestor.ativos.brutos.exceptions;

import org.springframework.http.HttpStatus;

public class ExternalStorageException extends ApplicationRuntimeException {

    public ExternalStorageException(String simbolo, Throwable cause) {
        super(
                "S3_STORAGE_ERROR",
                "Falha ao salvar insight no S3 para o ativo: " + simbolo,
                HttpStatus.BAD_GATEWAY,
                cause
        );
    }

    public ExternalStorageException(String action, String target, Throwable cause) {
        super(
                "S3_STORAGE_ERROR",
                action + ": " + target,
                HttpStatus.BAD_GATEWAY,
                cause
        );
    }
}
