package br.com.miranda.gestor.ativos.brutos.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClienteFocusBcbTest {

    @Test
    void montaOFiltroODataEConverteAsLinhas() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer bcb = MockRestServiceServer.bindTo(rest).build();
        bcb.expect(requestTo(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith(ClienteFocusBcb.URL_ANUAIS + "?$format=json&$top=10"),
                        org.hamcrest.Matchers.containsString("baseCalculo%20eq%200"),
                        org.hamcrest.Matchers.containsString("DataReferencia%20eq%20%272026%27%20or%20DataReferencia%20eq%20%272027%27"))))
                .andRespond(withSuccess("""
                        {"value":[{"Indicador":"Selic","Data":"2026-10-16","DataReferencia":"2027","Mediana":12.0,"baseCalculo":0}]}""",
                        MediaType.APPLICATION_JSON));

        var linhas = new ClienteFocusBcb(rest, new ObjectMapper()).consultarAnuais("Selic", List.of(2026, 2027), 5);

        assertEquals(1, linhas.size());
        assertEquals("2027", linhas.get(0).dataReferencia());
        assertEquals(0, new BigDecimal("12.0").compareTo(linhas.get(0).mediana()));
        bcb.verify();
    }

    @Test
    void erroDoBcbViraIllegalState() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer.bindTo(rest).build()
                .expect(requestTo(org.hamcrest.Matchers.startsWith(ClienteFocusBcb.URL_ANUAIS)))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        var cliente = new ClienteFocusBcb(rest, new ObjectMapper());
        assertThrows(IllegalStateException.class, () -> cliente.consultarAnuais("IPCA", List.of(2026), 5));
    }
}
