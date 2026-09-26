package br.com.miranda.gestor.ativos.brutos.external.dto;

import br.com.miranda.gestor.ativos.brutos.external.AtivoMonitoradoEntity;
import br.com.miranda.gestor.ativos.brutos.external.CotacaoAtualEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
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

    // Preenchidos so quando ha cotacao em cache pra este simbolo. precoAnterior
    // e precoAnteriorEm vem nulos ate a primeira mudanca de preco detectada
    // (ver ServicoAtualizacaoCache.persistirCotacao) - antes disso nao ha
    // "mudanca" para mostrar, so o preco atual.
    private BigDecimal precoAtual;
    private LocalDateTime precoAtualDesde;
    private BigDecimal precoAnterior;
    private LocalDateTime precoAnteriorEm;

    public static AtivoMonitoradoDTO de(AtivoMonitoradoEntity entidade, CotacaoAtualEntity cotacao) {
        return AtivoMonitoradoDTO.builder()
                .simbolo(entidade.getSimbolo())
                .ativo(entidade.getAtivo())
                .tipoColeta(entidade.getTipoColeta().name())
                .intervaloSegundos(entidade.getIntervaloSegundos())
                .criadoEm(entidade.getCriadoEm())
                .atualizadoEm(entidade.getAtualizadoEm())
                .precoAtual(cotacao != null ? cotacao.getRegularMarketPrice() : null)
                .precoAtualDesde(cotacao != null ? cotacao.getPrecoAtualDesde() : null)
                .precoAnterior(cotacao != null ? cotacao.getPrecoAnterior() : null)
                .precoAnteriorEm(cotacao != null ? cotacao.getPrecoAnteriorEm() : null)
                .build();
    }
}
