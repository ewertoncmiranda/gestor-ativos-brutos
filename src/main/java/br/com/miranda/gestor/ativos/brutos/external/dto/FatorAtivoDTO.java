package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ultimo valor de um fator (Plano LAC, LAC-GES-2; {@code fator_valor} +
 * {@code fator_definicao}, infra V16) para um ativo, com o percentil no
 * universo e no setor - permite comparar sem precisar ver a serie inteira.
 */
public record FatorAtivoDTO(String codigo, String familia, String descricao, int direcaoEsperada,
                            LocalDate dataReferencia, BigDecimal valor, BigDecimal percentilUniverso,
                            BigDecimal percentilSetor, String grupoSetor) {
}
