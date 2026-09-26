package br.com.miranda.gestor.ativos.brutos.external.http;

import br.com.miranda.gestor.ativos.brutos.external.dto.PontoSerieSgsDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Cliente da API SGS do Banco Central (series historicas: Selic, CDI, IPCA
 * etc). Publica, sem chave, sem custo - ao contrario da BRAPI, aqui nao ha
 * plano nem limite de simbolos por chamada a respeitar.
 */
@Slf4j
@Service
public class ClienteBancoCentral {

    private static final String BASE_URL = "https://api.bcb.gov.br";
    private static final String CAMINHO_SERIE = "/dados/serie/bcdata.sgs.{codigo}/dados/ultimos/{n}";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public ClienteBancoCentral(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Consulta os ultimos N pontos de uma serie SGS (ex.: 432 = Selic meta,
     * 12 = CDI, 433 = IPCA mensal).
     */
    public List<PontoSerieSgsDTO> consultarSerie(int codigoSgs, int ultimosN) {
        log.info("(BCB-SERVICE)-Iniciando consulta a serie SGS {}", codigoSgs);

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .path(CAMINHO_SERIE)
                .queryParam("formato", "json")
                .buildAndExpand(codigoSgs, ultimosN)
                .toUriString();

        try {
            ResponseEntity<String> resposta = restTemplate.getForEntity(url, String.class);
            String corpo = resposta.getBody();
            if (!StringUtils.hasText(corpo)) {
                throw new IllegalStateException("Resposta vazia da serie SGS " + codigoSgs);
            }
            List<PontoSerieSgsDTO> pontos = objectMapper.readValue(corpo, objectMapper.getTypeFactory().constructCollectionType(List.class, PontoSerieSgsDTO.class));
            log.info("(BCB-SERVICE)-Serie SGS {} parseada com sucesso. Pontos: {}", codigoSgs, pontos.size());
            return pontos;
        } catch (HttpStatusCodeException e) {
            log.error("(BCB-SERVICE)-Erro HTTP ao consultar serie SGS {}: {}", codigoSgs, e.getStatusCode());
            throw new IllegalStateException("Falha ao consultar serie SGS " + codigoSgs + ": HTTP " + e.getStatusCode(), e);
        } catch (JsonProcessingException e) {
            log.error("(BCB-SERVICE)-Erro ao processar JSON da serie SGS {}", codigoSgs, e);
            throw new IllegalStateException("Resposta JSON invalida da serie SGS " + codigoSgs, e);
        }
    }
}
