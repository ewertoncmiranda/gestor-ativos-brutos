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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cache de um candle diario (OHLCV) de um ativo, chave por simbolo+data.
 * Candles de dias passados sao imutaveis e nunca sao regravados; so o candle
 * do dia atual e atualizado (upsert) enquanto o pregao ainda esta em curso.
 * Escrito pelo {@code AgendadorCacheAtivos}; os controllers so leem.
 */
@Data
@Entity
@Table(name = "candle_diario",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_candle_diario_simbolo_data", columnNames = {"simbolo", "data"})
       }
)
public class CandleDiarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10, nullable = false)
    private String simbolo;

    @Column(nullable = false)
    private LocalDate data;

    @Column(precision = 12, scale = 4)
    private BigDecimal open;

    @Column(precision = 12, scale = 4)
    private BigDecimal high;

    @Column(precision = 12, scale = 4)
    private BigDecimal low;

    @Column(precision = 12, scale = 4)
    private BigDecimal close;

    private BigDecimal volume;

    @Column(name = "adjusted_close", precision = 12, scale = 4)
    private BigDecimal adjustedClose;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
