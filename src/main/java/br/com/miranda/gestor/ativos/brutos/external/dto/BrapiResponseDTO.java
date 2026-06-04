package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrapiResponseDTO {

    @JsonProperty("results")
    private List<BrapiAtivoDTO> results;

    @JsonProperty("requestedAt")
    private String requestedAt;

    @JsonProperty("took")
    private String took;
}
