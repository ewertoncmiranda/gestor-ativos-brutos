package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Calendario de datas ex de um ativo (MICRO-GES-2). Campos opcionais ficam
 * null quando a fonte nao os tem.
 */
public record DatasExDTO(String simbolo,
                         String origemDados,
                         LocalDate desde,
                         List<Evento> eventos,
                         Resumo resumo) {

    public record Evento(LocalDate dataEx,
                         String marca,
                         String tipo,
                         BigDecimal fechamentoAnterior,
                         BigDecimal aberturaEx,
                         BigDecimal ajusteImplicito,
                         BigDecimal ajustePercentual,
                         BigDecimal valorDeclarado,
                         String origemValor,
                         BigDecimal razaoAjuste,
                         BigDecimal yieldDoEvento) {
    }

    public record Resumo(int quantidade12Meses,
                         BigDecimal somaYield12Meses,
                         LocalDate proximaDataEstimada,
                         String proximoTipo,
                         String confianca,
                         String leitura) {
    }
}
