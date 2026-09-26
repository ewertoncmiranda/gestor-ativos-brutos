package br.com.miranda.gestor.ativos.brutos.external;

import lombok.Data;

import java.math.BigDecimal;
@Data
public class Ativo {

    private String schemaVersion;
    private String dedupKey;
    private Long id;
    private String symbol;
    private String currency;
    private String shortName;
    private String longName;
    private BigDecimal marketCap;
    private BigDecimal regularMarketChange;
    private BigDecimal regularMarketChangePercent;
    private String regularMarketTime;
    private BigDecimal regularMarketPrice;
    private BigDecimal regularMarketDayHigh;
    private BigDecimal regularMarketDayLow;
    private String regularMarketDayRange;
    private BigDecimal regularMarketVolume;
    private BigDecimal regularMarketPreviousClose;
    private BigDecimal regularMarketOpen;
    private String fiftyTwoWeekRange;
    private BigDecimal fiftyTwoWeekLow;
    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal priceEarnings;
    private BigDecimal earningsPerShare;
    private String logoUrl;
}
