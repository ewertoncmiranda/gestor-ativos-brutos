package br.com.miranda.gestor.ativos.brutos.external.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Cliente do servico de cargas do ETL (Plano CARGA, CARGA-GES-2): POST /cargas
 * na rede interna do compose. O ETL so registra o pedido e responde na hora
 * (202, ou 409 com a carga em andamento), por isso o timeout e curto. Status e
 * corpo voltam como vieram; quem traduz para o painel e o servico.
 */
@Slf4j
@Service
public class ClienteEtl {

    static final String CABECALHO_TOKEN = "X-ETL-Token";
    private static final String CLIENTE = "(ETL-CLIENT)";

    /** Resposta do ETL: status HTTP e corpo (pode ser null). */
    public record Resposta(int status, JsonNode corpo) {
    }

    /** ETL fora do ar ou sem resposta: o painel recebe 503 com a mensagem. */
    public static class EtlIndisponivel extends RuntimeException {
        public EtlIndisponivel(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper json;
    private final String baseUrl;
    private final String token;

    @Autowired
    public ClienteEtl(@Value("${etl.url}") String baseUrl,
                      @Value("${etl.token:}") String token,
                      ObjectMapper json) {
        this(criarRestTemplate(), json, baseUrl, token);
    }

    ClienteEtl(RestTemplate restTemplate, ObjectMapper json, String baseUrl, String token) {
        this.restTemplate = restTemplate;
        this.json = json;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token == null ? "" : token.trim();
    }

    private static RestTemplate criarRestTemplate() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3_000);
        fabrica.setReadTimeout(10_000);
        return new RestTemplate(fabrica);
    }

    public Resposta disparar(Map<String, Object> pedido) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (!token.isEmpty()) {
            cabecalhos.set(CABECALHO_TOKEN, token);
        }
        log.info("{} - POST /cargas tipo={}", CLIENTE, pedido.get("tipo"));
        try {
            ResponseEntity<String> resposta = restTemplate.exchange(baseUrl + "/cargas", HttpMethod.POST,
                    new HttpEntity<>(pedido, cabecalhos), String.class);
            return new Resposta(resposta.getStatusCode().value(), ler(resposta.getBody()));
        } catch (HttpStatusCodeException e) {
            // 409 (carga em andamento), 400, 401... seguem para o servico como vieram.
            return new Resposta(e.getStatusCode().value(), ler(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new EtlIndisponivel("ETL indisponível em " + baseUrl + ": " + e.getMessage(), e);
        }
    }

    private JsonNode ler(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return json.readTree(texto);
        } catch (Exception e) {
            return null;
        }
    }
}
