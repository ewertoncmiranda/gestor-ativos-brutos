package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RespostaPerfilBrapiDTO {

    @JsonProperty("results")
    private List<PerfilResultadoBrapiDTO> results;

    @JsonProperty("requestedAt")
    private String requestedAt;
}
