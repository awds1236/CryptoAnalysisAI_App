package com.example.cryptoanalysisai.models;

import java.util.List;

public class BinanceKline {
    private long openTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    private long closeTime;
    private double quoteAssetVolume;
    private int numberOfTrades;
    private double takerBuyBaseAssetVolume;
    private double takerBuyQuoteAssetVolume;

    public BinanceKline(List<Object> klineData) {
        if (klineData.size() >= 12) {
            this.openTime = ((Number) klineData.get(0)).longValue();
            this.open = Double.parseDouble((String) klineData.get(1));
            this.high = Double.parseDouble((String) klineData.get(2));
            this.low = Double.parseDouble((String) klineData.get(3));
            this.close = Double.parseDouble((String) klineData.get(4));
            this.volume = Double.parseDouble((String) klineData.get(5));
            this.closeTime = ((Number) klineData.get(6)).longValue();
            this.quoteAssetVolume = Double.parseDouble((String) klineData.get(7));
            this.numberOfTrades = ((Number) klineData.get(8)).intValue();
            this.takerBuyBaseAssetVolume = Double.parseDouble((String) klineData.get(9));
            this.takerBuyQuoteAssetVolume = Double.parseDouble((String) klineData.get(10));
        }
    }

    public long getOpenTime() {
        return openTime;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getClose() {
        return close;
    }

    public double getVolume() {
        return volume;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public double getQuoteAssetVolume() {
        return quoteAssetVolume;
    }

    public int getNumberOfTrades() {
        return numberOfTrades;
    }

    public double getTakerBuyBaseAssetVolume() {
        return takerBuyBaseAssetVolume;
    }

    public double getTakerBuyQuoteAssetVolume() {
        return takerBuyQuoteAssetVolume;
    }

    // 업비트 CandleData로 변환하는 메서드
    public CandleData toUpbitFormat(String market) {
        CandleData candleData = new CandleData();
        candleData.setMarket(market);
        candleData.setOpenTime(openTime);
        candleData.setOpeningPrice(open);
        candleData.setHighPrice(high);
        candleData.setLowPrice(low);
        candleData.setTradePrice(close);
        candleData.setCandleAccTradeVolume(volume);
        candleData.setCandleAccTradePrice(volume * close); // 대략적인 계산

        return candleData;
    }
}