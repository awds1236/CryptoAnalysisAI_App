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
            // null 체크 추가
            if (priceChangePercent == null || priceChangePercent.trim().isEmpty()) {
                return 0.0;
            }
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
        tickerData.setMarket(symbol != null ? symbol : "");
        tickerData.setTradePrice(getPrice());

        // 변화율 계산 - 안전하게 계산
        try {
            double changeRate = getPriceChangePercent() / 100.0;
            tickerData.setChangeRate(changeRate);
        } catch (Exception e) {
            // 오류 발생 시 기본값 0 설정
            tickerData.setChangeRate(0.0);
        }

        return tickerData;
    }
}