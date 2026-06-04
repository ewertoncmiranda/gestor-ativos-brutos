package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class InsightConsolidadoDTO {

    private String ativo;

    @JsonProperty("janela_analise")
    private JanelaAnalise janelaAnalise;

    @JsonProperty("quantidade_registros")
    private Integer quantidadeRegistros;

    @JsonProperty("sinal_predominante")
    private String sinalPredominante;

    @JsonProperty("percentual_sinais_venda")
    private Double percentualSinaisVenda;

    @JsonProperty("variacao_media")
    private Double variacaoMedia;

    private Map<String, Object> indicadores;

    @Data
    @Builder
    public static class JanelaAnalise {
        private String inicio;
        private String fim;
    }
}
