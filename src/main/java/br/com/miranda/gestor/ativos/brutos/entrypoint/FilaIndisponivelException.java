package br.com.miranda.gestor.ativos.brutos.entrypoint;

public class FilaIndisponivelException extends RuntimeException{
    public FilaIndisponivelException(String message, Exception e) {
        super(message,e);
    }
}
