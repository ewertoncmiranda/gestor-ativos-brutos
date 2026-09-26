package br.com.miranda.gestor.ativos.brutos.external;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

/**
 * Comunicado oficial de companhia aberta (base IPE dos dados abertos da CVM),
 * gravado pelo job {@code etl-fundamentos-cvm --comunicados}. Esta aplicação
 * é somente leitora - daí o {@link Immutable}.
 *
 * <p>O mapeamento espelha {@code infra-b3-ecossytem/mysql-migrations/V3__comunicados_cvm.sql}
 * coluna a coluna: o perfil dev roda com {@code ddl-auto=update}, e uma
 * divergência faria o Hibernate ALTERAR a tabela que o ETL escreve.
 *
 * <p>Guarda CNPJ, não ticker: a tradução é feita na consulta, via
 * {@code cvm_ticker} (contrato {@code infra#CTR-08}).
 */
@Data
@Entity
@Immutable
@Table(name = "comunicado_cvm")
public class ComunicadoCvmEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * numProtocolo do link do RAD: a identidade do documento. A unicidade
     * existe no banco (uq_comunicado_cvm_protocolo); não é declarada aqui para
     * o ddl-auto=update não criar uma segunda chave única com outro nome.
     */
    @Column(name = "protocolo_cvm", length = 20, nullable = false)
    private String protocoloCvm;

    @Column(name = "protocolo_entrega", length = 40)
    private String protocoloEntrega;

    @Column(nullable = false)
    private Integer versao;

    @Column(length = 20, nullable = false)
    private String cnpj;

    @Column(name = "codigo_cvm", length = 10)
    private String codigoCvm;

    @Column(length = 40, nullable = false)
    private String categoria;

    @Column(name = "categoria_original", length = 200, nullable = false)
    private String categoriaOriginal;

    @Column(length = 120)
    private String tipo;

    @Column(length = 120)
    private String especie;

    @Column(columnDefinition = "TEXT")
    private String assunto;

    @Column(name = "data_referencia")
    private LocalDate dataReferencia;

    @Column(name = "data_entrega", nullable = false)
    private LocalDate dataEntrega;

    @Column(name = "link_download", length = 300, nullable = false)
    private String linkDownload;
}
