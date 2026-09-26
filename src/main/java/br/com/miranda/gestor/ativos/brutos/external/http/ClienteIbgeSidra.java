package br.com.miranda.gestor.ativos.brutos.external.http;

import br.com.miranda.gestor.ativos.brutos.external.dto.RespostaSidraDTO;
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
import java.util.Map;

/**
 * Cliente da API de agregados do IBGE (SIDRA): series estatisticas oficiais
 * (desemprego, PIB, produção industrial...), publica, sem chave, sem custo -
 * mesmo espirito do ClienteBancoCentral, fonte diferente.
 *
 * So cobre o caso simples (uma variavel, nivel territorial Brasil, sem
 * quebra por classificacao) - tabelas com dimensao de classificacao (ex.:
 * Producao Fisica Industrial por atividade) exigem outro parametro na URL e
 * ficam fora deste cliente por ora.
 */
@Slf4j
@Service
public class ClienteIbgeSidra {

    private static final String BASE_URL = "https://servicodados.ibge.gov.br";
    private static final String CAMINHO_SERIE = "/api/v3/agregados/{idAgregado}/periodos/-{ultimosN}/variaveis/{idVariavel}";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public ClienteIbgeSidra(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Consulta os ultimos N pontos de uma variavel, nivel territorial Brasil
     * (N1). Devolve periodo -> valor (ambos como vieram da API, sem
     * conversao) - quem interpreta o formato do periodo e o servico chamador,
     * que sabe se a tabela e mensal ou anual.
     */
    public Map<String, String> consultarSerie(int idAgregado, int idVariavel, int ultimosN) {
        log.info("(IBGE-SIDRA)-Iniciando consulta ao agregado {} variavel {}", idAgregado, idVariavel);

        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .path(CAMINHO_SERIE)
                .queryParam("localidades", "N1[all]")
                .buildAndExpand(idAgregado, ultimosN, idVariavel)
                .toUriString();

        try {
            ResponseEntity<String> resposta = restTemplate.getForEntity(url, String.class);
            String corpo = resposta.getBody();
            if (!StringUtils.hasText(corpo)) {
                throw new IllegalStateException("Resposta vazia do agregado SIDRA " + idAgregado);
            }

            List<RespostaSidraDTO> variaveis = objectMapper.readValue(
                    corpo, objectMapper.getTypeFactory().constructCollectionType(List.class, RespostaSidraDTO.class));

            return extrairSerie(variaveis, idAgregado);
        } catch (HttpStatusCodeException e) {
            log.error("(IBGE-SIDRA)-Erro HTTP ao consultar agregado {}: {}", idAgregado, e.getStatusCode());
            throw new IllegalStateException("Falha ao consultar agregado SIDRA " + idAgregado + ": HTTP " + e.getStatusCode(), e);
        } catch (JsonProcessingException e) {
            log.error("(IBGE-SIDRA)-Erro ao processar JSON do agregado {}", idAgregado, e);
            throw new IllegalStateException("Resposta JSON invalida do agregado SIDRA " + idAgregado, e);
        }
    }

    private Map<String, String> extrairSerie(List<RespostaSidraDTO> variaveis, int idAgregado) {
        if (variaveis.isEmpty() || variaveis.getFirst().resultados() == null || variaveis.getFirst().resultados().isEmpty()) {
            throw new IllegalStateException("Agregado SIDRA " + idAgregado + " sem resultados (localidade/variavel podem ter mudado)");
        }
        var resultado = variaveis.getFirst().resultados().getFirst();
        if (resultado.series() == null || resultado.series().isEmpty()) {
            throw new IllegalStateException("Agregado SIDRA " + idAgregado + " sem series (localidade/variavel podem ter mudado)");
        }
        return resultado.series().getFirst().serie();
    }
}
