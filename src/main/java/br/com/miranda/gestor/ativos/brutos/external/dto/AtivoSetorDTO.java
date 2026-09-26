package br.com.miranda.gestor.ativos.brutos.external.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Um ativo dentro da visao "Mercado por setor" - preco e variacao vem do
 * cache ja existente (cotacao_atual); se o ativo acabou de ser registrado
 * como referencia e ainda nao rodou um ciclo do agendador, os campos vem
 * null em vez de travar a resposta inteira.
 */
@Data
@Builder
public class AtivoSetorDTO {
    private String simbolo;
    private BigDecimal preco;
    private BigDecimal variacaoPercent;
    private LocalDateTime atualizadoEm;
}
