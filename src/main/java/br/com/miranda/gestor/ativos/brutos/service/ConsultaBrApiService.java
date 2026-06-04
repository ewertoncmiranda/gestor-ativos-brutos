package br.com.miranda.gestor.ativos.brutos.service;

import br.com.miranda.gestor.ativos.brutos.external.dto.BrapiResponseDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesUtils.BRAPI_SERVICE;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@Slf4j
@Service
public class ConsultaBrApiService {

    @Value("${brapi.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public ConsultaBrApiService() {
        this.restTemplate = new RestTemplate();
    }

    public BrapiResponseDTO executar(String symbol) {
        log.info("{}-Iniciando consulta para symbol: {}", BRAPI_SERVICE, symbol);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<?> entity = new HttpEntity<>(headers);
        String url = "https://brapi.dev/api/quote/" + symbol;
        log.debug("{}-URL de requisição: {}", BRAPI_SERVICE, url);

        try {
            log.info("{}-Enviando requisição GET para BRAPI...", BRAPI_SERVICE);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            log.debug("{}-Status HTTP recebido: {}", BRAPI_SERVICE, response.getStatusCode());
            log.debug("{}-Tamanho da resposta: {} bytes", BRAPI_SERVICE, response.getBody().length());

            ObjectMapper mapper = new ObjectMapper();
            BrapiResponseDTO result = mapper.readValue(response.getBody(), BrapiResponseDTO.class);

            log.info("{}-Resposta parseada com sucesso. Resultados: {}",
                  BRAPI_SERVICE, result.getResults().size());

            return result;

        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("{}-Ativo não encontrado: {} (404)", BRAPI_SERVICE, symbol);
            return null;
        } catch (HttpClientErrorException ex) {
            log.error("{}-Erro HTTP ao consultar Brapi. Symbol: {}, Status: {}, Mensagem: {}",
                    BRAPI_SERVICE, symbol, ex.getStatusCode(), ex.getMessage());
            throw ex;
        } catch (JsonProcessingException e) {
            log.error("{}-Erro ao processar JSON da resposta BRAPI. Symbol: {}", BRAPI_SERVICE, symbol, e);
            throw new RuntimeException("Erro ao processar JSON da resposta da Brapi", e);
        }
    }
}
