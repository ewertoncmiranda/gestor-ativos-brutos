package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Uma linha de ExpectativasMercadoAnuais do Focus (BCB):
 * {"Indicador":"Selic","Data":"2026-10-16","DataReferencia":"2027","Mediana":12.0,"baseCalculo":0}.
 * Data e a data da pesquisa; DataReferencia e o ano projetado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpectativaFocusDTO(
        @JsonProperty("Indicador") String indicador,
        @JsonProperty("Data") String data,
        @JsonProperty("DataReferencia") String dataReferencia,
        @JsonProperty("Mediana") BigDecimal mediana
) {
}
