package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CarteiraOperacaoRequest(String tipo, String simbolo, LocalDateTime executadaEm,
                                      BigDecimal quantidade, BigDecimal precoUnitario,
                                      BigDecimal valorBruto, BigDecimal custos, BigDecimal impostos,
                                      String moeda, String chaveIdempotencia) { }
