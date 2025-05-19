package com.coinsense.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalysisResult {

    // 원래 한국어 필드명과 영어 필드명 모두 지원
    @SerializedName(value = "통화단위", alternate = {"currency_unit"})
    private String currencySymbol;

    @SerializedName(value = "거래소", alternate = {"exchange"})
    private String exchange;

    @SerializedName(value = "분석_요약", alternate = {"analysis_summary"})
    private String summary;

    @SerializedName(value = "매수매도_추천", alternate = {"buy_sell_recommendation"})
    private Recommendation recommendation;

    // 단기/중기/장기 전략으로 분리
    @SerializedName(value = "단기_전략", alternate = {"short_term_strategy"})
    private Strategy shortTermStrategy;

    @SerializedName(value = "중기_전략", alternate = {"mid_term_strategy"})
    private Strategy midTermStrategy;

    @SerializedName(value = "장기_전략", alternate = {"long_term_strategy"})
    private Strategy longTermStrategy;

    @SerializedName(value = "시간별_전망", alternate = {"time_based_outlook"})
    private Outlook outlook;

    @SerializedName(value = "기술적_분석", alternate = {"technical_analysis"})
    private TechnicalAnalysis technicalAnalysis;

    @SerializedName(value = "위험_요소", alternate = {"risk_factors"})
    private List<String> riskFactors;

    @SerializedName("coin_symbol")
    private String coinSymbol;

    // 언어 필드 추가
    @SerializedName("language")
    private String language;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCoinSymbol() {
        return coinSymbol;
    }

    public void setCoinSymbol(String coinSymbol) {
        this.coinSymbol = coinSymbol;
    }

    @SerializedName("timestamp")
    private long timestamp;

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // 내부 클래스 정의
    public static class Recommendation {
        @SerializedName(value = "매수_확률", alternate = {"buy_probability"})
        private double buyProbability;

        @SerializedName(value = "매도_확률", alternate = {"sell_probability"})
        private double sellProbability;

        @SerializedName(value = "추천", alternate = {"recommendation"})
        private String recommendation;

        @SerializedName(value = "신뢰도", alternate = {"confidence"})
        private double confidence;

        @SerializedName(value = "근거", alternate = {"reason"})
        private String reason;

        // Getters and Setters
        public double getBuyProbability() {
            return buyProbability;
        }

        public void setBuyProbability(double buyProbability) {
            this.buyProbability = buyProbability;
        }

        public double getSellProbability() {
            return sellProbability;
        }

        public void setSellProbability(double sellProbability) {
            this.sellProbability = sellProbability;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }

        public double getConfidence() {
            return confidence;
        }

        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class Strategy {
        @SerializedName(value = "기간", alternate = {"period"})
        private String period;

        @SerializedName(value = "매수_분할", alternate = {"buying_steps"})
        private List<TradingStep> buySteps;

        @SerializedName(value = "수익실현_목표가", alternate = {"target_prices"})
        private List<Double> targetPrices;

        @SerializedName(value = "손절매_라인", alternate = {"stop_loss"})
        private double stopLoss;

        @SerializedName(value = "리스크_보상_비율", alternate = {"risk_reward_ratio"})
        private double riskRewardRatio;

        @SerializedName(value = "전략_설명", alternate = {"strategy_explanation"})
        private String explanation;

        // Getters and Setters
        public String getPeriod() {
            return period;
        }

        public void setPeriod(String period) {
            this.period = period;
        }

        public List<TradingStep> getBuySteps() {
            return buySteps;
        }

        public void setBuySteps(List<TradingStep> buySteps) {
            this.buySteps = buySteps;
        }

        public List<Double> getTargetPrices() {
            return targetPrices;
        }

        public void setTargetPrices(List<Double> targetPrices) {
            this.targetPrices = targetPrices;
        }

        public double getStopLoss() {
            return stopLoss;
        }

        public void setStopLoss(double stopLoss) {
            this.stopLoss = stopLoss;
        }

        public double getRiskRewardRatio() {
            return riskRewardRatio;
        }

        public void setRiskRewardRatio(double riskRewardRatio) {
            this.riskRewardRatio = riskRewardRatio;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }

        // 매매 단계 클래스
        public static class TradingStep {
            @SerializedName(value = "가격", alternate = {"price"})
            private double price;

            @SerializedName(value = "비율", alternate = {"ratio"})
            private int percentage;

            @SerializedName(value = "설명", alternate = {"description"})
            private String description;

            // Getters and Setters
            public double getPrice() {
                return price;
            }

            public void setPrice(double price) {
                this.price = price;
            }

            public int getPercentage() {
                return percentage;
            }

            public void setPercentage(int percentage) {
                this.percentage = percentage;
            }

            public String getDescription() {
                return description;
            }

            public void setDescription(String description) {
                this.description = description;
            }
        }
    }

    public static class Outlook {
        @SerializedName(value = "단기_24시간", alternate = {"short_term_24h"})
        private String shortTerm;

        @SerializedName(value = "중기_1주일", alternate = {"mid_term_1week"})
        private String midTerm;

        @SerializedName(value = "장기_1개월", alternate = {"long_term_1month"})
        private String longTerm;

        // Getters and Setters
        public String getShortTerm() {
            return shortTerm;
        }

        public void setShortTerm(String shortTerm) {
            this.shortTerm = shortTerm;
        }

        public String getMidTerm() {
            return midTerm;
        }

        public void setMidTerm(String midTerm) {
            this.midTerm = midTerm;
        }

        public String getLongTerm() {
            return longTerm;
        }

        public void setLongTerm(String longTerm) {
            this.longTerm = longTerm;
        }
    }

    public static class TechnicalAnalysis {
        @SerializedName(value = "주요_지지선", alternate = {"key_support_levels"})
        private List<Double> supportLevels;

        @SerializedName(value = "주요_저항선", alternate = {"key_resistance_levels"})
        private List<Double> resistanceLevels;

        @SerializedName(value = "추세_강도", alternate = {"trend_strength"})
        private String trendStrength;

        @SerializedName(value = "주요_패턴", alternate = {"major_pattern"})
        private String pattern;

        @SerializedName(value = "이동평균선_신호", alternate = {"moving_average_signal"})
        private String crossSignal;

        @SerializedName(value = "매수매도_세력_비율", alternate = {"buy_sell_ratio"})
        private double buySellRatio;

        @SerializedName(value = "롱_비율", alternate = {"long_ratio"})
        private double longPercent = 50.0;

        @SerializedName(value = "숏_비율", alternate = {"short_ratio"})
        private double shortPercent = 50.0;

        // Getter와 Setter 추가
        public double getLongPercent() {
            return longPercent;
        }

        public double getShortPercent() {
            return shortPercent;
        }

        // 맵 스타일로 데이터에 접근하기 위한 메서드
        private Map<String, Object> additionalData = new HashMap<>();

        public Object get(String key) {
            return additionalData.get(key);
        }

        public void put(String key, Object value) {
            additionalData.put(key, value);
        }

        // Getters and Setters
        public List<Double> getSupportLevels() {
            return supportLevels;
        }

        public void setSupportLevels(List<Double> supportLevels) {
            this.supportLevels = supportLevels;
        }

        public List<Double> getResistanceLevels() {
            return resistanceLevels;
        }

        public void setResistanceLevels(List<Double> resistanceLevels) {
            this.resistanceLevels = resistanceLevels;
        }

        public String getTrendStrength() {
            return trendStrength;
        }

        public void setTrendStrength(String trendStrength) {
            this.trendStrength = trendStrength;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public String getCrossSignal() {
            return crossSignal;
        }

        public void setCrossSignal(String crossSignal) {
            this.crossSignal = crossSignal;
        }

        public double getBuySellRatio() {
            return buySellRatio;
        }

        public void setBuySellRatio(double buySellRatio) {
            this.buySellRatio = buySellRatio;
        }
    }

    // Main class Getters and Setters
    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(Recommendation recommendation) {
        this.recommendation = recommendation;
    }

    public Strategy getShortTermStrategy() {
        return shortTermStrategy;
    }

    public void setShortTermStrategy(Strategy shortTermStrategy) {
        this.shortTermStrategy = shortTermStrategy;
    }

    public Strategy getMidTermStrategy() {
        return midTermStrategy;
    }

    public void setMidTermStrategy(Strategy midTermStrategy) {
        this.midTermStrategy = midTermStrategy;
    }

    public Strategy getLongTermStrategy() {
        return longTermStrategy;
    }

    public void setLongTermStrategy(Strategy longTermStrategy) {
        this.longTermStrategy = longTermStrategy;
    }

    public Outlook getOutlook() {
        return outlook;
    }

    public void setOutlook(Outlook outlook) {
        this.outlook = outlook;
    }

    public TechnicalAnalysis getTechnicalAnalysis() {
        return technicalAnalysis;
    }

    public void setTechnicalAnalysis(TechnicalAnalysis technicalAnalysis) {
        this.technicalAnalysis = technicalAnalysis;
    }

    public List<String> getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(List<String> riskFactors) {
        this.riskFactors = riskFactors;
    }
}