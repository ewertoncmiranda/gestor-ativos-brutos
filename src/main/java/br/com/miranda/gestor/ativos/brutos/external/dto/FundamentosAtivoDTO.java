package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.AnaliseAcaoEntity;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Expõe o retrato bruto (não mediado) da última análise persistida de um ativo:
 * os mesmos números e classificações que o gerar-insights calculou naquele ciclo
 * (cenários de preço justo Graham, classificações, contexto técnico), sem a
 * consolidação/média que GET /analises/{simbolo}/analise aplica sobre todo o
 * histórico. Usado pela aba "Como funciona" para mostrar os fundamentos por trás
 * da última decisão de um ativo.
 *
 * Os campos perfisAplicaveis/riscoCompraAgora/riscoVendaAgora/confluenciaSinais
 * são calculados por {@link br.com.miranda.gestor.ativos.brutos.tools.PerfilOperacaoClassificador}
 * a partir do mesmo detalhes_json - não vêm do gerar-insights.
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

    private List<String> perfisAplicaveis;
    private String riscoCompraAgora;
    private String riscoVendaAgora;
    private ConfluenciaSinaisDTO confluenciaSinais;

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

    /**
     * Contagem de concordância entre os sinais independentes disponíveis
     * (recomendação fundamentalista, sinal de momentum, sinal de reversão) -
     * NÃO é uma probabilidade estatística de sucesso, só quantos sinais
     * apontam pro mesmo lado.
     */
    @Data
    @Builder
    public static class ConfluenciaSinaisDTO {
        private int compra;
        private int venda;
        private int neutro;
        private String resumo;
    }
}
