package br.com.miranda.gestor.ativos.brutos.external.http;

import br.com.miranda.gestor.ativos.brutos.exceptions.ExcecaoIntegracaoBrapi;
import br.com.miranda.gestor.ativos.brutos.external.dto.ConsultaHistoricoAcoesDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaBrapiDTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaHistoricoAcoesDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.Optional;

import static br.com.miranda.gestor.ativos.brutos.tools.ConstantesAplicacao.BRAPI_SERVICE;

@Slf4j
@Service
public class ClienteBrApi {

    private static final String BRAPI_BASE_URL = "https://brapi.dev";
    private static final String CAMINHO_COTACAO = "/api/quote/{symbol}";
    private static final String CAMINHO_HISTORICO_ACOES = "/api/v2/stocks/historical";

    @Value("${brapi.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ClienteBrApi(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * Consulta a cotação atual de um ativo na BRAPI.
     */
    public RespostaBrapiDTO consultarCotacao(String simbolo) {
        log.info("{}-Iniciando consulta para simbolo: {}", BRAPI_SERVICE, simbolo);

        String url = UriComponentsBuilder.fromHttpUrl(BRAPI_BASE_URL)
                .path(CAMINHO_COTACAO)
                .buildAndExpand(simbolo)
                .toUriString();

        RespostaBrapiDTO resposta = executarGet(url, RespostaBrapiDTO.class, "quote/" + simbolo);
        int totalResultados = resposta == null || resposta.getResults() == null ? 0 : resposta.getResults().size();
        log.info("{}-Cotacao parseada com sucesso. Resultados: {}", BRAPI_SERVICE, totalResultados);
        return resposta;
    }

    /**
     * Consulta séries históricas OHLCV de um ou mais ativos na BRAPI.
     */
    public RespostaHistoricoAcoesDTO consultarHistorico(ConsultaHistoricoAcoesDTO filtros) {
        log.info("{}-Iniciando consulta de historico OHLCV BRAPI. Filtros: {}", BRAPI_SERVICE, filtros);

        String url = UriComponentsBuilder.fromHttpUrl(BRAPI_BASE_URL)
                .path(CAMINHO_HISTORICO_ACOES)
                .queryParam("symbols", filtros.symbols())
                .queryParamIfPresent("range", texto(filtros.range()))
                .queryParamIfPresent("interval", texto(filtros.interval()))
                .queryParamIfPresent("startDate", texto(filtros.startDate()))
                .queryParamIfPresent("endDate", texto(filtros.endDate()))
                .queryParamIfPresent("sortOrder", texto(filtros.sortOrder()))
                .toUriString();

        RespostaHistoricoAcoesDTO resposta = executarGet(url, RespostaHistoricoAcoesDTO.class, "stocks/historical");
        int totalResultados = resposta == null || resposta.results() == null ? 0 : resposta.results().size();
        log.info("{}-Historico OHLCV parseado com sucesso. Resultados: {}", BRAPI_SERVICE, totalResultados);
        return resposta;
    }

    private <T> T executarGet(String url, Class<T> tipoResposta, String nomeRecurso) {
        log.debug("{}-URL de requisicao: {}", BRAPI_SERVICE, url);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entidadeAutenticada(),
                    String.class
            );

            String corpoResposta = response.getBody();
            log.debug("{}-Status HTTP recebido: {}", BRAPI_SERVICE, response.getStatusCode());
            log.debug("{}-Tamanho da resposta: {} bytes", BRAPI_SERVICE, corpoResposta == null ? 0 : corpoResposta.length());

            if (!StringUtils.hasText(corpoResposta)) {
                throw new ExcecaoIntegracaoBrapi(nomeRecurso, "resposta vazia", null);
            }

            return objectMapper.readValue(corpoResposta, tipoResposta);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.warn("{}-Recurso nao encontrado na BRAPI: {} (404)", BRAPI_SERVICE, nomeRecurso);
                return null;
            }

            log.error("{}-Erro HTTP ao consultar BRAPI. Recurso: {}, Status: {}, Mensagem: {}",
                    BRAPI_SERVICE, nomeRecurso, ex.getStatusCode(), ex.getMessage());
            throw new ExcecaoIntegracaoBrapi(nomeRecurso, "HTTP " + ex.getStatusCode(), ex);
        } catch (JsonProcessingException e) {
            log.error("{}-Erro ao processar JSON da resposta BRAPI. Recurso: {}", BRAPI_SERVICE, nomeRecurso, e);
            throw new ExcecaoIntegracaoBrapi(nomeRecurso, "resposta JSON invalida", e);
        }
    }

    private HttpEntity<?> entidadeAutenticada() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", apiKey);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    private Optional<String> texto(String valor) {
        return Optional.ofNullable(valor).filter(StringUtils::hasText);
    }
}
