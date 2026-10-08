package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.time.LocalDate;

/**
 * Composicao acionaria de um ativo, lida de {@code cvm_composicao_capital} (FRE/DFP CVM).
 * Fonte principal e o DFP anual mais recente; ITR e usado quando nao ha DFP.
 */
public record ComposicaoCapitalDTO(
        String simbolo,
        String cnpj,
        LocalDate dtRefer,
        String tipoDoc,
        Long qtAcaoOrdinaria,
        Long qtAcaoPreferencial,
        Long qtAcaoTotal,
        Long qtAcaoExTesouraria) {
}
