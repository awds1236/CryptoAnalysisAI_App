package com.example.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class AnalysisResult {

    @SerializedName("통화단위")
    private String currencySymbol;

    @SerializedName("거래소")
    private String exchange;

    @SerializedName("분석_요약")
    private String summary;

    @SerializedName("매수매도_추천")
    private Recommendation recommendation;

    // 단기/중기/장기 전략으로 분리
    @SerializedName("단기_전략")
    private Strategy shortTermStrategy;

    @SerializedName("중기_전략")
    private Strategy midTermStrategy;

    @SerializedName("장기_전략")
    private Strategy longTermStrategy;

    @SerializedName("시간별_전망")
    private Outlook outlook;

    @SerializedName("기술적_분석")
    private TechnicalAnalysis technicalAnalysis;

    @SerializedName("위험_요소")
    private List<String> riskFactors;

    @SerializedName("coin_symbol")
    private String coinSymbol;

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
        @SerializedName("매수_확률")
        private double buyProbability;

        @SerializedName("매도_확률")
        private double sellProbability;

        @SerializedName("추천")
        private String recommendation;

        @SerializedName("신뢰도")
        private double confidence;

        @SerializedName("근거")
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
        @SerializedName("기간")
        private String period;

        @SerializedName("매수_분할")
        private List<TradingStep> buySteps;

        @SerializedName("수익실현_목표가")
        private List<Double> targetPrices;

        @SerializedName("손절매_라인")
        private double stopLoss;

        @SerializedName("리스크_보상_비율")
        private double riskRewardRatio;

        @SerializedName("전략_설명")
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
            @SerializedName("가격")
            private double price;

            @SerializedName("비율")
            private int percentage;

            @SerializedName("설명")
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
        @SerializedName("단기_24시간")
        private String shortTerm;

        @SerializedName("중기_1주일")
        private String midTerm;

        @SerializedName("장기_1개월")
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
        @SerializedName("주요_지지선")
        private List<Double> supportLevels;

        @SerializedName("주요_저항선")
        private List<Double> resistanceLevels;

        @SerializedName("추세_강도")
        private String trendStrength;

        @SerializedName("주요_패턴")
        private String pattern;

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