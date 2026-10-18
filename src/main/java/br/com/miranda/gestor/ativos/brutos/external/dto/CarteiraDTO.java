package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CarteiraDTO(
        String id,
        String nome,
        String estrategia,
        String cor,
        String moedaBase,
        String status,
        LocalDateTime criadaEm,
        List<AtivoDTO> ativos) {

    public record AtivoDTO(String simbolo, LocalDate dataEntrada, java.math.BigDecimal capitalAlocado,
                           String naturezaCapital) {
    }
}
