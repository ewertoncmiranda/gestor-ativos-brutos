package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.SimuladorIaAnaliseEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SimuladorIaAnaliseDTO {

    private String simbolo;
    private String dadosEntradaJson;
    private String analiseTexto;
    private String modeloIa;
    private Integer tokensUsados;
    private Boolean emCache;
    private LocalDateTime salvoEm;

    public static SimuladorIaAnaliseDTO de(SimuladorIaAnaliseEntity entidade, Boolean emCache) {
        return SimuladorIaAnaliseDTO.builder()
                .simbolo(entidade.getAtivo() != null ? entidade.getAtivo().getSimbolo() : null)
                .dadosEntradaJson(entidade.getDadosEntradaJson())
                .analiseTexto(entidade.getAnaliseTexto())
                .modeloIa(entidade.getModeloIa())
                .tokensUsados(entidade.getTokensUsados())
                .emCache(emCache)
                .salvoEm(entidade.getCriadoEm())
                .build();
    }
}

