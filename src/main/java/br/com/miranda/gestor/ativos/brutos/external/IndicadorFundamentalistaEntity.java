package br.com.miranda.gestor.ativos.brutos.external;

import br.com.miranda.gestor.ativos.brutos.tools.ConversorJsonNode;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Fundamentos contábeis derivados dos dados abertos da CVM, gravados pelo job
 * em lote {@code etl-fundamentos-cvm} (Python). Esta aplicação é somente
 * leitora desta tabela.
 *
 * <p>O mapeamento precisa bater coluna a coluna com o DDL em
 * {@code infra-b3-ecossytem/mysql-init/1 - schema.sql}: como o perfil dev roda
 * com {@code ddl-auto=update}, uma divergência aqui faz o Hibernate ALTERAR a
 * tabela que o ETL escreve.
 *
 * <p>Campo nulo é intencional, não falha: quando o plano de contas da
 * companhia não comporta a métrica (margem e ROIC de banco, por exemplo), o
 * ETL grava NULL e registra a razão em {@code coberturaJson}.
 */
@Data
@Entity
@Table(name = "indicador_fundamentalista")
public class IndicadorFundamentalistaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10, nullable = false)
    private String simbolo;

    @Column(length = 20, nullable = false)
    private String cnpj;

    /** Data de fechamento do exercício a que os números se referem. */
    @Column(nullable = false)
    private LocalDate periodo;

    @Column(name = "tipo_periodo", length = 12, nullable = false)
    private String tipoPeriodo;

    @Column(name = "lucro_liquido", precision = 24, scale = 2)
    private BigDecimal lucroLiquido;

    @Column(name = "patrimonio_liquido", precision = 24, scale = 2)
    private BigDecimal patrimonioLiquido;

    @Column(name = "lucro_liquido_controlador", precision = 24, scale = 2)
    private BigDecimal lucroLiquidoControlador;

    @Column(name = "participacao_nao_controladores", precision = 24, scale = 2)
    private BigDecimal participacaoNaoControladores;

    @Column(name = "receita_liquida", precision = 24, scale = 2)
    private BigDecimal receitaLiquida;

    @Column(precision = 24, scale = 2)
    private BigDecimal ebit;

    @Column(name = "divida_bruta", precision = 24, scale = 2)
    private BigDecimal dividaBruta;

    @Column(name = "caixa_equivalentes", precision = 24, scale = 2)
    private BigDecimal caixaEquivalentes;

    @Column(name = "fluxo_caixa_operacional", precision = 24, scale = 2)
    private BigDecimal fluxoCaixaOperacional;

    @Column(precision = 24, scale = 2)
    private BigDecimal capex;

    @Column(name = "acoes_ex_tesouraria")
    private Long acoesExTesouraria;

    @Column(precision = 18, scale = 6)
    private BigDecimal lpa;

    @Column(precision = 18, scale = 6)
    private BigDecimal vpa;

    @Column(precision = 10, scale = 4)
    private BigDecimal roe;

    @Column(precision = 10, scale = 4)
    private BigDecimal roic;

    @Column(name = "margem_liquida", precision = 10, scale = 4)
    private BigDecimal margemLiquida;

    @Column(name = "divida_liquida", precision = 24, scale = 2)
    private BigDecimal dividaLiquida;

    @Column(name = "fluxo_caixa_livre", precision = 24, scale = 2)
    private BigDecimal fluxoCaixaLivre;

    @Column(length = 20, nullable = false)
    private String fonte;

    @Column(name = "tipo_doc", length = 5, nullable = false)
    private String tipoDoc;

    @Column(length = 3, nullable = false)
    private String grupo;

    @Column(name = "versao_cvm", nullable = false)
    private Integer versaoCvm;

    @Column(name = "plano_contas", length = 20, nullable = false)
    private String planoContas;

    /** Procedência por métrica: qual conta foi usada, ou por que ficou nula. */
    @Convert(converter = ConversorJsonNode.class)
    @Column(name = "cobertura_json", columnDefinition = "json")
    private JsonNode coberturaJson;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", insertable = false, updatable = false)
    private LocalDateTime atualizadoEm;
}
