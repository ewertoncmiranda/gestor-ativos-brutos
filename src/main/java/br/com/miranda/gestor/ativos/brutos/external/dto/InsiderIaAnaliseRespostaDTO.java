package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Resposta de POST /simulador/analisar do insider-ia (SIM-IA-1).
 */
@Data
public class InsiderIaAnaliseRespostaDTO {

    @JsonProperty("analise")
    private String analise;

    @JsonProperty("modelo")
    private String modelo;

    @JsonProperty("origem")
    private String origem;

    @JsonProperty("tokens_usados")
    private Integer tokensUsados;

    @JsonProperty("em_cache")
    private Boolean emCache;
}
