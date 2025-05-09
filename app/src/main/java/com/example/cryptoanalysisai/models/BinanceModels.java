package com.example.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class BinanceModels {

    // 바이낸스 거래소 정보 클래스
    public static class BinanceExchangeInfo {
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

                // 한글 이름과 영문 이름은 설정하지 않음 (바이낸스 API에 해당 정보 없음)
                // 필요시 매핑 테이블을 사용하여 추가 설정

                return coinInfo;
            }
        }
    }

    // 바이낸스 현재가 정보 클래스
    public static class BinanceTicker {
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

    // 바이낸스 캔들스틱 클래스
    public static class BinanceKline {
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
}