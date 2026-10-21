package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Curva real (NTN-B) da data-base mais recente (FONTES-GES-1). */
public record CurvaRealDTO(LocalDate dataBase, Ponto referencia, List<Ponto> vencimentos, String motivo) {

    public record Ponto(String tipo, LocalDate vencimento, BigDecimal taxaCompra, BigDecimal taxaVenda) {
    }
}
