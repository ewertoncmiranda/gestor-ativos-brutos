package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Item de resultado de estratégia enviado ao insider-ia no corpo de POST /simulador/analisar.
 */
@Data
@Builder
public class InsiderIaResultadoItemDTO {

    @JsonProperty("versao")
    private String versao;

    @JsonProperty("quintil")
    private Integer quintil;

    @JsonProperty("retorno_pct")
    private BigDecimal retornoPct;
}
