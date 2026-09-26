package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resposta da API de agregados do IBGE (SIDRA) pra uma variavel:
 * {@code GET /api/v3/agregados/{id}/periodos/-{n}/variaveis/{var}?localidades=N1[all]}.
 * Vem como array com 1 elemento (a variavel pedida); esse elemento tem
 * resultados, e cada resultado tem series por localidade - aqui so pedimos
 * N1 (Brasil), entao e sempre 1 resultado com 1 serie.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RespostaSidraDTO(
        @JsonProperty("id") String idVariavel,
        @JsonProperty("resultados") List<ResultadoSidraDTO> resultados
) {
}
