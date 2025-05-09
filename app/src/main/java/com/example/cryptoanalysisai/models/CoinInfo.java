package com.example.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;

public class CoinInfo {

    @SerializedName("market")
    private String market;

    @SerializedName("korean_name")
    private String koreanName;

    @SerializedName("english_name")
    private String englishName;

    // 바이낸스 API 호환
    @SerializedName("baseAsset")
    private String baseAsset;

    @SerializedName("quoteAsset")
    private String quoteAsset;

    private String symbol; // 심볼 추출용 (KRW-BTC -> BTC)
    private double currentPrice;
    private double priceChange;

    public CoinInfo() {
        // 기본 생성자
    }

    public CoinInfo(String market, String koreanName, String englishName) {
        this.market = market;
        this.koreanName = koreanName;
        this.englishName = englishName;

        // 마켓 코드에서 심볼 추출
        if (market != null) {
            if (market.contains("-")) {
                this.symbol = market.split("-")[1];
            } else if (market.endsWith("USDT")) {
                this.symbol = market.replace("USDT", "");
            } else {
                this.symbol = market;
            }
        }
    }

    // Getters and Setters

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;

        // 마켓 코드에서 심볼 추출
        if (market != null) {
            if (market.contains("-")) {
                this.symbol = market.split("-")[1];
            } else if (market.endsWith("USDT")) {
                this.symbol = market.replace("USDT", "");
            } else {
                this.symbol = market;
            }
        }
    }

    public String getKoreanName() {
        return koreanName;
    }

    public void setKoreanName(String koreanName) {
        this.koreanName = koreanName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }

    public String getBaseAsset() {
        return baseAsset;
    }

    public void setBaseAsset(String baseAsset) {
        this.baseAsset = baseAsset;
    }

    public String getQuoteAsset() {
        return quoteAsset;
    }

    public void setQuoteAsset(String quoteAsset) {
        this.quoteAsset = quoteAsset;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getPriceChange() {
        return priceChange;
    }

    public void setPriceChange(double priceChange) {
        this.priceChange = priceChange;
    }

    // 업비트용 코인 이름 얻기 (한글 또는 영문)
    public String getDisplayName() {
        if (koreanName != null && !koreanName.isEmpty()) {
            return koreanName;
        } else if (baseAsset != null && !baseAsset.isEmpty()) {
            return baseAsset;
        } else {
            return symbol != null ? symbol : market;
        }
    }

    // 화폐 단위 얻기 (KRW 또는 USDT)
    public String getCurrencyUnit() {
        if (market != null) {
            if (market.startsWith("KRW-")) {
                return "KRW";
            } else if (market.endsWith("USDT")) {
                return "USDT";
            }
        } else if (quoteAsset != null) {
            return quoteAsset;
        }
        return "KRW"; // 기본값
    }

    // 화폐 단위 기호 얻기 (₩ 또는 $)
    public String getCurrencySymbol() {
        String unit = getCurrencyUnit();
        return "KRW".equals(unit) ? "₩" : "$";
    }

    // 가격 포맷팅 (₩55,000,000 또는 $45,000.00)
    public String getFormattedPrice() {
        String symbol = getCurrencySymbol();
        if ("₩".equals(symbol)) {
            return String.format("%s%,.0f", symbol, currentPrice);
        } else {
            return String.format("%s%,.2f", symbol, currentPrice);
        }
    }

    // 가격 변화율 포맷팅 (+3.45% 또는 -2.12%)
    public String getFormattedPriceChange() {
        return String.format("%s%.2f%%", priceChange >= 0 ? "+" : "", priceChange * 100);
    }

    @Override
    public String toString() {
        return "CoinInfo{" +
                "market='" + market + '\'' +
                ", koreanName='" + koreanName + '\'' +
                ", englishName='" + englishName + '\'' +
                ", symbol='" + symbol + '\'' +
                ", currentPrice=" + currentPrice +
                ", priceChange=" + priceChange +
                '}';
    }
}