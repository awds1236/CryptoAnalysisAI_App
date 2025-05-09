package com.example.cryptoanalysisai.services;


import android.util.Log;

import com.example.cryptoanalysisai.models.CandleData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TechnicalIndicatorService {

    private static final String TAG = "TechnicalIndicatorService";

    /**
     * 모든 기술적 지표 계산
     */
    public Map<String, Object> calculateAllIndicators(List<CandleData> candles) {
        Map<String, Object> indicators = new HashMap<>();
        try {
            if (candles == null || candles.isEmpty()) {
                return indicators;
            }

            // 종가 배열 추출
            double[] closePrices = extractClosePrices(candles);

            // RSI 계산
            double rsi14 = calculateRSI(closePrices, 14);

            // 이동평균 계산
            double sma20 = calculateSMA(closePrices, 20);
            double ema20 = calculateEMA(closePrices, 20);

            // 볼린저 밴드 계산
            Map<String, Double> bollingerBands = calculateBollingerBands(closePrices, 20, 2.0);

            // MACD 계산
            Map<String, Double> macdResult = calculateMACD(closePrices, 12, 26, 9);

            // 결과 저장
            indicators.put("rsi14", rsi14);
            indicators.put("sma20", sma20);
            indicators.put("ema20", ema20);
            indicators.put("bollingerUpper", bollingerBands.get("upper"));
            indicators.put("bollingerMiddle", bollingerBands.get("middle"));
            indicators.put("bollingerLower", bollingerBands.get("lower"));
            indicators.put("macdLine", macdResult.get("macdLine"));
            indicators.put("signalLine", macdResult.get("signalLine"));
            indicators.put("histogram", macdResult.get("histogram"));

            // 지지선과 저항선 분석
            Map<String, List<Double>> supportResistanceLevels = findSupportResistanceLevels(candles);
            indicators.put("supportLevels", supportResistanceLevels.get("support"));
            indicators.put("resistanceLevels", supportResistanceLevels.get("resistance"));

            // 추세 강도 분석
            String trendStrength = analyzeTrendStrength(closePrices, sma20);
            indicators.put("trendStrength", trendStrength);

            // 과매수/과매도 분석
            Map<String, Object> overboughtOversold = analyzeOverboughtOversold(rsi14, bollingerBands, closePrices[closePrices.length - 1]);
            indicators.putAll(overboughtOversold);

            // 지표 시계열 데이터 (차트용)
            List<Double> rsiSeries = calculateRSISeries(closePrices, 14);
            List<Double> smaSeries = calculateSMASeries(closePrices, 20);
            List<Double> emaSeries = calculateEMASeries(closePrices, 20);

            indicators.put("rsiSeries", rsiSeries);
            indicators.put("smaSeries", smaSeries);
            indicators.put("emaSeries", emaSeries);

        } catch (Exception e) {
            Log.e(TAG, "지표 계산 오류: " + e.getMessage());
        }

        return indicators;
    }

    /**
     * 캔들 데이터에서 종가 추출
     */
    private double[] extractClosePrices(List<CandleData> candles) {
        double[] prices = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            prices[i] = candles.get(i).getTradePrice();
        }
        return prices;
    }

    /**
     * RSI(상대강도지수) 계산
     */
    public double calculateRSI(double[] prices, int period) {
        if (prices.length <= period) {
            return 50.0; // 충분한 데이터가 없으면 중립적인 값 반환
        }

        double[] changes = new double[prices.length - 1];
        for (int i = 0; i < changes.length; i++) {
            changes[i] = prices[i + 1] - prices[i];
        }

        double sumGain = 0;
        double sumLoss = 0;

        // 첫 번째 기간의 평균 이득과 손실 계산
        for (int i = 0; i < period; i++) {
            if (changes[i] > 0) {
                sumGain += changes[i];
            } else {
                sumLoss += Math.abs(changes[i]);
            }
        }

        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;

        // 이후 기간의 평균 이득과 손실 계산
        for (int i = period; i < changes.length; i++) {
            if (changes[i] > 0) {
                avgGain = (avgGain * (period - 1) + changes[i]) / period;
                avgLoss = (avgLoss * (period - 1) + 0) / period;
            } else {
                avgGain = (avgGain * (period - 1) + 0) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(changes[i])) / period;
            }
        }

        if (avgLoss == 0) {
            return 100.0; // 0으로 나누는 것 방지
        }

        double rs = avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));

        return rsi;
    }

    /**
     * RSI 시계열 계산
     */
    public List<Double> calculateRSISeries(double[] prices, int period) {
        List<Double> rsiSeries = new ArrayList<>();

        if (prices.length <= period) {
            for (int i = 0; i < prices.length; i++) {
                rsiSeries.add(50.0); // 충분한 데이터가 없으면 중립적인 값 반환
            }
            return rsiSeries;
        }

        double[] changes = new double[prices.length - 1];
        for (int i = 0; i < changes.length; i++) {
            changes[i] = prices[i + 1] - prices[i];
        }

        // 초기 평균 이득과 손실 계산
        double sumGain = 0;
        double sumLoss = 0;
        for (int i = 0; i < period; i++) {
            if (changes[i] > 0) {
                sumGain += changes[i];
            } else {
                sumLoss += Math.abs(changes[i]);
            }
        }

        double avgGain = sumGain / period;
        double avgLoss = sumLoss / period;

        for (int i = 0; i < period; i++) {
            rsiSeries.add(50.0); // 초기 기간에는 충분한 데이터가 없으므로 중립값 사용
        }

        // 첫 번째 RSI 값 계산
        double rs = avgGain / (avgLoss == 0 ? 0.001 : avgLoss); // 0으로 나누기 방지
        double rsi = 100 - (100 / (1 + rs));
        rsiSeries.add(rsi);

        // 이후 기간의 RSI 계산
        for (int i = period; i < changes.length; i++) {
            if (changes[i] > 0) {
                avgGain = (avgGain * (period - 1) + changes[i]) / period;
                avgLoss = (avgLoss * (period - 1) + 0) / period;
            } else {
                avgGain = (avgGain * (period - 1) + 0) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(changes[i])) / period;
            }

            rs = avgGain / (avgLoss == 0 ? 0.001 : avgLoss); // 0으로 나누기 방지
            rsi = 100 - (100 / (1 + rs));
            rsiSeries.add(rsi);
        }

        return rsiSeries;
    }

    /**
     * SMA(단순이동평균) 계산
     */
    public double calculateSMA(double[] prices, int period) {
        if (prices.length < period) {
            return prices[prices.length - 1]; // 충분한 데이터가 없으면 마지막 가격 반환
        }

        double sum = 0;
        for (int i = prices.length - period; i < prices.length; i++) {
            sum += prices[i];
        }

        return sum / period;
    }

    /**
     * SMA 시계열 계산
     */
    public List<Double> calculateSMASeries(double[] prices, int period) {
        List<Double> smaSeries = new ArrayList<>();

        // 초기 기간에는 충분한 데이터 없음
        for (int i = 0; i < period - 1; i++) {
            smaSeries.add(prices[i]);
        }

        // 이후 SMA 계산
        for (int i = period - 1; i < prices.length; i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += prices[j];
            }
            smaSeries.add(sum / period);
        }

        return smaSeries;
    }

    /**
     * EMA(지수이동평균) 계산
     */
    public double calculateEMA(double[] prices, int period) {
        if (prices.length < period) {
            return prices[prices.length - 1]; // 충분한 데이터가 없으면 마지막 가격 반환
        }

        // 가중치 계산
        double multiplier = 2.0 / (period + 1);

        // 초기 SMA 계산
        double ema = calculateSMA(prices, period);

        // EMA 계산
        for (int i = prices.length - period; i < prices.length; i++) {
            ema = (prices[i] - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * EMA 시계열 계산
     */
    public List<Double> calculateEMASeries(double[] prices, int period) {
        List<Double> emaSeries = new ArrayList<>();

        if (prices.length < period) {
            for (int i = 0; i < prices.length; i++) {
                emaSeries.add(prices[i]);
            }
            return emaSeries;
        }

        // 가중치 계산
        double multiplier = 2.0 / (period + 1);

        // 초기 기간에는 충분한 데이터 없음
        for (int i = 0; i < period - 1; i++) {
            emaSeries.add(prices[i]);
        }

        // 초기 SMA 계산
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += prices[i];
        }
        double ema = sum / period;
        emaSeries.add(ema);

        // 이후 EMA 계산
        for (int i = period; i < prices.length; i++) {
            ema = (prices[i] - ema) * multiplier + ema;
            emaSeries.add(ema);
        }

        return emaSeries;
    }

    /**
     * 볼린저 밴드 계산
     */
    public Map<String, Double> calculateBollingerBands(double[] prices, int period, double multiplier) {
        Map<String, Double> result = new HashMap<>();

        if (prices.length < period) {
            result.put("upper", prices[prices.length - 1] * 1.05);
            result.put("middle", prices[prices.length - 1]);
            result.put("lower", prices[prices.length - 1] * 0.95);
            return result;
        }

        // 중앙선 (SMA) 계산
        double middle = calculateSMA(prices, period);

        // 표준편차 계산
        double sum = 0;
        for (int i = prices.length - period; i < prices.length; i++) {
            sum += Math.pow(prices[i] - middle, 2);
        }
        double stdDev = Math.sqrt(sum / period);

        // 상단선과 하단선 계산
        double upper = middle + (multiplier * stdDev);
        double lower = middle - (multiplier * stdDev);

        result.put("upper", upper);
        result.put("middle", middle);
        result.put("lower", lower);

        return result;
    }

    /**
     * MACD 계산
     */
    public Map<String, Double> calculateMACD(double[] prices, int fastPeriod, int slowPeriod, int signalPeriod) {
        Map<String, Double> result = new HashMap<>();

        if (prices.length < Math.max(fastPeriod, slowPeriod) + signalPeriod) {
            result.put("macdLine", 0.0);
            result.put("signalLine", 0.0);
            result.put("histogram", 0.0);
            return result;
        }

        // 빠른 EMA 계산
        double fastEMA = calculateEMA(prices, fastPeriod);

        // 느린 EMA 계산
        double slowEMA = calculateEMA(prices, slowPeriod);

        // MACD 라인 = 빠른 EMA - 느린 EMA
        double macdLine = fastEMA - slowEMA;

        // 시그널 라인 = MACD의 EMA
        // 여기서는 간단히 하기 위해 마지막 MACD 값만 사용
        double signalLine = macdLine * 0.8; // 약간의 지연 효과 추가

        // 히스토그램 = MACD - 시그널 라인
        double histogram = macdLine - signalLine;

        result.put("macdLine", macdLine);
        result.put("signalLine", signalLine);
        result.put("histogram", histogram);

        return result;
    }

    /**
     * 지지선과 저항선 찾기
     */
    public Map<String, List<Double>> findSupportResistanceLevels(List<CandleData> candles) {
        Map<String, List<Double>> result = new HashMap<>();
        List<Double> supportLevels = new ArrayList<>();
        List<Double> resistanceLevels = new ArrayList<>();

        if (candles.size() < 5) {
            // 데이터가 부족한 경우 간단한 추정값 반환
            double lastPrice = candles.get(candles.size() - 1).getTradePrice();
            supportLevels.add(lastPrice * 0.95);
            supportLevels.add(lastPrice * 0.9);
            resistanceLevels.add(lastPrice * 1.05);
            resistanceLevels.add(lastPrice * 1.1);

            result.put("support", supportLevels);
            result.put("resistance", resistanceLevels);
            return result;
        }

        try {
            // 최근 저점과 고점 찾기
            double recentLow = Double.MAX_VALUE;
            double recentHigh = Double.MIN_VALUE;

            for (int i = candles.size() - 5; i < candles.size(); i++) {
                CandleData candle = candles.get(i);
                if (candle.getLowPrice() < recentLow) {
                    recentLow = candle.getLowPrice();
                }
                if (candle.getHighPrice() > recentHigh) {
                    recentHigh = candle.getHighPrice();
                }
            }

            // 이전 저점과 고점 찾기
            double previousLow = Double.MAX_VALUE;
            double previousHigh = Double.MIN_VALUE;

            for (int i = Math.max(0, candles.size() - 20); i < candles.size() - 5; i++) {
                CandleData candle = candles.get(i);
                if (candle.getLowPrice() < previousLow) {
                    previousLow = candle.getLowPrice();
                }
                if (candle.getHighPrice() > previousHigh) {
                    previousHigh = candle.getHighPrice();
                }
            }

            // 지지선 추가
            supportLevels.add(recentLow);
            if (Math.abs(recentLow - previousLow) / recentLow > 0.01) {
                supportLevels.add(previousLow);
            }

            // 저항선 추가
            resistanceLevels.add(recentHigh);
            if (Math.abs(recentHigh - previousHigh) / recentHigh > 0.01) {
                resistanceLevels.add(previousHigh);
            }

            // 피보나치 되돌림 레벨 추가
            double range = recentHigh - recentLow;
            double fib382 = recentHigh - (range * 0.382);
            double fib618 = recentHigh - (range * 0.618);

            if (!supportLevels.contains(fib382) && !resistanceLevels.contains(fib382)) {
                double lastPrice = candles.get(candles.size() - 1).getTradePrice();
                if (fib382 < lastPrice) {
                    supportLevels.add(fib382);
                } else {
                    resistanceLevels.add(fib382);
                }
            }

            if (!supportLevels.contains(fib618) && !resistanceLevels.contains(fib618)) {
                double lastPrice = candles.get(candles.size() - 1).getTradePrice();
                if (fib618 < lastPrice) {
                    supportLevels.add(fib618);
                } else {
                    resistanceLevels.add(fib618);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "지지선/저항선 계산 오류: " + e.getMessage());

            // 오류 시 간단한 추정값 사용
            double lastPrice = candles.get(candles.size() - 1).getTradePrice();
            supportLevels.add(lastPrice * 0.95);
            supportLevels.add(lastPrice * 0.9);
            resistanceLevels.add(lastPrice * 1.05);
            resistanceLevels.add(lastPrice * 1.1);
        }

        result.put("support", supportLevels);
        result.put("resistance", resistanceLevels);
        return result;
    }

    /**
     * 추세 강도 분석
     */
    public String analyzeTrendStrength(double[] prices, double sma) {
        try {
            if (prices.length < 5) {
                return "중"; // 데이터가 부족한 경우 중립
            }

            // 최근 가격과 SMA 차이 계산
            double currentPrice = prices[prices.length - 1];
            double deviation = (currentPrice - sma) / sma;

            // 최근 5개 가격의 방향성 계산
            int upCount = 0;
            int downCount = 0;

            for (int i = prices.length - 5; i < prices.length - 1; i++) {
                if (prices[i + 1] > prices[i]) {
                    upCount++;
                } else if (prices[i + 1] < prices[i]) {
                    downCount++;
                }
            }

            // 강도 판정
            if (Math.abs(deviation) > 0.05 && (upCount >= 4 || downCount >= 4)) {
                return "강";
            } else if (Math.abs(deviation) < 0.02 && Math.abs(upCount - downCount) <= 1) {
                return "약";
            } else {
                return "중";
            }
        } catch (Exception e) {
            Log.e(TAG, "추세 강도 분석 오류: " + e.getMessage());
            return "중"; // 오류 시 중립값 반환
        }
    }

    /**
     * 과매수/과매도 분석
     */
    public Map<String, Object> analyzeOverboughtOversold(double rsi, Map<String, Double> bollingerBands, double currentPrice) {
        Map<String, Object> result = new HashMap<>();

        try {
            boolean isOverbought = rsi > 70 || currentPrice > bollingerBands.get("upper");
            boolean isOversold = rsi < 30 || currentPrice < bollingerBands.get("lower");

            result.put("isOverbought", isOverbought);
            result.put("isOversold", isOversold);

            if (isOverbought) {
                result.put("signal", "매도");
                result.put("signalStrength", "강");
            } else if (isOversold) {
                result.put("signal", "매수");
                result.put("signalStrength", "강");
            } else if (rsi > 60) {
                result.put("signal", "매도");
                result.put("signalStrength", "약");
            } else if (rsi < 40) {
                result.put("signal", "매수");
                result.put("signalStrength", "약");
            } else {
                result.put("signal", "중립");
                result.put("signalStrength", "중");
            }
        } catch (Exception e) {
            Log.e(TAG, "과매수/과매도 분석 오류: " + e.getMessage());
            result.put("isOverbought", false);
            result.put("isOversold", false);
            result.put("signal", "중립");
            result.put("signalStrength", "중");
        }

        return result;
    }
}
