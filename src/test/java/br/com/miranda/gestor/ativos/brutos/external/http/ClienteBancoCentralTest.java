package br.com.miranda.gestor.ativos.brutos.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ClienteBancoCentralTest {
    private MockRestServiceServer server;
    private ClienteBancoCentral cliente;
    private final String url = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.21619/dados/ultimos/10?formato=json";
    @BeforeEach void preparar() {
        var template = new RestTemplate();
        server = MockRestServiceServer.bindTo(template).build();
        cliente = new ClienteBancoCentral(new ObjectMapper(), template);
    }
    @Test void recebeListaJsonComAcceptExplicito() {
        server.expect(requestTo(url)).andExpect(header("Accept", "application/json"))
                .andRespond(withSuccess("[{\"data\":\"20/10/2026\",\"valor\":\"5.63\"}]", MediaType.APPLICATION_JSON));
        assertEquals("5.63", cliente.consultarSerie(21619, 10).getFirst().valor().toPlainString());
        server.verify();
    }
    @Test void html200TransitorioRepeteERecupera() {
        server.expect(requestTo(url)).andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));
        server.expect(requestTo(url)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertTrue(cliente.consultarSerie(21619, 10).isEmpty()); server.verify();
    }
    @Test void htmlPersistenteNaoViraSerieVaziaNemJsonParseException() {
        for (int i = 0; i < 2; i++) server.expect(requestTo(url)).andRespond(withSuccess("<html>gateway</html>", MediaType.APPLICATION_JSON));
        var erro = assertThrows(IllegalStateException.class, () -> cliente.consultarSerie(21619, 10));
        assertTrue(erro.getMessage().contains("21619"));
        assertTrue(erro.getMessage().contains("não é uma lista JSON"));
        assertFalse(erro.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException);
        assertFalse(erro.getMessage().contains("<html>")); server.verify();
    }
    @Test void erro500TemUmaNovaTentativa() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(url)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertTrue(cliente.consultarSerie(21619, 10).isEmpty()); server.verify();
    }
    @Test void erro400Ou429NaoRepete() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertTrue(assertThrows(IllegalStateException.class, () -> cliente.consultarSerie(21619, 10)).getMessage().contains("429"));
        server.verify();
    }
    @Test void respostaVaziaNaoViraSucesso() {
        for (int i = 0; i < 2; i++) server.expect(requestTo(url)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> cliente.consultarSerie(21619, 10)); server.verify();
    }
    @Test void jsonTruncadoNaoViraSucesso() {
        for (int i = 0; i < 2; i++) server.expect(requestTo(url)).andRespond(withSuccess("[{", MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class, () -> cliente.consultarSerie(21619, 10)); server.verify();
    }
    @Test void consultaHistoricaPreservaJanela() {
        server.expect(requestTo("https://api.bcb.gov.br/dados/serie/bcdata.sgs.21619/dados?formato=json&dataInicial=01/01/2024&dataFinal=31/12/2024"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertTrue(cliente.consultarSeriePeriodo(21619, LocalDate.of(2024,1,1), LocalDate.of(2024,12,31)).isEmpty()); server.verify();
    }
    @Test void falhaDeRedeRepeteDeFormaLimitada() {
        server.expect(requestTo(url)).andRespond(withException(new java.io.IOException("timeout")));
        server.expect(requestTo(url)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertTrue(cliente.consultarSerie(21619, 10).isEmpty()); server.verify();
    }
}
