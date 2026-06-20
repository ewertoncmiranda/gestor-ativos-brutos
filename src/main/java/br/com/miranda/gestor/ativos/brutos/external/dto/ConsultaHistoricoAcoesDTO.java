package br.com.miranda.gestor.ativos.brutos.external.dto;

public record ConsultaHistoricoAcoesDTO(
        String symbols,
        String range,
        String interval,
        String startDate,
        String endDate,
        String sortOrder
) {
}
