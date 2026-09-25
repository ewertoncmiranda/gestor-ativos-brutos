package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RespostaHistoricoAcoesDTO(
        @JsonProperty("results")
        List<ResultadoHistoricoAcaoDTO> results,

        @JsonProperty("requestedAt")
        String requestedAt,

        @JsonProperty("took")
        Long took
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultadoHistoricoAcaoDTO(
            @JsonProperty("requestedSymbol")
            String requestedSymbol,

            @JsonProperty("symbol")
            String symbol,

            @JsonProperty("changed")
            Boolean changed,

            @JsonProperty("data")
            SerieHistoricaAcaoDTO data
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SerieHistoricaAcaoDTO(
            @JsonProperty("usedInterval")
            String usedInterval,

            @JsonProperty("usedRange")
            String usedRange,

            @JsonProperty("historicalDataPrice")
            List<PrecoHistoricoAcaoDTO> historicalDataPrice
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrecoHistoricoAcaoDTO(
            @JsonProperty("date")
            Long date,

            @JsonProperty("open")
            BigDecimal open,

            @JsonProperty("high")
            BigDecimal high,

            @JsonProperty("low")
            BigDecimal low,

            @JsonProperty("close")
            BigDecimal close,

            @JsonProperty("volume")
            BigDecimal volume,

            @JsonProperty("adjustedClose")
            BigDecimal adjustedClose
    ) {
        private static final DateTimeFormatter FORMATADOR_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");

        /**
         * Retorna a data do pregao no formato brasileiro, derivada do timestamp Unix em segundos.
         */
        @JsonProperty("dataFormatada")
        public String dataFormatada() {
            if (date == null) {
                return null;
            }

            return Instant.ofEpochSecond(date)
                    .atZone(ZONA_BRASIL)
                    .format(FORMATADOR_DATA);
        }
    }
}
