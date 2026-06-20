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

    @Value("${aws.sqs.endpoint.base}")
    private String sqsEndpointBase;

    @Value("${aws.sqs.queue.url}")
    private String queueUrl;

    @Autowired
    private SqsClient sqsClient;

    private Integer tentativas = 0;

    /**
     * Envia o payload para a fila SQS configurada, com retentativa simples em falhas transitórias.
     */
    @Override
    public String enviarMensagemParaFila(String mensagem) {
        log.info("{}-Preparando envio de mensagem para fila: {}", FILA, queueUrl);
        log.debug("{}-Conteúdo da mensagem: {}", FILA, mensagem.substring(0, Math.min(200, mensagem.length())) + "...");

        try {
            SendMessageResponse response = sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(mensagem)
                    .build());

            log.info("{}-Mensagem enviada com sucesso. Message ID: {}", FILA, response.messageId());
            return response.toString();
        } catch (Exception e) {
            if (tentativas < 3) {
                tentativas++;
                log.warn("{}-Falha ao enviar mensagem para fila. Tentativa {}/3. Erro: {}", FILA, tentativas, e.getMessage());
                return enviarMensagemParaFila(mensagem);
            }
            log.error("{}-Erro ao enviar mensagem para fila: {}. Erro: {}", FILA, queueUrl, e.getMessage(), e);
            throw new ExcecaoFilaIndisponivel(queueUrl, e);
        }
    }
}
