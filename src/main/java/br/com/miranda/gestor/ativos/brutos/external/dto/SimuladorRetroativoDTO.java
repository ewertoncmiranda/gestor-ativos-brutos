package br.com.miranda.gestor.ativos.brutos.external.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resumo de retroativo para uma estratégia × versão × janela.
 * Mostra taxa de acerto geral, retorno médio e lista de entradas.
 */
@Data
@Builder
public class SimuladorRetroativoDTO {

    private String simbolo;
    private String estrategia;
    private String versao;
    private Integer janelaPregoesApos;
    private Integer totalEntradas;
    private Integer ganhos;
    private Integer perdas;
    private Integer neutros;
    private BigDecimal taxaAcerto; // (ganhos / totalEntradas) * 100
    private BigDecimal retornoMedio;
    private List<SimuladorRetroativoEntradaDTO> entradas;
}

