package com.example.cryptoanalysisai.services;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.utils.Constants;

import org.json.JSONArray;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AwsRdsService {
    private static final String TAG = "AwsRdsService";

    // AWS RDS 설정
    private static final String DB_URL = Constants.DB_URL;
    private static final String DB_USER = Constants.DB_USER;
    private static final String DB_PASS = Constants.DB_PASS;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static AwsRdsService instance;

    private AwsRdsService() {
        // 싱글톤 패턴
    }

    public static synchronized AwsRdsService getInstance() {
        if (instance == null) {
            instance = new AwsRdsService();
        }
        return instance;
    }

    /**
     * 데이터베이스 연결 얻기
     */
    private Connection getConnection() throws SQLException {
        try {
            // MariaDB JDBC 드라이버 로드
            Class.forName("org.mariadb.jdbc.Driver");

            // DB 연결
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "JDBC 드라이버 로드 실패: " + e.getMessage());
            throw new SQLException("드라이버 로드 실패", e);
        }
    }

    /**
     * 특정 코인의 최신 분석 결과 가져오기
     */
    public void getLatestAnalysis(String coinSymbol, String exchange, OnAnalysisRetrievedListener listener) {
        executorService.execute(() -> {
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                conn = getConnection();

                String sql = "SELECT * FROM analyses WHERE coin_symbol = ? AND exchange = ? ORDER BY timestamp DESC LIMIT 1";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, coinSymbol);
                stmt.setString(2, exchange);

                rs = stmt.executeQuery();

                if (rs.next()) {
                    // 데이터베이스 결과를 AnalysisResult 객체로 변환
                    AnalysisResult result = parseAnalysisResult(rs);

                    mainHandler.post(() -> {
                        listener.onAnalysisRetrieved(result);
                    });
                } else {
                    mainHandler.post(() -> {
                        listener.onNoAnalysisFound();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "분석 결과 조회 실패: " + e.getMessage());
                mainHandler.post(() -> {
                    listener.onFailure("분석 결과 조회 실패: " + e.getMessage());
                });
            } finally {
                closeConnection(conn, stmt, rs);
            }
        });
    }

    /**
     * 모든 주요 코인의 최신 분석 결과 가져오기
     */
    public void getAllLatestAnalyses(OnAllAnalysesRetrievedListener listener) {
        executorService.execute(() -> {
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                conn = getConnection();

                // 주요 코인들(BTC, ETH, XRP, SOL)의 최신 분석 결과만 가져오기
                StringBuilder coinsPlaceholder = new StringBuilder();
                for (int i = 0; i < Constants.MAIN_COINS.length; i++) {
                    if (i > 0) coinsPlaceholder.append(",");
                    coinsPlaceholder.append("?");
                }

                String sql = "SELECT a.* FROM analyses a " +
                        "INNER JOIN (" +
                        "    SELECT coin_symbol, exchange, MAX(timestamp) AS max_timestamp " +
                        "    FROM analyses " +
                        "    WHERE coin_symbol IN (" + coinsPlaceholder.toString() + ") AND exchange = 'binance' " +
                        "    GROUP BY coin_symbol, exchange" +
                        ") b ON a.coin_symbol = b.coin_symbol AND a.exchange = b.exchange AND a.timestamp = b.max_timestamp";

                stmt = conn.prepareStatement(sql);

                // 파라미터 설정
                for (int i = 0; i < Constants.MAIN_COINS.length; i++) {
                    stmt.setString(i + 1, Constants.MAIN_COINS[i]);
                }

                rs = stmt.executeQuery();

                List<AnalysisResult> resultList = new ArrayList<>();

                while (rs.next()) {
                    AnalysisResult result = parseAnalysisResult(rs);
                    resultList.add(result);
                }

                mainHandler.post(() -> {
                    listener.onAllAnalysesRetrieved(resultList);
                });
            } catch (Exception e) {
                Log.e(TAG, "전체 분석 결과 조회 실패: " + e.getMessage());
                mainHandler.post(() -> {
                    listener.onFailure("전체 분석 결과 조회 실패: " + e.getMessage());
                });
            } finally {
                closeConnection(conn, stmt, rs);
            }
        });
    }

    /**
     * 데이터베이스 연결 종료
     */
    private void closeConnection(Connection conn, PreparedStatement stmt, ResultSet rs) {
        try {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        } catch (SQLException e) {
            Log.e(TAG, "연결 종료 실패: " + e.getMessage());
        }
    }

    /**
     * ResultSet에서 AnalysisResult 객체로 변환
     */
    private AnalysisResult parseAnalysisResult(ResultSet rs) throws Exception {
        AnalysisResult result = new AnalysisResult();

        // 기본 정보
        result.setCurrencySymbol(rs.getString("currency_symbol"));
        result.setExchange(rs.getString("exchange"));
        result.setSummary(rs.getString("summary"));

        // 추천 정보
        AnalysisResult.Recommendation recommendation = new AnalysisResult.Recommendation();
        recommendation.setBuyProbability(rs.getDouble("buy_probability"));
        recommendation.setSellProbability(rs.getDouble("sell_probability"));
        recommendation.setRecommendation(rs.getString("recommendation"));
        recommendation.setConfidence(rs.getDouble("confidence"));
        recommendation.setReason(rs.getString("strategy_explanation")); // 전략 설명을 근거로 사용
        result.setRecommendation(recommendation);

        // 매매 전략
        AnalysisResult.Strategy strategy = new AnalysisResult.Strategy();

        // JSON 문자열을 List<Double>로 변환
        String targetPricesJson = rs.getString("target_prices");
        List<Double> targetPrices = new ArrayList<>();
        if (targetPricesJson != null && !targetPricesJson.isEmpty()) {
            JSONArray targetPricesArray = new JSONArray(targetPricesJson);
            for (int i = 0; i < targetPricesArray.length(); i++) {
                targetPrices.add(targetPricesArray.getDouble(i));
            }
        }

        strategy.setTargetPrices(targetPrices);
        strategy.setStopLoss(rs.getDouble("stop_loss"));
        strategy.setRiskRewardRatio(rs.getDouble("risk_reward_ratio"));
        strategy.setExplanation(rs.getString("strategy_explanation"));
        result.setStrategy(strategy);

        // 시간별 전망
        AnalysisResult.Outlook outlook = new AnalysisResult.Outlook();
        outlook.setShortTerm(rs.getString("short_term_outlook"));
        outlook.setMidTerm(rs.getString("mid_term_outlook"));
        outlook.setLongTerm(rs.getString("long_term_outlook"));
        result.setOutlook(outlook);

        // 기술적 분석
        AnalysisResult.TechnicalAnalysis technicalAnalysis = new AnalysisResult.TechnicalAnalysis();

        // JSON 문자열을 List<Double>로 변환
        String supportLevelsJson = rs.getString("support_levels");
        List<Double> supportLevels = new ArrayList<>();
        if (supportLevelsJson != null && !supportLevelsJson.isEmpty()) {
            JSONArray supportLevelsArray = new JSONArray(supportLevelsJson);
            for (int i = 0; i < supportLevelsArray.length(); i++) {
                supportLevels.add(supportLevelsArray.getDouble(i));
            }
        }

        String resistanceLevelsJson = rs.getString("resistance_levels");
        List<Double> resistanceLevels = new ArrayList<>();
        if (resistanceLevelsJson != null && !resistanceLevelsJson.isEmpty()) {
            JSONArray resistanceLevelsArray = new JSONArray(resistanceLevelsJson);
            for (int i = 0; i < resistanceLevelsArray.length(); i++) {
                resistanceLevels.add(resistanceLevelsArray.getDouble(i));
            }
        }

        technicalAnalysis.setSupportLevels(supportLevels);
        technicalAnalysis.setResistanceLevels(resistanceLevels);
        technicalAnalysis.setTrendStrength(rs.getString("trend_strength"));
        technicalAnalysis.setPattern(rs.getString("pattern"));
        result.setTechnicalAnalysis(technicalAnalysis);

        // 위험 요소
        String riskFactorsJson = rs.getString("risk_factors");
        List<String> riskFactors = new ArrayList<>();
        if (riskFactorsJson != null && !riskFactorsJson.isEmpty()) {
            JSONArray riskFactorsArray = new JSONArray(riskFactorsJson);
            for (int i = 0; i < riskFactorsArray.length(); i++) {
                riskFactors.add(riskFactorsArray.getString(i));
            }
        }
        result.setRiskFactors(riskFactors);

        return result;
    }

    // 리스너 인터페이스 정의
    public interface OnAnalysisRetrievedListener {
        void onAnalysisRetrieved(AnalysisResult result);
        void onNoAnalysisFound();
        void onFailure(String errorMessage);
    }

    public interface OnAllAnalysesRetrievedListener {
        void onAllAnalysesRetrieved(List<AnalysisResult> resultList);
        void onFailure(String errorMessage);
    }
}