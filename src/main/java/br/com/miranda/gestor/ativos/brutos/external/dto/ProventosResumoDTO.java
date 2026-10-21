package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Valores brutos por ação; a cobertura se refere aos eventos identificados, não a recebimentos em carteira. */
public record ProventosResumoDTO(String simbolo, LocalDate de, LocalDate ate, List<Evento> eventos,
                                 Resumo resumo, String sinteseHolistica, String premissa) {
    public record Pagamento(LocalDate dataPagamento, BigDecimal valorPorAcao, String tipo) {}
    public record Evento(LocalDate dataEx, String marca, String tipo, BigDecimal fechamentoAnterior,
                         BigDecimal aberturaEx, BigDecimal ajusteImplicito, BigDecimal ajustePercentual,
                         BigDecimal valorPorAcao, List<Pagamento> pagamentos, String fonte) {}
    public record Resumo(BigDecimal totalPorAcao, BigDecimal jcpPorAcao, BigDecimal dividendosPorAcao,
                         int quantidadeEventos, int eventosComValor, BigDecimal retornoPrecoPercentual,
                         BigDecimal contribuicaoProventosPontosPercentuais, BigDecimal retornoComProventosPercentual,
                         BigDecimal ajusteMedioPercentual, int mesesComEventos, BigDecimal intervaloMedioDias,
                         LocalDate ultimoEvento, boolean coberturaCompleta, List<String> limitacoes) {}
}
