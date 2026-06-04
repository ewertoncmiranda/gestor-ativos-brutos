package br.com.miranda.gestor.ativos.brutos.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class BrapiAtivoDTO {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("shortName")
    private String shortName;

    @JsonProperty("longName")
    private String longName;

    @JsonProperty("marketCap")
    private BigDecimal marketCap;

    @JsonProperty("regularMarketChange")
    private BigDecimal regularMarketChange;

    @JsonProperty("regularMarketChangePercent")
    private BigDecimal regularMarketChangePercent;

    @JsonProperty("regularMarketTime")
    private String regularMarketTime; // será transformado em LocalDateTime no conversor

    @JsonProperty("regularMarketPrice")
    private BigDecimal regularMarketPrice;

    @JsonProperty("regularMarketDayHigh")
    private BigDecimal regularMarketDayHigh;

    @JsonProperty("regularMarketDayLow")
    private BigDecimal regularMarketDayLow;

    @JsonProperty("regularMarketDayRange")
    private String regularMarketDayRange;

    @JsonProperty("regularMarketVolume")
    private BigDecimal regularMarketVolume;

    @JsonProperty("regularMarketPreviousClose")
    private BigDecimal regularMarketPreviousClose;

    @JsonProperty("regularMarketOpen")
    private BigDecimal regularMarketOpen;

    @JsonProperty("fiftyTwoWeekRange")
    private String fiftyTwoWeekRange;

    @JsonProperty("fiftyTwoWeekLow")
    private BigDecimal fiftyTwoWeekLow;

    @JsonProperty("fiftyTwoWeekHigh")
    private BigDecimal fiftyTwoWeekHigh;

    @JsonProperty("priceEarnings")
    private BigDecimal priceEarnings;

    @JsonProperty("earningsPerShare")
    private BigDecimal earningsPerShare;

    @JsonProperty("logourl")
    private String logoUrl;
}
