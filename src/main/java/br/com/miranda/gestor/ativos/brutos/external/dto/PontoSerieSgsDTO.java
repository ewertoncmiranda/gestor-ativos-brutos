package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Um ponto da serie SGS do Banco Central: {"data":"25/09/2026","valor":"14.75"}.
 * A API devolve os dois campos como string, mesmo o valor numerico.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PontoSerieSgsDTO(
        @JsonProperty("data") String data,
        @JsonProperty("valor") BigDecimal valor
) {
}
