package br.com.miranda.gestor.ativos.brutos.external;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Cache diário de análises IA do simulador (V35).
 * Uma linha por ativo por dia (unique ativo_id + DATE(data_analise)).
 * Segunda chamada do mesmo dia reutiliza a linha existente.
 */
@Data
@Entity
@Table(name = "simulador_ia_analise",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_simulador_ia_analise_ativo_dia",
                           columnNames = {"ativo_id"})
       }
)
public class SimuladorIaAnaliseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ativo_id", nullable = false)
    private Integer ativoId;

    @Column(name = "data_analise", nullable = false)
    private LocalDateTime dataAnalise;

    @Column(name = "dados_entrada_json", columnDefinition = "JSON")
    private String dadosEntradaJson;

    @Column(name = "analise_texto", columnDefinition = "TEXT")
    private String analiseTexto;

    @Column(name = "modelo_ia", length = 100, nullable = false)
    private String modeloIa;

    @Column(name = "tokens_usados")
    private Integer tokensUsados;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ativo_id", insertable = false, updatable = false)
    private SimuladorAtivoEntity ativo;
}
