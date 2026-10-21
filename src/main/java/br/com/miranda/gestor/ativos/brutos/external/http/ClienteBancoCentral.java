package br.com.miranda.gestor.ativos.brutos.external.http;

import br.com.miranda.gestor.ativos.brutos.external.dto.PontoSerieSgsDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private static final String CAMINHO_PERIODO = "/dados/serie/bcdata.sgs.{codigo}/dados";
    private static final DateTimeFormatter FORMATO_BCB = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ClienteBancoCentral(ObjectMapper objectMapper) {
        this(objectMapper, criarRestTemplate());
    }

    ClienteBancoCentral(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate criarRestTemplate() {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(8_000);
        fabrica.setReadTimeout(20_000);
        return new RestTemplate(fabrica);
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

        return buscar(url, codigoSgs);
    }

    /**
     * Consulta um periodo fechado de uma serie SGS. O BCB limita o intervalo
     * por chamada para series diarias; quem chama deve fatiar (ano a ano
     * basta com folga).
     */
    public List<PontoSerieSgsDTO> consultarSeriePeriodo(int codigoSgs, LocalDate inicio, LocalDate fim) {
        log.info("(BCB-SERVICE)-Consultando serie SGS {} de {} a {}", codigoSgs, inicio, fim);

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .path(CAMINHO_PERIODO)
                .queryParam("formato", "json")
                .queryParam("dataInicial", inicio.format(FORMATO_BCB))
                .queryParam("dataFinal", fim.format(FORMATO_BCB))
                .buildAndExpand(codigoSgs)
                .toUriString();

        return buscar(url, codigoSgs);
    }

    private List<PontoSerieSgsDTO> buscar(String url, int codigoSgs) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        for (int tentativa = 1; tentativa <= 2; tentativa++) {
            try {
                ResponseEntity<String> resposta = restTemplate.exchange(url, HttpMethod.GET,
                        new HttpEntity<Void>(headers), String.class);
                String corpo = resposta.getBody();
                MediaType tipo = resposta.getHeaders().getContentType();
                if (!resposta.getStatusCode().is2xxSuccessful()) {
                    throw new IllegalStateException("HTTP " + resposta.getStatusCode().value());
                }
                if (!StringUtils.hasText(corpo)) {
                    throw new IllegalStateException("resposta vazia");
                }
                // Alguns intermediários devolvem páginas HTML inclusive com HTTP 200.
                // Não enviar essas páginas ao Jackson nem confundir falha com uma série vazia.
                String conteudo = corpo.stripLeading();
                if (conteudo.startsWith("\uFEFF")) conteudo = conteudo.substring(1).stripLeading();
                if (MediaType.TEXT_HTML.isCompatibleWith(tipo == null ? MediaType.APPLICATION_JSON : tipo)
                        || !conteudo.startsWith("[")) {
                    throw new IllegalStateException("resposta não é uma lista JSON (HTTP "
                            + resposta.getStatusCode().value() + ", Content-Type " + tipo + ")");
                }
                List<PontoSerieSgsDTO> pontos = objectMapper.readValue(conteudo,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PontoSerieSgsDTO.class));
                log.info("(BCB-SERVICE)-Serie SGS {} recebida. Pontos: {}", codigoSgs, pontos.size());
                return pontos;
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if (tentativa == 2 || status < 500) {
                    throw new IllegalStateException("Falha ao consultar serie SGS " + codigoSgs + ": HTTP " + status, e);
                }
                log.warn("(BCB-SERVICE)-Serie SGS {}: HTTP {}; nova tentativa limitada", codigoSgs, status);
            } catch (ResourceAccessException | JsonProcessingException | IllegalStateException e) {
                if (tentativa == 2) {
                    String motivo = e instanceof JsonProcessingException ? "lista JSON inválida"
                            : e instanceof ResourceAccessException ? "falha de conexão ou tempo limite" : e.getMessage();
                    throw new IllegalStateException("Fonte BCB indisponível para serie SGS " + codigoSgs + ": " + motivo, e);
                }
                log.warn("(BCB-SERVICE)-Resposta indisponível ou inválida da serie SGS {}; nova tentativa limitada", codigoSgs);
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Consulta SGS " + codigoSgs + " interrompida", e);
            }
        }
        throw new IllegalStateException("Consulta SGS encerrada sem resposta");
    }
}
