package br.com.miranda.gestor.ativos.brutos.external;

import lombok.Data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ativo monitorado no simulador.
 * Cada ativo tem janela fixa (imutável após cadastro) e capital inicial de referência.
 * Correspondente à migration V33 (SIM-INFRA-1).
 */
@Data
@Entity
@Table(name = "simulador_ativo",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_simulador_simbolo", columnNames = "simbolo")
       }
)
public class SimuladorAtivoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 10, nullable = false)
    private String simbolo;

    @Column(name = "janela_pregoes", nullable = false)
    private Integer janelaPregoes = 63;

    @Column(name = "capital_inicial", nullable = false, precision = 15, scale = 2)
    private BigDecimal capitalInicial = new BigDecimal("100000.00");

    @Column(name = "data_inclusao", nullable = false)
    private LocalDate dataInclusao;

    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;
}

