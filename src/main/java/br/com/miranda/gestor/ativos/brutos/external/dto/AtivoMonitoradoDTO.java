package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AtivoMonitoradoDTO {

    private String simbolo;
    private Boolean ativo;
    private String tipoColeta;
    private Integer intervaloSegundos;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public static AtivoMonitoradoDTO de(AtivoMonitoradoEntity entidade) {
        return AtivoMonitoradoDTO.builder()
                .simbolo(entidade.getSimbolo())
                .ativo(entidade.getAtivo())
                .tipoColeta(entidade.getTipoColeta().name())
                .intervaloSegundos(entidade.getIntervaloSegundos())
                .criadoEm(entidade.getCriadoEm())
                .atualizadoEm(entidade.getAtualizadoEm())
                .build();
    }
}
