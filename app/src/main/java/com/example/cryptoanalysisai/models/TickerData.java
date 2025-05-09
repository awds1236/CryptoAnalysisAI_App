package com.example.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;

public class TickerData {

    @SerializedName("market")
    private String market;

    @SerializedName("trade_price")
    private double tradePrice;

    @SerializedName("change_rate")
    private double changeRate;

    @SerializedName("acc_trade_price_24h")
    private double accTradePrice24h;

    @SerializedName("acc_trade_volume_24h")
    private double accTradeVolume24h;

    @SerializedName("high_price")
    private double highPrice;

    @SerializedName("low_price")
    private double lowPrice;

    @SerializedName("prev_closing_price")
    private double prevClosingPrice;

    public TickerData() {
        // 기본 생성자
    }

    // Getters and Setters

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public double getTradePrice() {
        return tradePrice;
    }

    public void setTradePrice(double tradePrice) {
        this.tradePrice = tradePrice;
    }

    public double getChangeRate() {
        return changeRate;
    }

    public void setChangeRate(double changeRate) {
        this.changeRate = changeRate;
    }

    public double getAccTradePrice24h() {
        return accTradePrice24h;
    }

    public void setAccTradePrice24h(double accTradePrice24h) {
        this.accTradePrice24h = accTradePrice24h;
    }

    public double getAccTradeVolume24h() {
        return accTradeVolume24h;
    }

    public void setAccTradeVolume24h(double accTradeVolume24h) {
        this.accTradeVolume24h = accTradeVolume24h;
    }

    public double getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(double highPrice) {
        this.highPrice = highPrice;
    }

    public double getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(double lowPrice) {
        this.lowPrice = lowPrice;
    }

    public double getPrevClosingPrice() {
        return prevClosingPrice;
    }

    public void setPrevClosingPrice(double prevClosingPrice) {
        this.prevClosingPrice = prevClosingPrice;
    }

    // 가격 변화 포맷팅 (+3.45% 또는 -2.12%)
    public String getFormattedChangeRate() {
        return String.format("%s%.2f%%", changeRate >= 0 ? "+" : "", changeRate * 100);
    }

    // 가격에 따른 색상 코드 반환 (상승:초록, 하락:빨강)
    public int getChangeColor() {
        return changeRate >= 0 ?
                android.graphics.Color.rgb(76, 175, 80) :  // 초록색
                android.graphics.Color.rgb(244, 67, 54);   // 빨간색
    }

    @Override
    public String toString() {
        return "TickerData{" +
                "market='" + market + '\'' +
                ", tradePrice=" + tradePrice +
                ", changeRate=" + changeRate +
                '}';
    }
}
