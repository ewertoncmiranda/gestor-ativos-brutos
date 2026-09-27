package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.ProventoDistribuidoEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProventoDistribuidoDTO {
    private String tipo;
    private BigDecimal valorPorAcao;
    private String periodoReferencia;
    private String aprovadoEm;
    private String ultimaDataComDireito;
    private String dataPagamento;

    public static ProventoDistribuidoDTO de(ProventoDistribuidoEntity e) {
        return ProventoDistribuidoDTO.builder()
                .tipo(e.getTipo())
                .valorPorAcao(e.getValorPorAcao())
                .periodoReferencia(e.getPeriodoReferencia())
                .aprovadoEm(e.getAprovadoEm() == null ? null : e.getAprovadoEm().toString())
                .ultimaDataComDireito(e.getUltimaDataComDireito() == null ? null : e.getUltimaDataComDireito().toString())
                .dataPagamento(e.getDataPagamento().toString())
                .build();
    }
}
