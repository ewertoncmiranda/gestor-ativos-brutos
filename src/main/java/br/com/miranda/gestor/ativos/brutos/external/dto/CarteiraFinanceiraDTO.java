package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CarteiraFinanceiraDTO {
    private CarteiraFinanceiraDTO() { }

    public record Operacao(String id, String tipo, String operacaoReferenciadaId, String simbolo,
                           LocalDateTime executadaEm, BigDecimal quantidade, BigDecimal precoUnitario,
                           BigDecimal valorBruto, BigDecimal custos, BigDecimal impostos,
                           BigDecimal valorLiquido, String moeda, String origem, String chaveIdempotencia) { }

    public record Provento(String id, String simbolo, String tipo, LocalDate dataEx, LocalDate dataPagamento,
                           BigDecimal quantidadeElegivel, BigDecimal valorPorAtivo, BigDecimal valorBruto,
                           BigDecimal impostoRetido, BigDecimal valorLiquido, String moeda,
                           String referenciaEvento, String chaveIdempotencia) { }

    public record AvaliacaoAtivo(String simbolo, BigDecimal quantidade, BigDecimal precoFechamento,
                                 LocalDate dataPregaoPreco, String fontePreco, BigDecimal valorMercado,
                                 String versaoCalculo) { }

    public record Rentabilidade(LocalDate dataReferencia, String moeda, BigDecimal valorMercado,
                                BigDecimal saldoCaixa, BigDecimal fluxoExterno, BigDecimal proventosLiquidos,
                                BigDecimal resultadoRealizado, BigDecimal resultadoNaoRealizado,
                                BigDecimal retornoPeriodo, BigDecimal retornoAcumulado,
                                String metodo, String versaoCalculo, LocalDateTime calculadoEm) { }

    public record Snapshot(LocalDate dataReferencia, String versaoCalculo, java.util.List<AvaliacaoAtivo> ativos,
                          java.util.List<String> simbolosSemCotacao, Rentabilidade rentabilidade,
                          String observacao) { }
}
