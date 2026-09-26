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

    public static Ativo de(CotacaoAtualEntity entidade) {
        Ativo ativo = new Ativo();
        ativo.setSymbol(entidade.getSimbolo());
        ativo.setShortName(entidade.getShortName());
        ativo.setLongName(entidade.getLongName());
        ativo.setMarketCap(entidade.getMarketCap());
        ativo.setRegularMarketChange(entidade.getRegularMarketChange());
        ativo.setRegularMarketChangePercent(entidade.getRegularMarketChangePercent());
        ativo.setRegularMarketTime(entidade.getRegularMarketTime());
        ativo.setRegularMarketPrice(entidade.getRegularMarketPrice());
        ativo.setRegularMarketDayHigh(entidade.getRegularMarketDayHigh());
        ativo.setRegularMarketDayLow(entidade.getRegularMarketDayLow());
        ativo.setRegularMarketVolume(entidade.getRegularMarketVolume());
        ativo.setRegularMarketPreviousClose(entidade.getRegularMarketPreviousClose());
        ativo.setRegularMarketOpen(entidade.getRegularMarketOpen());
        ativo.setFiftyTwoWeekLow(entidade.getFiftyTwoWeekLow());
        ativo.setFiftyTwoWeekHigh(entidade.getFiftyTwoWeekHigh());
        ativo.setPriceEarnings(entidade.getPriceEarnings());
        ativo.setEarningsPerShare(entidade.getEarningsPerShare());
        return ativo;
    }
}
