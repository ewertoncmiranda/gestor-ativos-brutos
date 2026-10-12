package br.com.miranda.gestor.ativos.brutos.external.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class SimuladorOperacaoHistoricoDTO {
    private Long operacaoId;
    private String simbolo;
    private LocalDate dataEntrada;
    private BigDecimal capital;
    private BigDecimal precoEntrada;
    private BigDecimal totalRetornoPct;
    private BigDecimal totalPnlRs;
    private List<SimuladorOperacaoPontoDiarioDTO> pontos;
}
