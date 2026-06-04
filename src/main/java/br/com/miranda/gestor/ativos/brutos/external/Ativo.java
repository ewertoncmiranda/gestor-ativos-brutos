package br.com.miranda.gestor.ativos.brutos.external;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
public class Ativo {

    private Long id;
    private String symbol;
    private String currency;
    private String shortName;
    private String longName;
    private BigDecimal marketCap;
    private BigDecimal regularMarketChange;
    private BigDecimal regularMarketChangePercent;
    private LocalDateTime regularMarketTime;
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
