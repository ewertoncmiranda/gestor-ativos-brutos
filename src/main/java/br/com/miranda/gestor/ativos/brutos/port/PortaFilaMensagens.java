package br.com.miranda.gestor.ativos.brutos.port;

public interface PortaFilaMensagens {

    /**
     * Publica uma mensagem serializada na fila configurada.
     */
    String enviarMensagemParaFila(String mensagem);

    /**
     * Publica uma mensagem serializada em uma fila especifica.
     */
    String enviarMensagemParaFila(String mensagem, String queueUrl);
}
