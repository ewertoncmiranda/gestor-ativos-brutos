package br.com.miranda.gestor.ativos.brutos.external;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ativo_monitorado",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_ativo_monitorado_simbolo", columnNames = "simbolo")
       }
)
public class AtivoMonitoradoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10, nullable = false)
    private String simbolo;

    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_coleta", length = 30, nullable = false)
    private TipoColeta tipoColeta = TipoColeta.COTACAO;

    @Column(name = "intervalo_segundos", nullable = false)
    private Integer intervaloSegundos = 300;

    @Version
    @Column(nullable = false)
    private Long versao;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
