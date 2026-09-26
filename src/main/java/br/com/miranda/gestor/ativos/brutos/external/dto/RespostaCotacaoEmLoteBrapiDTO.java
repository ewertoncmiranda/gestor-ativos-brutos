package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Resposta do endpoint em lote /v2/stocks/quote?symbols=A,B,C - formato
 * diferente do /api/quote/{symbol} legado (RespostaBrapiDTO), que devolve os
 * campos direto em results[], sem o envelope "data".
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RespostaCotacaoEmLoteBrapiDTO {

    @JsonProperty("results")
    private List<ResultadoCotacaoBrapiDTO> results;
}
