package br.com.miranda.gestor.ativos.brutos.port;

public interface QueueConnectPort {
    String enviarMensagemParaFila(String mensagem);
}
