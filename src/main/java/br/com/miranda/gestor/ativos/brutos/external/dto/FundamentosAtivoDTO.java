package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Expõe o retrato bruto (não mediado) da última análise persistida de um ativo:
 * os mesmos números e classificações que o gerar-insights calculou naquele ciclo
 * (cenários de preço justo Graham, classificações, contexto técnico), sem a
 * consolidação/média que GET /analises/{simbolo}/analise aplica sobre todo o
 * histórico. Usado pela aba "Como funciona" para mostrar os fundamentos por trás
 * da última decisão de um ativo.
 */
@Data
@Builder
public class FundamentosAtivoDTO {

    private String simbolo;
    private LocalDateTime dataAnalise;
    private BigDecimal precoJustoGraham;
    private BigDecimal margemSegurancaPercent;
    private String recomendacao;
    private JsonNode detalhes;

    public static FundamentosAtivoDTO de(AnaliseAcaoEntity entidade) {
        return FundamentosAtivoDTO.builder()
                .simbolo(entidade.getSimbolo())
                .dataAnalise(entidade.getDataAnalise())
                .precoJustoGraham(entidade.getPrecoJustoGraham())
                .margemSegurancaPercent(entidade.getMargemSegurancaPercent())
                .recomendacao(entidade.getRecomendacao())
                .detalhes(entidade.getDetalhesJson())
                .build();
    }

    public static FundamentosAtivoDTO vazio(String simbolo) {
        return FundamentosAtivoDTO.builder().simbolo(simbolo).build();
    }
}
