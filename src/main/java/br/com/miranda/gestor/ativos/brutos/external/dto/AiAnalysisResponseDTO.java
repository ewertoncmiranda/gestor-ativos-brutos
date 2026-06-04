package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResponseDTO {

    private String ativo;
    private String sentimento;

    @JsonProperty("forca_sinal")
    private String forcaSinal;

    private String risco;

    @JsonProperty("confianca_analise")
    private Double confiancaAnalise;

    private String resumo;

    @JsonProperty("analise_tecnica")
    private String analiseTecnica;

    @JsonProperty("analise_fundamentalista")
    private String analiseFundamentalista;

    @JsonProperty("possivel_cenario")
    private String possivelCenario;

    private String recomendacao;
}
