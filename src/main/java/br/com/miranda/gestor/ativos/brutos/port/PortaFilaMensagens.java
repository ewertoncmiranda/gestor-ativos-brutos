package br.com.miranda.gestor.ativos.brutos.port;

public interface PortaFilaMensagens {

    /**
     * Publica uma mensagem serializada na fila configurada.
     */
    String enviarMensagemParaFila(String mensagem);
}
