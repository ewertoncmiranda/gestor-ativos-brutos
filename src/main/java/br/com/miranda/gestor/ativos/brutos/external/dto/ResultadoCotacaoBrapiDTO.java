package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResultadoCotacaoBrapiDTO {

    @JsonProperty("requestedSymbol")
    private String requestedSymbol;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("data")
    private AtivoBrapiDTO data;
}
