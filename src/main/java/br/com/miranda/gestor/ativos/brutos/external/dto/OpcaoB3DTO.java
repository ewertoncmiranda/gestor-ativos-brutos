package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Uma opcao da B3 num pregao (tabela {@code opcao_b3_diaria}).
 * tipoOpcao vem do TPMERC curado (CALL/PUT); BDI permanece código de origem independente.
 */
public record OpcaoB3DTO(
        String simbolo,
        String bdi,
        String tipoOpcao,
        LocalDate dataPregao,
        LocalDate dataVencimento,
        BigDecimal precoExercicio,
        BigDecimal abertura,
        BigDecimal maxima,
        BigDecimal minima,
        BigDecimal fechamento,
        BigDecimal precoMedio,
        Long volume,
        Integer numeroNegocios,
        BigDecimal volumeFinanceiro,
        BigDecimal melhorOfertaCompra,
        BigDecimal melhorOfertaVenda,
        String indiceCorrecao) {

    public OpcaoB3DTO(String simbolo, String bdi, String tipoOpcao, LocalDate dataPregao,
                      LocalDate dataVencimento, BigDecimal precoExercicio, BigDecimal abertura,
                      BigDecimal maxima, BigDecimal minima, BigDecimal fechamento, BigDecimal precoMedio,
                      Long volume, Integer numeroNegocios, BigDecimal volumeFinanceiro) {
        this(simbolo, bdi, tipoOpcao, dataPregao, dataVencimento, precoExercicio, abertura,
                maxima, minima, fechamento, precoMedio, volume, numeroNegocios, volumeFinanceiro,
                null, null, null);
    }

    /** Meio do spread cotado; ofertas ausentes, zero ou cruzadas não formam preço justo. */
    @JsonProperty("precoJusto")
    public BigDecimal precoJusto() {
        if (melhorOfertaCompra == null || melhorOfertaVenda == null
                || melhorOfertaCompra.signum() <= 0 || melhorOfertaVenda.signum() <= 0
                || melhorOfertaVenda.compareTo(melhorOfertaCompra) < 0) return null;
        return melhorOfertaCompra.add(melhorOfertaVenda).divide(BigDecimal.valueOf(2));
    }

    /** Percentual em pontos percentuais, relativo ao meio do spread. */
    @JsonProperty("spreadPercentual")
    public BigDecimal spreadPercentual() {
        BigDecimal meio = precoJusto();
        return meio == null ? null : melhorOfertaVenda.subtract(melhorOfertaCompra)
                .multiply(BigDecimal.valueOf(100)).divide(meio, 8, RoundingMode.HALF_UP);
    }
}
