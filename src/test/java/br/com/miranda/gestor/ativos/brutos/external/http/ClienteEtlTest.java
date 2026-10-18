package br.com.miranda.gestor.ativos.brutos.external.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ClienteEtlTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void enviaJsonComTokenEDevolve202ComCorpo() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer etl = MockRestServiceServer.bindTo(rest).build();
        etl.expect(requestTo("http://etl:8080/cargas"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(ClienteEtl.CABECALHO_TOKEN, "segredo"))
                .andExpect(content().json("{\"tipo\":\"ROTINA\",\"solicitada_por\":\"PAINEL\"}"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"c-1\",\"status\":\"PENDENTE\"}"));

        var resposta = new ClienteEtl(rest, json, "http://etl:8080/", "segredo")
                .disparar(Map.of("tipo", "ROTINA", "solicitada_por", "PAINEL"));

        assertEquals(202, resposta.status());
        assertEquals("c-1", resposta.corpo().get("id").asText());
        etl.verify();
    }

    @Test
    void semTokenNaoMandaCabecalhoE409VoltaComoVeio() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer etl = MockRestServiceServer.bindTo(rest).build();
        etl.expect(requestTo("http://etl:8080/cargas"))
                .andExpect(headerDoesNotExist(ClienteEtl.CABECALHO_TOKEN))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"execucao_em_andamento\":\"c-9\"}"));

        var resposta = new ClienteEtl(rest, json, "http://etl:8080", "").disparar(Map.of("tipo", "ROTINA"));

        assertEquals(409, resposta.status());
        assertEquals("c-9", resposta.corpo().get("execucao_em_andamento").asText());
    }

    @Test
    void enviaCorpoComContentLengthESemChunkedParaOEtlDeVerdade() throws Exception {
        // O MockRestServiceServer nao enxerga chunked; aqui o RestTemplate real fala com um servidor HTTP.
        AtomicReference<String> contentLength = new AtomicReference<>();
        AtomicReference<String> transferEncoding = new AtomicReference<>();
        AtomicReference<byte[]> corpoRecebido = new AtomicReference<>();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/cargas", troca -> {
            contentLength.set(troca.getRequestHeaders().getFirst("Content-Length"));
            transferEncoding.set(troca.getRequestHeaders().getFirst("Transfer-Encoding"));
            corpoRecebido.set(troca.getRequestBody().readAllBytes());
            byte[] resposta = "{\"id\":\"c-1\",\"status\":\"PENDENTE\"}".getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().set("Content-Type", "application/json");
            troca.sendResponseHeaders(202, resposta.length);
            troca.getResponseBody().write(resposta);
            troca.close();
        });
        servidor.start();
        try {
            String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
            var resposta = new ClienteEtl(baseUrl, "segredo", new ObjectMapper())
                    .disparar(Map.of("tipo", "ROTINA", "solicitada_por", "PAINEL"));

            assertEquals(202, resposta.status());
            assertEquals("c-1", resposta.corpo().get("id").asText());
            assertNotNull(contentLength.get(), "requisicao sem Content-Length");
            assertTrue(Integer.parseInt(contentLength.get()) > 0);
            assertNull(transferEncoding.get(), "requisicao saiu com Transfer-Encoding: " + transferEncoding.get());
            assertEquals(corpoRecebido.get().length, Integer.parseInt(contentLength.get()));
            JsonNode corpo = json.readTree(corpoRecebido.get());
            assertEquals("ROTINA", corpo.get("tipo").asText());
            assertEquals("PAINEL", corpo.get("solicitada_por").asText());
        } finally {
            servidor.stop(0);
        }
    }

    @Test
    void etlForaDoArLancaIndisponivel() {
        var cliente = new ClienteEtl(new RestTemplate(), json, "http://127.0.0.1:1", "");
        assertThrows(ClienteEtl.EtlIndisponivel.class, () -> cliente.disparar(Map.of("tipo", "ROTINA")));
    }
}
