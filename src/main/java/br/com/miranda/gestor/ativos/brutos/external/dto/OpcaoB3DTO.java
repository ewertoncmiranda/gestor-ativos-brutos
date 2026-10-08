package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma opcao da B3 num pregao (tabela {@code opcao_b3_diaria}).
 * bdi: "12" = call, "14" = put.
 */
public record OpcaoB3DTO(
        String simbolo,
        String bdi,
        LocalDate dataPregao,
        LocalDate dataVencimento,
        BigDecimal precoExercicio,
        BigDecimal abertura,
        BigDecimal maxima,
        BigDecimal minima,
        BigDecimal fechamento,
        BigDecimal precoMedio,
        Long volume,
        Integer numeroNegocios,
        BigDecimal volumeFinanceiro) {
}
