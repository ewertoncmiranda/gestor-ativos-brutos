package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Corpo da requisição para POST /simulador/analisar no insider-ia (SIM-IA-1).
 */
@Data
@Builder
public class InsiderIaAnalisarRequisicaoDTO {

    @JsonProperty("simbolo")
    private String simbolo;

    @JsonProperty("resultados")
    private List<InsiderIaResultadoItemDTO> resultados;
}
