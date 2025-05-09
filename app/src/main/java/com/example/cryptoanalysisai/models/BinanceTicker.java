package com.example.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;

public class BinanceTicker {
    @SerializedName("symbol")
    private String symbol;

    @SerializedName("price")
    private String price;

    @SerializedName("priceChange")
    private String priceChange;

    @SerializedName("priceChangePercent")
    private String priceChangePercent;

    @SerializedName("weightedAvgPrice")
    private String weightedAvgPrice;

    @SerializedName("volume")
    private String volume;

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        try {
            return Double.parseDouble(price);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public double getPriceChange() {
        try {
            return Double.parseDouble(priceChange);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public double getPriceChangePercent() {
        try {
            return Double.parseDouble(priceChangePercent);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public double getWeightedAvgPrice() {
        try {
            return Double.parseDouble(weightedAvgPrice);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public double getVolume() {
        try {
            return Double.parseDouble(volume);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // 업비트 TickerData로 변환하는 메서드
    public TickerData toUpbitFormat() {
        TickerData tickerData = new TickerData();
        tickerData.setMarket(symbol);
        tickerData.setTradePrice(getPrice());

        // 변화율 계산
        double changeRate = getPriceChangePercent() / 100.0;
        tickerData.setChangeRate(changeRate);

        return tickerData;
    }
}