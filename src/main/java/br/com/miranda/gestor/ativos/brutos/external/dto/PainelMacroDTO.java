package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resposta de GET /indices-macro/painel (MACRO-GES-3). Campo ausente = null + motivo. */
public record PainelMacroDTO(LocalDate referencia,
                             List<Serie> series,
                             JuroReal juroReal,
                             CicloSelic cicloSelic,
                             Barreira barreira) {

    /** Variacoes em p.p. para taxas e em % para DOLAR, EURO e IBCBR ({@code unidadeVariacao}). */
    public record Serie(String codigo, BigDecimal valor, LocalDate data,
                        BigDecimal variacao1m, BigDecimal variacao3m, BigDecimal variacao12m,
                        String unidadeVariacao, BigDecimal acumulado12m,
                        String motivo, String leitura) {
    }

    public record JuroReal(BigDecimal valor, BigDecimal selicMeta, BigDecimal ipcaEsperado,
                           String fonteIpca, String motivo, String leitura) {
    }

    public record CicloSelic(String ciclo, BigDecimal metaAtual, BigDecimal meta90DiasAntes,
                             LocalDate dataReferenciaAnterior, String motivo, String leitura) {
    }

    public record AtivoAcima(String simbolo, BigDecimal earningsYield) {
    }

    public record Barreira(BigDecimal earningsYieldMinimo, BigDecimal earningsYieldMediano,
                           List<AtivoAcima> ativosAcimaDaBarreira, String fonte,
                           String motivo, String leitura) {
    }
}
