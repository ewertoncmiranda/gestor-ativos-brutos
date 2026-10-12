package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorResultadoEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class SimuladorResultadoDTO {

    private String simbolo;
    private String versaoEstrategia;
    private LocalDate dataPregao;
    private Integer quintil;
    private BigDecimal retornoPct;
    private BigDecimal score;
    private String parametrosJson;

    public static SimuladorResultadoDTO de(SimuladorResultadoEntity entidade) {
        return SimuladorResultadoDTO.builder()
                .simbolo(entidade.getAtivo() != null ? entidade.getAtivo().getSimbolo() : null)
                .versaoEstrategia(entidade.getVersaoEstrategia())
                .dataPregao(entidade.getDataPregao())
                .quintil(entidade.getQuintil())
                .retornoPct(entidade.getRetornoPct())
                .score(entidade.getScore())
                .parametrosJson(entidade.getParametrosJson())
                .build();
    }
}

