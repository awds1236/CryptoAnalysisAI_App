package com.example.cryptoanalysisai.models;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {

    private static final String TAG = "AnalysisResult";

    @SerializedName("id")
    private int id;

    @SerializedName("coin_symbol")
    private String coinSymbol;

    @SerializedName("coin_name")
    private String coinName;

    @SerializedName("market")
    private String market;

    @SerializedName("exchange")
    private String exchange;

    @SerializedName("timestamp")
    private long timestamp;

    @SerializedName("current_price")
    private double currentPrice;

    @SerializedName("change_rate")
    private double changeRate;

    @SerializedName("currency_symbol")
    private String currencySymbol;

    @SerializedName("summary")
    private String summary;

    @SerializedName("buy_probability")
    private double buyProbability;

    @SerializedName("sell_probability")
    private double sellProbability;

    @SerializedName("recommendation")
    private String recommendation;

    @SerializedName("confidence")
    private double confidence;

    @SerializedName("target_prices")
    private String targetPricesJson;

    @SerializedName("stop_loss")
    private double stopLoss;

    @SerializedName("risk_reward_ratio")
    private double riskRewardRatio;

    @SerializedName("strategy_explanation")
    private String strategyExplanation;

    @SerializedName("short_term_outlook")
    private String shortTermOutlook;

    @SerializedName("mid_term_outlook")
    private String midTermOutlook;

    @SerializedName("long_term_outlook")
    private String longTermOutlook;

    @SerializedName("support_levels")
    private String supportLevelsJson;

    @SerializedName("resistance_levels")
    private String resistanceLevelsJson;

    @SerializedName("trend_strength")
    private String trendStrength;

    @SerializedName("pattern")
    private String pattern;

    @SerializedName("risk_factors")
    private String riskFactorsJson;

    @SerializedName("created_at")
    private String createdAt;

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCoinSymbol() {
        return coinSymbol;
    }

    public void setCoinSymbol(String coinSymbol) {
        this.coinSymbol = coinSymbol;
    }

    public String getCoinName() {
        return coinName;
    }

    public void setCoinName(String coinName) {
        this.coinName = coinName;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public double getChangeRate() {
        return changeRate;
    }

    public void setChangeRate(double changeRate) {
        this.changeRate = changeRate;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

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

    public String getStrategyExplanation() {
        return strategyExplanation;
    }

    public void setStrategyExplanation(String strategyExplanation) {
        this.strategyExplanation = strategyExplanation;
    }

    public String getShortTermOutlook() {
        return shortTermOutlook;
    }

    public void setShortTermOutlook(String shortTermOutlook) {
        this.shortTermOutlook = shortTermOutlook;
    }

    public String getMidTermOutlook() {
        return midTermOutlook;
    }

    public void setMidTermOutlook(String midTermOutlook) {
        this.midTermOutlook = midTermOutlook;
    }

    public String getLongTermOutlook() {
        return longTermOutlook;
    }

    public void setLongTermOutlook(String longTermOutlook) {
        this.longTermOutlook = longTermOutlook;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    // JSON 파싱 메서드들
    public List<Double> getTargetPrices() {
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Double>>(){}.getType();
            return gson.fromJson(targetPricesJson, type);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "타겟 가격 파싱 오류: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Double> getSupportLevels() {
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Double>>(){}.getType();
            return gson.fromJson(supportLevelsJson, type);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "지지선 파싱 오류: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Double> getResistanceLevels() {
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<List<Double>>(){}.getType();
            return gson.fromJson(resistanceLevelsJson, type);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "저항선 파싱 오류: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> getRiskFactors() {
        try {
            Gson gson = new Gson();
            Type type = new TypeToken<List<String>>(){}.getType();
            List<String> riskFactors = gson.fromJson(riskFactorsJson, type);

            // JSON 이스케이프 문자 처리
            List<String> decodedRiskFactors = new ArrayList<>();
            for (String risk : riskFactors) {
                // UTF-8 인코딩된 문자열을 다시 디코딩
                decodedRiskFactors.add(decodeUnicodeEscapes(risk));
            }
            return decodedRiskFactors;
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "위험 요소 파싱 오류: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // 유니코드 이스케이프 시퀀스를 실제 문자로 변환
    private String decodeUnicodeEscapes(String input) {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            if (input.charAt(i) == '\\' && i + 1 < input.length() && input.charAt(i + 1) == 'u') {
                if (i + 5 < input.length()) {
                    String hex = input.substring(i + 2, i + 6);
                    try {
                        int code = Integer.parseInt(hex, 16);
                        builder.append((char) code);
                        i += 6;
                        continue;
                    } catch (NumberFormatException ignored) {
                        // 유효한 유니코드가 아니면 그냥 원래 문자로 처리
                    }
                }
            }
            // 일반 문자 또는 유효하지 않은 이스케이프 시퀀스
            builder.append(input.charAt(i));
            i++;
        }
        return builder.toString();
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

    @Override
    public String toString() {
        return "AnalysisResult{" +
                "id=" + id +
                ", coinSymbol='" + coinSymbol + '\'' +
                ", market='" + market + '\'' +
                ", currentPrice=" + currentPrice +
                ", recommendation='" + recommendation + '\'' +
                '}';
    }
}