package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CarteiraProventoRequest(String simbolo, String tipo, LocalDate dataEx, LocalDate dataPagamento,
                                     BigDecimal quantidadeElegivel, BigDecimal valorPorAtivo,
                                     BigDecimal valorBruto, BigDecimal impostoRetido, String moeda,
                                     String referenciaEvento, String chaveIdempotencia) { }
