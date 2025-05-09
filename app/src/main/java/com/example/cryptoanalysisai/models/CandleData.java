package com.example.cryptoanalysisai.models;

import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.interfaces.datasets.ICandleDataSet;
import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CandleData extends com.github.mikephil.charting.data.CandleData {

    @SerializedName("market")
    private String market;

    @SerializedName("candle_date_time_utc")
    private String candleDateTimeUtc;

    @SerializedName("candle_date_time_kst")
    private String candleDateTimeKst;

    @SerializedName("opening_price")
    private double openingPrice;

    @SerializedName("high_price")
    private double highPrice;

    @SerializedName("low_price")
    private double lowPrice;

    @SerializedName("trade_price")
    private double tradePrice;

    @SerializedName("candle_acc_trade_price")
    private double candleAccTradePrice;

    @SerializedName("candle_acc_trade_volume")
    private double candleAccTradeVolume;

    // 바이낸스 API 호환 필드
    @SerializedName("openTime")
    private Long openTime;

    // 기본 생성자 - 부모 클래스의 기본 생성자 호출
    public CandleData() {
        super();
    }

    // 단일 ICandleDataSet을 받는 생성자
    public CandleData(ICandleDataSet dataSet) {
        super(dataSet);
    }

    // 여러 ICandleDataSet을 받는 생성자
    public CandleData(List<ICandleDataSet> dataSets) {
        super(dataSets);
    }

    // 기존 메서드들...

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getCandleDateTimeUtc() {
        return candleDateTimeUtc;
    }

    public void setCandleDateTimeUtc(String candleDateTimeUtc) {
        this.candleDateTimeUtc = candleDateTimeUtc;
    }

    public String getCandleDateTimeKst() {
        return candleDateTimeKst;
    }

    public void setCandleDateTimeKst(String candleDateTimeKst) {
        this.candleDateTimeKst = candleDateTimeKst;
    }

    public double getOpeningPrice() {
        return openingPrice;
    }

    public void setOpeningPrice(double openingPrice) {
        this.openingPrice = openingPrice;
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

    public double getTradePrice() {
        return tradePrice;
    }

    public void setTradePrice(double tradePrice) {
        this.tradePrice = tradePrice;
    }

    public double getCandleAccTradePrice() {
        return candleAccTradePrice;
    }

    public void setCandleAccTradePrice(double candleAccTradePrice) {
        this.candleAccTradePrice = candleAccTradePrice;
    }

    public double getCandleAccTradeVolume() {
        return candleAccTradeVolume;
    }

    public void setCandleAccTradeVolume(double candleAccTradeVolume) {
        this.candleAccTradeVolume = candleAccTradeVolume;
    }

    public Long getOpenTime() {
        return openTime;
    }

    public void setOpenTime(Long openTime) {
        this.openTime = openTime;

        // openTime이 설정되면 candleDateTimeUtc와 candleDateTimeKst 생성
        if (openTime != null) {
            Date date = new Date(openTime);
            SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            this.candleDateTimeUtc = utcFormat.format(date);

            // KST는 UTC + 9시간
            SimpleDateFormat kstFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            this.candleDateTimeKst = kstFormat.format(new Date(openTime + 9 * 60 * 60 * 1000));
        }
    }

    // 날짜 포맷팅 (MM-dd HH:mm)
    public String getFormattedDate() {
        try {
            String dateTime = candleDateTimeKst != null ? candleDateTimeKst : candleDateTimeUtc;
            if (dateTime == null && openTime != null) {
                Date date = new Date(openTime);
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                dateTime = format.format(date);
            }

            if (dateTime != null) {
                // 날짜 파싱
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                Date date = inputFormat.parse(dateTime);

                // 포맷팅
                SimpleDateFormat outputFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "날짜 오류";
    }

    @Override
    public String toString() {
        return "CandleData{" +
                "market='" + market + '\'' +
                ", candleDateTimeUtc='" + candleDateTimeUtc + '\'' +
                ", openingPrice=" + openingPrice +
                ", highPrice=" + highPrice +
                ", lowPrice=" + lowPrice +
                ", tradePrice=" + tradePrice +
                '}';
    }
}