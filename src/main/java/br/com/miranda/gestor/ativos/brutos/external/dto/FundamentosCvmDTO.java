package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.IndicadorFundamentalistaEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Fundamentos contábeis de um ativo, vindos dos dados abertos da CVM, com os
 * múltiplos de mercado derivados na leitura.
 *
 * <p>Os campos precoLucro e precoValorPatrimonial NÃO vêm do banco: são
 * calculados a cada requisição cruzando LPA/VPA (que mudam por trimestre) com
 * a cotação mais recente (que muda em segundos). É o que os mantém atuais sem
 * reexecutar o ETL.
 *
 * <p>A procedência (periodo, defasagemDias, precoEm, fonte) acompanha os
 * números de propósito: cruzar preço de hoje com balanço de dezembro sem dizer
 * isso produz número sem contexto.
 *
 * <p>Métrica nula é intencional. Quando o plano de contas da companhia não
 * comporta a métrica — margem e ROIC de banco, por exemplo — o ETL grava NULL
 * e a razão fica em cobertura.
 */
@Data
@Builder
public class FundamentosCvmDTO {

    private String simbolo;
    private String cnpj;

    private LocalDate periodo;

    @JsonProperty("tipo_periodo")
    private String tipoPeriodo;

    @JsonProperty("defasagem_dias")
    private Long defasagemDias;

    // --- rentabilidade ---
    private BigDecimal roe;
    private BigDecimal roic;

    @JsonProperty("margem_liquida")
    private BigDecimal margemLiquida;

    // --- por ação ---
    private BigDecimal lpa;
    private BigDecimal vpa;

    @JsonProperty("preco_lucro")
    private BigDecimal precoLucro;

    @JsonProperty("preco_valor_patrimonial")
    private BigDecimal precoValorPatrimonial;

    // --- estrutura de capital ---
    @JsonProperty("divida_bruta")
    private BigDecimal dividaBruta;

    @JsonProperty("divida_liquida")
    private BigDecimal dividaLiquida;

    @JsonProperty("caixa_equivalentes")
    private BigDecimal caixaEquivalentes;

    @JsonProperty("fluxo_caixa_operacional")
    private BigDecimal fluxoCaixaOperacional;

    @JsonProperty("fluxo_caixa_livre")
    private BigDecimal fluxoCaixaLivre;

    // --- resultado ---
    @JsonProperty("receita_liquida")
    private BigDecimal receitaLiquida;

    private BigDecimal ebit;

    @JsonProperty("lucro_liquido")
    private BigDecimal lucroLiquido;

    @JsonProperty("acoes_ex_tesouraria")
    private Long acoesExTesouraria;

    // --- procedência ---
    private String fonte;

    @JsonProperty("tipo_doc")
    private String tipoDoc;

    @JsonProperty("plano_contas")
    private String planoContas;

    @JsonProperty("versao_cvm")
    private Integer versaoCvm;

    private JsonNode cobertura;

    /** Cotação usada para derivar os múltiplos, e quando foi coletada. */
    @JsonProperty("preco_referencia")
    private BigDecimal precoReferencia;

    @JsonProperty("preco_em")
    private LocalDateTime precoEm;

    public static FundamentosCvmDTO de(IndicadorFundamentalistaEntity entidade) {
        return FundamentosCvmDTO.builder()
                .simbolo(entidade.getSimbolo())
                .cnpj(entidade.getCnpj())
                .periodo(entidade.getPeriodo())
                .tipoPeriodo(entidade.getTipoPeriodo())
                .roe(entidade.getRoe())
                .roic(entidade.getRoic())
                .margemLiquida(entidade.getMargemLiquida())
                .lpa(entidade.getLpa())
                .vpa(entidade.getVpa())
                .dividaBruta(entidade.getDividaBruta())
                .dividaLiquida(entidade.getDividaLiquida())
                .caixaEquivalentes(entidade.getCaixaEquivalentes())
                .fluxoCaixaOperacional(entidade.getFluxoCaixaOperacional())
                .fluxoCaixaLivre(entidade.getFluxoCaixaLivre())
                .receitaLiquida(entidade.getReceitaLiquida())
                .ebit(entidade.getEbit())
                .lucroLiquido(entidade.getLucroLiquido())
                .acoesExTesouraria(entidade.getAcoesExTesouraria())
                .fonte(entidade.getFonte())
                .tipoDoc(entidade.getTipoDoc())
                .planoContas(entidade.getPlanoContas())
                .versaoCvm(entidade.getVersaoCvm())
                .cobertura(entidade.getCoberturaJson())
                .build();
    }

    /**
     * Resposta para símbolo sem fundamento carregado. Devolvida com HTTP 200,
     * igual ao /fundamentos existente: o front já sabe lidar com campos nulos,
     * e 404 aqui confundiria "ticker inválido" com "ainda não carregado pelo
     * ETL".
     */
    public static FundamentosCvmDTO vazio(String simbolo) {
        return FundamentosCvmDTO.builder().simbolo(simbolo).build();
    }
}
