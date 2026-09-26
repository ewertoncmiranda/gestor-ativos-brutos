package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PerfilEmpresaBrapiDTO {

    @JsonProperty("sector")
    private String sector;

    @JsonProperty("industry")
    private String industry;

    @JsonProperty("longBusinessSummary")
    private String longBusinessSummary;

    @JsonProperty("website")
    private String website;

    @JsonProperty("cnpj")
    private String cnpj;

    @JsonProperty("fullTimeEmployees")
    private Integer fullTimeEmployees;

    @JsonProperty("city")
    private String city;

    @JsonProperty("state")
    private String state;
}
