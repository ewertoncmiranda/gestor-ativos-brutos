package br.com.miranda.gestor.ativos.brutos.external.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resultado de uma entrada do retroativo.
 * Cruza quintil histórico com retorno real do COTAHIST.
 */
@Data
@Builder
public class SimuladorRetroativoEntradaDTO {

    private LocalDate dataPregao;
    private Integer quintil;
    private String estrategia;
    private String versao;
    private BigDecimal precoEntrada;
    private LocalDate dataJanela;
    private BigDecimal precoSaida;
    private BigDecimal resultadoPercentual;
    private String resultado; // GANHO, PERDA, NEUTRO
    private LocalDate dataAcertoDesde;
}

