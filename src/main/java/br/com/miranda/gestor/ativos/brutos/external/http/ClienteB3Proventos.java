package br.com.miranda.gestor.ativos.brutos.external.http;

import br.com.miranda.gestor.ativos.brutos.external.dto.ProventoB3DTO;
import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaSuplementoEmpresaB3DTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Cliente do endpoint (nao-documentado, mas publico, sem chave) da B3 que
 * serve a aba "Proventos em Dinheiro" da pagina de cada empresa listada -
 * confirmado ao vivo em 27/09/2026 navegando o proprio site da B3 e
 * capturando a chamada real (GetListedSupplementCompany), nao adivinhado.
 *
 * Limitacao conhecida: a B3 so devolve os proventos aprovados nos ~12 meses
 * anteriores a CADA coleta (janela movel, nao um corte fixo - confirmado: a
 * primeira coleta em 27/09/2026 trouxe eventos desde 26/09/2025), ou o
 * ultimo, se mais antigo que isso - nao serve pra backfill historico de anos
 * anteriores sozinho. Rodado periodicamente (mesmo espirito do diario de
 * sinais), a janela vai andando pra frente a cada coleta.
 */
@Slf4j
@Service
public class ClienteB3Proventos {

    private static final String BASE_URL = "https://sistemaswebb3-listados.b3.com.br";
    private static final String CAMINHO =
            "/listedCompaniesProxy/CompanyCall/GetListedSupplementCompany/{payload}";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public ClienteB3Proventos(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param codigoEmissor codigo de 4 letras da B3 (ex.: "PETR" para PETR4/PETR3,
     *                       nao o ticker com o digito da especie).
     */
    public List<ProventoB3DTO> consultarProventos(String codigoEmissor) {
        String json = "{\"issuingCompany\":\"" + codigoEmissor + "\",\"language\":\"pt-br\"}";
        String payload = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String url = BASE_URL + CAMINHO.replace("{payload}", payload);

        try {
            ResponseEntity<String> resposta = restTemplate.getForEntity(url, String.class);
            String corpo = resposta.getBody();
            if (!StringUtils.hasText(corpo) || "[]".equals(corpo.trim())) {
                return List.of();
            }
            List<RespostaSuplementoEmpresaB3DTO> emissores = objectMapper.readValue(
                    corpo, objectMapper.getTypeFactory().constructCollectionType(List.class, RespostaSuplementoEmpresaB3DTO.class));
            if (emissores.isEmpty() || emissores.getFirst().cashDividends() == null) {
                return List.of();
            }
            return emissores.getFirst().cashDividends();
        } catch (HttpStatusCodeException e) {
            log.warn("(B3-PROVENTOS)-Erro HTTP ao consultar proventos de {}: {}", codigoEmissor, e.getStatusCode());
            return List.of();
        } catch (JsonProcessingException e) {
            log.warn("(B3-PROVENTOS)-Resposta JSON invalida para {}: {}", codigoEmissor, e.getMessage());
            return List.of();
        }
    }
}
