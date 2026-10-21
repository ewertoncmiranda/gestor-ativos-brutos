package br.com.miranda.gestor.ativos.brutos.external.http;

import br.com.miranda.gestor.ativos.brutos.external.dto.ExpectativaFocusDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cliente das expectativas de mercado (Focus) na API OData aberta do BCB, sem
 * chave. Conferido ao vivo em 2026-10-20. Usa baseCalculo 0 (todos os
 * respondentes); a base 1 (so os ultimos 5 dias) duplicaria cada pesquisa.
 */
@Slf4j
@Service
public class ClienteFocusBcb {

    static final String URL_ANUAIS =
            "https://olinda.bcb.gov.br/olinda/servico/Expectativas/versao/v1/odata/ExpectativasMercadoAnuais";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ClienteFocusBcb(ObjectMapper objectMapper) {
        this(new RestTemplate(), objectMapper);
    }

    ClienteFocusBcb(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** Ultimas {@code pesquisas} medianas do indicador (ex.: "Selic", "IPCA") para os anos pedidos. */
    public List<ExpectativaFocusDTO> consultarAnuais(String indicador, List<Integer> anos, int pesquisas) {
        String filtroAnos = anos.stream().map(ano -> "DataReferencia eq '" + ano + "'")
                .collect(Collectors.joining(" or "));
        String filtro = "Indicador eq '" + indicador + "' and baseCalculo eq 0 and (" + filtroAnos + ")";
        URI uri = URI.create(URL_ANUAIS
                + "?$format=json"
                + "&$top=" + pesquisas * anos.size()
                + "&$orderby=" + codificar("Data desc")
                + "&$select=Indicador,Data,DataReferencia,Mediana"
                + "&$filter=" + codificar(filtro));
        try {
            JsonNode valores = objectMapper.readTree(restTemplate.getForObject(uri, String.class)).path("value");
            List<ExpectativaFocusDTO> linhas = new ArrayList<>();
            for (JsonNode valor : valores) {
                linhas.add(objectMapper.treeToValue(valor, ExpectativaFocusDTO.class));
            }
            log.info("(FOCUS-BCB)-{} anos {}: {} linha(s)", indicador, anos, linhas.size());
            return linhas;
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao consultar o Focus de " + indicador + ": " + e.getMessage(), e);
        }
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
