package br.com.miranda.gestor.ativos.brutos.external.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Premio de risco do ativo contra a NTN-B de referencia (FONTES-GES-1). Pontos percentuais. */
public record PremioRiscoDTO(
        String simbolo,
        BigDecimal earningsYield,
        BigDecimal juroReal,
        LocalDate vencimentoReferencia,
        LocalDate dataBase,
        BigDecimal premioRisco,
        String veredito,
        String leitura,
        BigDecimal selicMeta,
        BigDecimal premioSobreSelic,
        String motivo) {
}
