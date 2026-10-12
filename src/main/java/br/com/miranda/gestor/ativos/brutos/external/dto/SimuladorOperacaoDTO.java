package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorOperacaoEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SimuladorOperacaoDTO {

    private Long id;
    private String simbolo;
    private LocalDate dataEntrada;
    private BigDecimal capital;
    private String versaoEstrategia;
    private String status;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public static SimuladorOperacaoDTO de(SimuladorOperacaoEntity entidade) {
        return SimuladorOperacaoDTO.builder()
                .id(entidade.getId())
                .simbolo(entidade.getAtivo() != null ? entidade.getAtivo().getSimbolo() : null)
                .dataEntrada(entidade.getDataEntrada())
                .capital(entidade.getCapital())
                .versaoEstrategia(entidade.getVersaoEstrategia())
                .status(entidade.getStatus().name())
                .criadoEm(entidade.getCriadoEm())
                .atualizadoEm(entidade.getAtualizadoEm())
                .build();
    }
}
