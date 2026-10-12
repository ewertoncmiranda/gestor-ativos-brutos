package br.com.miranda.gestor.ativos.brutos.external.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * P&L de um cenário pontual (what-if).
 * Para data e janela fornecidas, mostra resultado por estratégia + CDI + posição.
 */
@Data
@Builder
public class SimuladorCenarioDTO {

    private String simbolo;
    private LocalDate dataEntrada;
    private LocalDate dataSaida;
    private Integer janelaPregoesApos;
    private BigDecimal capitalInicial;

    // P&L por estratégia
    private Map<String, BigDecimal> retornoPorEstrategia; // <estrategia, retorno%>
    private Map<String, BigDecimal> reaisGanhoPerdaPorEstrategia; // <estrategia, R$>

    // Benchmark
    private BigDecimal cdiPercentual;
    private BigDecimal cdiReais;

    // Melhor e pior caso
    private String melhorEstrategia;
    private BigDecimal melhorRetorno;
    private String piorEstrategia;
    private BigDecimal piorRetorno;
}

