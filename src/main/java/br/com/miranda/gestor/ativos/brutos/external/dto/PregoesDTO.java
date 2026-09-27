package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Velas de um ativo num periodo (contrato infra#CTR-15).
 *
 * @param simbolo  codigo canonico (pedir ELET3 devolve AXIA3)
 * @param codigos  codigos usados na serie (canonico + antigos do mesmo papel)
 * @param velas    uma por dia, semana ou mes; {@code data} e o primeiro pregao do grupo
 * @param saltos   pregoes com abertura >= 40% longe do fechamento anterior:
 *                 provavel desdobramento/grupamento em preco bruto, nao movimento
 */
public record PregoesDTO(
        String simbolo,
        List<String> codigos,
        String intervalo,
        LocalDate de,
        LocalDate ate,
        List<Vela> velas,
        Resumo resumo,
        List<Salto> saltos,
        String aviso) {

    public record Vela(LocalDate data, LocalDate dataFim, String codigo, BigDecimal abertura, BigDecimal maxima,
                       BigDecimal minima, BigDecimal fechamento, Long volume, int pregoes, String fonte) {
    }

    /** Abertura do primeiro pregao ao fechamento do ultimo; extremos com a data. */
    public record Resumo(int pregoes, LocalDate primeiroPregao, LocalDate ultimoPregao, BigDecimal abertura,
                         BigDecimal fechamento, BigDecimal variacao, BigDecimal maxima, LocalDate dataMaxima,
                         BigDecimal minima, LocalDate dataMinima, boolean temSalto) {
    }

    public record Salto(LocalDate data, BigDecimal fechamentoAnterior, BigDecimal abertura, BigDecimal variacao) {
    }
}
