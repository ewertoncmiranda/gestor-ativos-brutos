package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Provento por periodo contabil (DVA da CVM: JCP + dividendos declarados no
 * DFP/ITR, Plano LAC LAC-GES-3, {@code provento_contabil}, infra V16) - ao
 * lado dos eventos de {@code /proventos/{simbolo}} (data-com da B3, endpoint
 * ao vivo). Esta fonte e por periodo (ano/trimestre), nao por data de
 * pagamento; a aproximacao e descrita em {@code infra#SPEC.md, Plano LAC}.
 */
public record ProventoContabilDTO(String tipoDoc, LocalDate dtInicioExercicio, LocalDate dtFimExercicio,
                                  LocalDate dataEntrega, BigDecimal jcp, BigDecimal dividendos,
                                  BigDecimal total, BigDecimal porAcao, String origem) {
}
