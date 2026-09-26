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
 * Cache de um ponto de serie macroeconomica (Selic, CDI, IPCA...) vindo da
 * API SGS do Banco Central. Escrito exclusivamente por
 * ServicoAtualizacaoIndicesMacro; os controllers so leem.
 */
@Data
@Entity
@Table(name = "indice_macro",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_indice_macro_codigo_data", columnNames = {"codigo_serie", "data"})
       }
)
public class IndiceMacroEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_serie", length = 20, nullable = false)
    private String codigoSerie;

    @Column(nullable = false)
    private LocalDate data;

    @Column(precision = 12, scale = 6)
    private BigDecimal valor;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
