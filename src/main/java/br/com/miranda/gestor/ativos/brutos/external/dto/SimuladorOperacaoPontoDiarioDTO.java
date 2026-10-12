package br.com.miranda.gestor.ativos.brutos.external.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class SimuladorOperacaoPontoDiarioDTO {
    private LocalDate dataPregao;
    private BigDecimal preco;
    private BigDecimal retornoDiaPct;
    private BigDecimal retornoAcumPct;
    private BigDecimal pnlRs;
    private Map<String, Integer> quintilPorEstrategia;
}
