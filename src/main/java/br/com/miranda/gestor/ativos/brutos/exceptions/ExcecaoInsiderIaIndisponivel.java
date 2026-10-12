package br.com.miranda.gestor.ativos.brutos.exceptions;

/**
 * Lançada pelo ClienteInsiderIa quando o serviço insider-ia não está acessível
 * (ConnectException, timeout, HTTP 5xx).
 * O ServicoSimuladorIa captura esta exceção e retorna STATUS_INDISPONIVEL —
 * nunca propaga para o controller.
 */
public class ExcecaoInsiderIaIndisponivel extends RuntimeException {

    public ExcecaoInsiderIaIndisponivel(String simbolo, String detalhe, Throwable cause) {
        super("insider-ia indisponível para " + simbolo + ": " + detalhe, cause);
    }
}
