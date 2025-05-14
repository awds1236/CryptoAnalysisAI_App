package com.coinsense.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class BinanceExchangeInfo {
    @SerializedName("timezone")
    private String timezone;

    @SerializedName("serverTime")
    private long serverTime;

    @SerializedName("symbols")
    private List<SymbolInfo> symbols;

    public String getTimezone() {
        return timezone;
    }

    public long getServerTime() {
        return serverTime;
    }

    public List<SymbolInfo> getSymbols() {
        return symbols;
    }

    // 심볼 정보 클래스
    public static class SymbolInfo {
        @SerializedName("symbol")
        private String symbol;

        @SerializedName("status")
        private String status;

        @SerializedName("baseAsset")
        private String baseAsset;

        @SerializedName("quoteAsset")
        private String quoteAsset;

        @SerializedName("baseAssetPrecision")
        private int baseAssetPrecision;

        @SerializedName("quotePrecision")
        private int quotePrecision;

        public String getSymbol() {
            return symbol;
        }

        public String getStatus() {
            return status;
        }

        public String getBaseAsset() {
            return baseAsset;
        }

        public String getQuoteAsset() {
            return quoteAsset;
        }

        public int getBaseAssetPrecision() {
            return baseAssetPrecision;
        }

        public int getQuotePrecision() {
            return quotePrecision;
        }

        // 업비트 API 형식으로 변환하는 메서드
        public CoinInfo toUpbitFormat() {
            CoinInfo coinInfo = new CoinInfo();
            coinInfo.setMarket(symbol);
            coinInfo.setBaseAsset(baseAsset);
            coinInfo.setQuoteAsset(quoteAsset);

            return coinInfo;
        }
    }
}