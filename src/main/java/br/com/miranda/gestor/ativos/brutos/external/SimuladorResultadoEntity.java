package br.com.miranda.gestor.ativos.brutos.external;

import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Resultados agregados por estratégia e ativo no simulador.
 * Uma linha por ativo × pregão × versao_estrategia.
 * Escritas pelo gerar-insights (SIM-INS-1), lidas pelo gestor.
 * Correspondente à migration V34 (SIM-INFRA-2).
 */
@Data
@Entity
@Table(name = "simulador_resultado")
public class SimuladorResultadoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ativo_id", nullable = false)
    private Integer ativoId;

    @Column(name = "data_pregao", nullable = false)
    private LocalDate dataPregao;

    @Column(name = "versao_estrategia", length = 30, nullable = false)
    private String versaoEstrategia;

    @Column(nullable = false)
    private Integer quintil = 1;

    @Column(name = "retorno_pct", precision = 12, scale = 8)
    private BigDecimal retornoPct;

    @Column(precision = 12, scale = 8)
    private BigDecimal score;

    @Column(name = "parametros_json", columnDefinition = "JSON")
    private String parametrosJson;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    // Join com simulador_ativo para acessar o simbolo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ativo_id", insertable = false, updatable = false)
    private SimuladorAtivoEntity ativo;
}

