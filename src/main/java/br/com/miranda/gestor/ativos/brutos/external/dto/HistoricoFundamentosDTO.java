package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HistoricoFundamentosDTO(String simbolo, List<Ponto> pontos) {
    public record Ponto(LocalDate periodo,
                        @JsonProperty("tipo_periodo") String tipoPeriodo,
                        BigDecimal roe,
                        BigDecimal roic,
                        @JsonProperty("margem_liquida") BigDecimal margemLiquida,
                        @JsonProperty("divida_liquida") BigDecimal dividaLiquida,
                        BigDecimal ebitda,
                        @JsonProperty("liquidez_corrente") BigDecimal liquidezCorrente,
                        BigDecimal lpa,
                        BigDecimal vpa,
                        @JsonProperty("receita_liquida") BigDecimal receitaLiquida,
                        @JsonProperty("lucro_liquido") BigDecimal lucroLiquido) {}
}
