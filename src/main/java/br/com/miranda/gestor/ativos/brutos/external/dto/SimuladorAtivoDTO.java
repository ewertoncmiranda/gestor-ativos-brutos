package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorAtivoEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class SimuladorAtivoDTO {

    private Integer id;
    private String simbolo;
    private Integer janelaPregoes;
    private BigDecimal capitalInicial;
    private LocalDate dataInclusao;
    private Boolean ativo;

    public static SimuladorAtivoDTO de(SimuladorAtivoEntity entidade) {
        return SimuladorAtivoDTO.builder()
                .id(entidade.getId())
                .simbolo(entidade.getSimbolo())
                .janelaPregoes(entidade.getJanelaPregoes())
                .capitalInicial(entidade.getCapitalInicial())
                .dataInclusao(entidade.getDataInclusao())
                .ativo(entidade.getAtivo())
                .build();
    }
}

