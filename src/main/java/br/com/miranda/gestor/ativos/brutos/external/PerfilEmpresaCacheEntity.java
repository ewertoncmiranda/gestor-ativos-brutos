package br.com.miranda.gestor.ativos.brutos.external;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Cache do perfil da empresa (setor, industria, resumo do negocio) por
 * simbolo. E o dado que menos muda de todo o ecossistema - por isso a
 * cadencia de atualizacao e semanal, nao diaria nem a cada 30s. Escrito pelo
 * {@code AgendadorCacheAtivos}; os controllers so leem.
 */
@Data
@Entity
@Table(name = "perfil_empresa_cache",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_perfil_empresa_cache_simbolo", columnNames = "simbolo")
       }
)
public class PerfilEmpresaCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10, nullable = false)
    private String simbolo;

    private String sector;
    private String industry;

    // @Lob mapeia pra TINYTEXT (max 255 bytes) no MySQL8Dialect - insuficiente
    // pro resumo do negocio, que costuma passar de 1000 caracteres.
    @Column(columnDefinition = "TEXT")
    private String longBusinessSummary;

    private String website;
    private String cnpj;
    private Integer fullTimeEmployees;
    private String city;
    private String state;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
