package br.com.miranda.gestor.ativos.brutos.external;

import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Snapshot diário de operações abertas. Gravado pela rotina da manhã (SIM-GES-4).
 * Uma linha por operação com status=ABERTA em cada pregão.
 * Idempotente: mesmo pregão + operação não duplica.
 * Correspondente à migration V34 (SIM-INFRA-2).
 */
@Data
@Entity
@Table(name = "simulador_operacao_diario")
public class SimuladorOperacaoDiarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operacao_id", nullable = false)
    private Long operacaoId;

    @Column(name = "data_pregao", nullable = false)
    private LocalDate dataPregao;

    @Column(precision = 18, scale = 6)
    private BigDecimal preco;

    @Column(name = "retorno_dia_pct", precision = 12, scale = 8)
    private BigDecimal retornoDiaPct;

    @Column(name = "retorno_acum_pct", precision = 12, scale = 8)
    private BigDecimal retornoAcumPct;

    @Column(name = "pnl_rs", precision = 18, scale = 2)
    private BigDecimal pnlRs;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    // Join com simulador_operacao
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operacao_id", insertable = false, updatable = false)
    private SimuladorOperacaoEntity operacao;
}

