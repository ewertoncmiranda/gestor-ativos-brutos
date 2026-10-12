package br.com.miranda.gestor.ativos.brutos.external;

import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Operações manuais do simulador: PENDENTE (criada) -> ABERTA (pregão >= data_entrada) -> FECHADA (saída executada).
 * Criadas pelo usuário via POST /simulador/operacoes.
 * Transições de status: job da manhã (SIM-GES-4) abre operações PENDENTE com data_entrada <= hoje.
 * Correspondente à migration V34 (SIM-INFRA-2).
 */
@Data
@Entity
@Table(name = "simulador_operacao")
public class SimuladorOperacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ativo_id", nullable = false)
    private Integer ativoId;

    @Column(name = "data_entrada", nullable = false)
    private LocalDate dataEntrada;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal capital;

    @Column(name = "versao_estrategia", length = 30, nullable = false)
    private String versaoEstrategia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusOperacao status = StatusOperacao.PENDENTE;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", insertable = false, updatable = false)
    private LocalDateTime atualizadoEm;

    // Join com simulador_ativo para acessar o simbolo
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ativo_id", insertable = false, updatable = false)
    private SimuladorAtivoEntity ativo;

    public enum StatusOperacao {
        PENDENTE, ABERTA, FECHADA
    }
}

