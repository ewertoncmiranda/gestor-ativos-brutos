package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Payload compatível com o store local b3-carteiras do frontend. */
public record ImportacaoCarteirasRequest(List<CarteiraLegada> carteiras) {
    public record CarteiraLegada(String id, String nome, String estrategia, String cor,
                                 LocalDateTime criadaEm, List<AtivoLegado> ativos) { }
    public record AtivoLegado(String simbolo, LocalDate dataEntrada, BigDecimal capital) { }
}
