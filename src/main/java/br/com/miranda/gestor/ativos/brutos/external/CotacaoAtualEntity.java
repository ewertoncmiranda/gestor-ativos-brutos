package br.com.miranda.gestor.ativos.brutos.external;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cache da cotacao mais recente de um ativo, escrito exclusivamente pelo
 * {@code AgendadorCacheAtivos}. Os controllers que servem o frontend leem
 * daqui - nunca chamam a BRAPI na hora do clique do usuario. Ver
 * ServicoAtualizacaoCache para quem escreve e com que frequencia.
 */
@Data
@Entity
@Table(name = "cotacao_atual",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_cotacao_atual_simbolo", columnNames = "simbolo")
       }
)
public class CotacaoAtualEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10, nullable = false)
    private String simbolo;

    private String shortName;
    private String longName;
    private BigDecimal marketCap;
    private BigDecimal regularMarketChange;
    private BigDecimal regularMarketChangePercent;
    private String regularMarketTime;
    private BigDecimal regularMarketPrice;
    private BigDecimal regularMarketDayHigh;
    private BigDecimal regularMarketDayLow;
    private BigDecimal regularMarketVolume;
    private BigDecimal regularMarketPreviousClose;
    private BigDecimal regularMarketOpen;
    private BigDecimal fiftyTwoWeekLow;
    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal priceEarnings;
    private BigDecimal earningsPerShare;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
