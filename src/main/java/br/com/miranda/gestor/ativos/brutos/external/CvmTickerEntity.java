package br.com.miranda.gestor.ativos.brutos.external;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Ticker da CVM (FCA), carregado pelo etl-fundamentos-cvm. Um simbolo por
 * linha - a empresa (CNPJ, nome, setor) esta em {@link CvmEmpresaEntity}.
 * Somente leitura aqui: quem escreve e o ETL.
 */
@Data
@Entity
@Table(name = "cvm_ticker")
public class CvmTickerEntity {

    @Id
    @Column(length = 10, nullable = false)
    private String simbolo;

    @Column(length = 20, nullable = false)
    private String cnpj;

    @Column(name = "tipo_valor_mobiliario", length = 60)
    private String tipoValorMobiliario;

    @Column(length = 40)
    private String mercado;

    @Column(nullable = false)
    private Boolean ativo = Boolean.TRUE;

    @Column(name = "criado_em", insertable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", insertable = false, updatable = false)
    private LocalDateTime atualizadoEm;
}
