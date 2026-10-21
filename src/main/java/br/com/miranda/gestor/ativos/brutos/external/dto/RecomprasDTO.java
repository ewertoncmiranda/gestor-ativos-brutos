package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Recompras pela conta Acoes em Tesouraria do BPP (FONTES-GES-3). O saldo e negativo (redutora do PL):
 * variacao negativa = recompra; recompraLiquida = -variacao (positiva = recompra liquida em R$).
 */
public record RecomprasDTO(
        String simbolo,
        String cnpj,
        boolean disponivel,
        String motivo,
        BigDecimal recompraLiquida12m,
        List<Ponto> serie) {

    public record Ponto(LocalDate dataBase, String tipoDoc, BigDecimal saldo, BigDecimal variacao,
                        BigDecimal recompraLiquida) {
    }
}
