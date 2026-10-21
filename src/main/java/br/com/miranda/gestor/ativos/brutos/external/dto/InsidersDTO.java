package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Sinal de administradores/controladores (VLMO, FONTES-GES-3). Compra liquida = compras - vendas. */
public record InsidersDTO(
        String simbolo,
        String cnpj,
        boolean disponivel,
        String motivo,
        String sinal,
        String leitura,
        LocalDate dataUltimaMovimentacao,
        LocalDate dataEntregaMaisRecente,
        List<Janela> janelas) {

    public record Janela(int meses, Agregado total, Map<String, Agregado> porCargo) {
    }

    /** compraLiquidaVolume e null quando nenhuma linha da janela tem volume. */
    public record Agregado(
            BigDecimal quantidadeCompras,
            BigDecimal quantidadeVendas,
            BigDecimal compraLiquidaQuantidade,
            BigDecimal compraLiquidaVolume,
            int movimentacoes) {
    }
}
