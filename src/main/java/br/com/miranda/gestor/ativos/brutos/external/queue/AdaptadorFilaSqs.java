package br.com.miranda.gestor.ativos.brutos.external.queue;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoFilaIndisponivel;
import br.com.miranda.gestor.ativos.brutos.port.PortaFilaMensagens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.FILA;

@Slf4j
@Service
public class AdaptadorFilaSqs implements PortaFilaMensagens {

    @Value("${aws.sqs.queue.url}")
    private String queueUrl;

    @Autowired
    private SqsClient sqsClient;

    @Override
    public String enviarMensagemParaFila(String mensagem) {
        return enviarMensagemParaFila(mensagem, queueUrl);
    }

    @Override
    public String enviarMensagemParaFila(String mensagem, String queueUrlDestino) {
        log.info("{}-Preparando envio de mensagem para fila: {}", FILA, queueUrlDestino);
        log.debug("{}-Conteudo da mensagem: {}", FILA, mensagem.substring(0, Math.min(200, mensagem.length())) + "...");

        int tentativa = 0;
        while (true) {
            try {
                SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrlDestino)
                        .messageBody(mensagem)
                        .build());

                log.info("{}-Mensagem enviada com sucesso. Message ID: {}", FILA, response.messageId());
                return response.toString();
            } catch (Exception e) {
                tentativa++;
                if (tentativa <= 3) {
                    log.warn("{}-Falha ao enviar mensagem para fila. Tentativa {}/3. Erro: {}",
                            FILA, tentativa, e.getMessage());
                    continue;
                }

                log.error("{}-Erro ao enviar mensagem para fila: {}. Erro: {}",
                        FILA, queueUrlDestino, e.getMessage(), e);
                throw new ExcecaoFilaIndisponivel(queueUrlDestino, e);
            }
        }
    }
}
