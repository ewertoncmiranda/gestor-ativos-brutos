package br.com.miranda.gestor.ativos.brutos.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

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
    void etlForaDoArLancaIndisponivel() {
        var cliente = new ClienteEtl(new RestTemplate(), json, "http://127.0.0.1:1", "");
        assertThrows(ClienteEtl.EtlIndisponivel.class, () -> cliente.disparar(Map.of("tipo", "ROTINA")));
    }
}
