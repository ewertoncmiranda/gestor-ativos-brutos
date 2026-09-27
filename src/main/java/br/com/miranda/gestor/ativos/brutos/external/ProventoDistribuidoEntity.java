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
 * Cache de um provento (dividendo ou JCP) aprovado, por ativo - lido da B3
 * (ClienteB3Proventos) e escrito por ServicoAtualizacaoProventos. So captura
 * os ultimos 12 meses a cada consulta (limite do proprio endpoint da B3);
 * rodando periodicamente, a tabela acumula historico real a partir de agora.
 *
 * Chave natural (simbolo, isin, tipo, data_pagamento): a mesma distribuicao
 * pode aparecer em varias consultas seguidas sem duplicar.
 */
@Data
@Entity
@Table(name = "provento_distribuido",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_provento_distribuido",
                   columnNames = {"simbolo", "isin", "tipo", "data_pagamento"})
       }
)
public class ProventoDistribuidoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10, nullable = false)
    private String simbolo;

    @Column(length = 20, nullable = false)
    private String isin;

    @Column(length = 30, nullable = false)
    private String tipo;

    @Column(name = "valor_por_acao", precision = 14, scale = 8, nullable = false)
    private BigDecimal valorPorAcao;

    @Column(length = 30)
    private String periodoReferencia;

    @Column(name = "aprovado_em")
    private LocalDate aprovadoEm;

    @Column(name = "ultima_data_com_direito")
    private LocalDate ultimaDataComDireito;

    @Column(name = "data_pagamento", nullable = false)
    private LocalDate dataPagamento;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
