package com.example.cryptoanalysisai.models.firebase;

import com.example.cryptoanalysisai.models.AnalysisResult;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.List;

public class FirestoreAnalysisResult {
    @DocumentId
    private String documentId;

    private String coinSymbol;
    private String coinName;
    private String market;
    private String exchange;

    @ServerTimestamp
    private Date createdAt;

    private long timestamp;
    private double currentPrice;
    private double buyProbability;
    private double sellProbability;
    private String recommendation;
    private double confidence;
    private String summary;

    // 매매 전략
    private List<Double> targetPrices;
    private double stopLoss;
    private double riskRewardRatio;
    private String strategyExplanation;

    // 시간별 전망
    private String shortTermOutlook;
    private String midTermOutlook;
    private String longTermOutlook;

    // 기술적 분석
    private List<Double> supportLevels;
    private List<Double> resistanceLevels;
    private String trendStrength;
    private String pattern;

    // 위험 요소
    private List<String> riskFactors;

    // 통화 단위
    private String currencySymbol;

    public FirestoreAnalysisResult() {
        // Firestore를 위한 빈 생성자
    }

    @Exclude
    public static FirestoreAnalysisResult fromAnalysisResult(AnalysisResult result, String coinSymbol, String coinName, String market, String exchange) {
        FirestoreAnalysisResult firestoreResult = new FirestoreAnalysisResult();

        firestoreResult.setCoinSymbol(coinSymbol);
        firestoreResult.setCoinName(coinName);
        firestoreResult.setMarket(market);
        firestoreResult.setExchange(exchange);
        firestoreResult.setTimestamp(System.currentTimeMillis());
        firestoreResult.setCurrencySymbol(result.getCurrencySymbol());
        firestoreResult.setSummary(result.getSummary());

        // 추천 정보
        if (result.getRecommendation() != null) {
            firestoreResult.setBuyProbability(result.getRecommendation().getBuyProbability());
            firestoreResult.setSellProbability(result.getRecommendation().getSellProbability());
            firestoreResult.setRecommendation(result.getRecommendation().getRecommendation());
            firestoreResult.setConfidence(result.getRecommendation().getConfidence());
        }

        // 매매 전략
        if (result.getStrategy() != null) {
            firestoreResult.setTargetPrices(result.getStrategy().getTargetPrices());
            firestoreResult.setStopLoss(result.getStrategy().getStopLoss());
            firestoreResult.setRiskRewardRatio(result.getStrategy().getRiskRewardRatio());
            firestoreResult.setStrategyExplanation(result.getStrategy().getExplanation());
        }

        // 시간별 전망
        if (result.getOutlook() != null) {
            firestoreResult.setShortTermOutlook(result.getOutlook().getShortTerm());
            firestoreResult.setMidTermOutlook(result.getOutlook().getMidTerm());
            firestoreResult.setLongTermOutlook(result.getOutlook().getLongTerm());
        }

        // 기술적 분석
        if (result.getTechnicalAnalysis() != null) {
            firestoreResult.setSupportLevels(result.getTechnicalAnalysis().getSupportLevels());
            firestoreResult.setResistanceLevels(result.getTechnicalAnalysis().getResistanceLevels());
            firestoreResult.setTrendStrength(result.getTechnicalAnalysis().getTrendStrength());
            firestoreResult.setPattern(result.getTechnicalAnalysis().getPattern());
        }

        // 위험 요소
        firestoreResult.setRiskFactors(result.getRiskFactors());

        return firestoreResult;
    }

    @Exclude
    public AnalysisResult toAnalysisResult() {
        AnalysisResult result = new AnalysisResult();

        // 기본 정보
        result.setCurrencySymbol(currencySymbol);
        result.setExchange(exchange);
        result.setSummary(summary);

        // 추천 정보
        AnalysisResult.Recommendation recommendationObj = new AnalysisResult.Recommendation();
        recommendationObj.setBuyProbability(buyProbability);
        recommendationObj.setSellProbability(sellProbability);
        recommendationObj.setRecommendation(recommendation);
        recommendationObj.setConfidence(confidence);
        result.setRecommendation(recommendationObj);

        // 매매 전략
        AnalysisResult.Strategy strategyObj = new AnalysisResult.Strategy();
        strategyObj.setTargetPrices(targetPrices);
        strategyObj.setStopLoss(stopLoss);
        strategyObj.setRiskRewardRatio(riskRewardRatio);
        strategyObj.setExplanation(strategyExplanation);
        result.setStrategy(strategyObj);

        // 시간별 전망
        AnalysisResult.Outlook outlookObj = new AnalysisResult.Outlook();
        outlookObj.setShortTerm(shortTermOutlook);
        outlookObj.setMidTerm(midTermOutlook);
        outlookObj.setLongTerm(longTermOutlook);
        result.setOutlook(outlookObj);

        // 기술적 분석
        AnalysisResult.TechnicalAnalysis technicalAnalysisObj = new AnalysisResult.TechnicalAnalysis();
        technicalAnalysisObj.setSupportLevels(supportLevels);
        technicalAnalysisObj.setResistanceLevels(resistanceLevels);
        technicalAnalysisObj.setTrendStrength(trendStrength);
        technicalAnalysisObj.setPattern(pattern);
        result.setTechnicalAnalysis(technicalAnalysisObj);

        // 위험 요소
        result.setRiskFactors(riskFactors);

        return result;
    }

    // Getters and Setters
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
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

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
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

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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

    public List<String> getRiskFactors() {
        return riskFactors;
    }

    public void setRiskFactors(List<String> riskFactors) {
        this.riskFactors = riskFactors;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }
}