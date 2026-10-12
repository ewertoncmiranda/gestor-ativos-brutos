package br.com.miranda.gestor.ativos.brutos.external.http;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoInsiderIaIndisponivel;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsiderIaAnalisarRequisicaoDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsiderIaAnaliseRespostaDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.InsiderIaResultadoItemDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Cliente HTTP para o serviço insider-ia.
 * Chama POST /simulador/analisar e devolve a análise gerada pelo modelo.
 * Lança ExcecaoInsiderIaIndisponivel quando o serviço não está acessível —
 * o ServicoSimuladorIa captura e degrada para STATUS_INDISPONIVEL.
 */
@Slf4j
@Service
public class ClienteInsiderIa {

    private static final String CAMINHO_ANALISAR = "/simulador/analisar";
    private static final String CLIENTE = "(INSIDER-IA-CLIENT)";

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl;

    public ClienteInsiderIa(@Value("${insider.ia.url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Envia simbolo e resultados ao insider-ia e retorna a análise gerada.
     *
     * @param simbolo   código do ativo (ex.: PETR4)
     * @param resultados itens de estratégia para o pregão mais recente
     * @return resposta com analise, modelo e tokens_usados
     * @throws ExcecaoInsiderIaIndisponivel se o serviço estiver offline ou retornar 5xx
     */
    public InsiderIaAnaliseRespostaDTO analisarSimulador(String simbolo,
                                                         List<InsiderIaResultadoItemDTO> resultados) {
        log.info("{} - Chamando POST /simulador/analisar para simbolo={}", CLIENTE, simbolo);

        InsiderIaAnalisarRequisicaoDTO requisicao = InsiderIaAnalisarRequisicaoDTO.builder()
                .simbolo(simbolo)
                .resultados(resultados)
                .build();

        String url = baseUrl + CAMINHO_ANALISAR;

        try {
            ResponseEntity<InsiderIaAnaliseRespostaDTO> resposta =
                    restTemplate.postForEntity(url, requisicao, InsiderIaAnaliseRespostaDTO.class);

            InsiderIaAnaliseRespostaDTO corpo = resposta.getBody();
            if (corpo == null) {
                throw new ExcecaoInsiderIaIndisponivel(simbolo, "resposta vazia do insider-ia", null);
            }

            log.info("{} - Análise recebida para simbolo={} modelo={}", CLIENTE, simbolo, corpo.getModelo());
            return corpo;

        } catch (ResourceAccessException e) {
            throw new ExcecaoInsiderIaIndisponivel(simbolo, "serviço inacessível: " + e.getMessage(), e);
        } catch (HttpStatusCodeException e) {
            throw new ExcecaoInsiderIaIndisponivel(simbolo, "HTTP " + e.getStatusCode(), e);
        }
    }
}
