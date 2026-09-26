package br.com.miranda.gestor.ativos.brutos.external;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Snapshot de cotação gravado pelo {@code gerar-insights} a cada ciclo de
 * coleta.
 *
 * <p>Esta aplicação lê daqui apenas o preço mais recente, usado para derivar
 * P/L e P/VP na leitura. O preço é o dado rápido (muda em segundos) e o
 * fundamento é o lento (muda por trimestre); cruzá-los na consulta mantém o
 * múltiplo sempre atual sem reexecutar o ETL.
 */
@Data
@Entity
@Table(name = "historico_acoes")
public class HistoricoAcaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10, nullable = false)
    private String simbolo;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "preco_abertura", precision = 12, scale = 4)
    private BigDecimal precoAbertura;

    @Column(name = "preco_fechamento", precision = 12, scale = 4)
    private BigDecimal precoFechamento;

    @Column(name = "preco_maximo", precision = 12, scale = 4)
    private BigDecimal precoMaximo;

    @Column(name = "preco_minimo", precision = 12, scale = 4)
    private BigDecimal precoMinimo;

    private Long volume;

    @Column(name = "minima_52_semanas", precision = 12, scale = 4)
    private BigDecimal minima52Semanas;

    @Column(name = "maxima_52_semanas", precision = 12, scale = 4)
    private BigDecimal maxima52Semanas;

    @Column(name = "valor_mercado")
    private Long valorMercado;

    @Column(name = "preco_lucro", precision = 10, scale = 4)
    private BigDecimal precoLucro;

    @Column(name = "lucro_por_acao", precision = 10, scale = 4)
    private BigDecimal lucroPorAcao;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime criadoEm;
}
