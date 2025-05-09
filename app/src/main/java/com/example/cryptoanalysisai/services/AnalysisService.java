package com.example.cryptoanalysisai.services;

import android.util.Log;

import com.example.cryptoanalysisai.api.ClaudeApiService;
import com.example.cryptoanalysisai.api.RetrofitClient;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.CandleData;
import com.example.cryptoanalysisai.models.ClaudeRequest;
import com.example.cryptoanalysisai.models.ClaudeResponse;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.models.TickerData;
import com.example.cryptoanalysisai.utils.JsonUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AnalysisService {

    private static final String TAG = "AnalysisService";

    private final ClaudeApiService claudeApiService;
    private final TechnicalIndicatorService indicatorService;
    private final Gson gson;

    public AnalysisService() {
        this.claudeApiService = RetrofitClient.getClaudeApiService();
        this.indicatorService = new TechnicalIndicatorService();
        this.gson = new Gson();
    }

    /**
     * Claude API를 사용하여 코인 분석 요청
     */
    public void generateAnalysis(
            CoinInfo coinInfo,
            List<CandleData> candles,
            TickerData tickerData,
            ExchangeType exchangeType,
            Map<String, Object> technicalIndicators,
            AnalysisCallback callback) {

        try {
            // 분석에 필요한 데이터 준비
            Map<String, Object> analysisData = new HashMap<>();
            analysisData.put("market", coinInfo.getMarket());
            analysisData.put("exchange", exchangeType.getCode());
            analysisData.put("currentPrice", gson.toJson(tickerData));
            analysisData.put("candles", gson.toJson(candles));
            analysisData.put("technicalIndicators", technicalIndicators);

            // 공포/욕심 지수 데이터 (예시)
            Map<String, Object> fearGreedIndex = new HashMap<>();
            fearGreedIndex.put("value", 45);
            fearGreedIndex.put("valueClassification", "중립");
            analysisData.put("fearGreedIndex", fearGreedIndex);

            // 프롬프트 생성
            String prompt = buildPrompt(coinInfo, analysisData, exchangeType);

            // Claude API 요청 생성
            ClaudeRequest request = new ClaudeRequest(prompt);

            // API 호출
            claudeApiService.generateAnalysis(request).enqueue(new Callback<ClaudeResponse>() {
                @Override
                public void onResponse(Call<ClaudeResponse> call, Response<ClaudeResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        ClaudeResponse claudeResponse = response.body();

                        // 응답에서 JSON 부분 추출
                        String jsonResponse = extractJsonFromResponse(claudeResponse.getText());

                        try {
                            // JSON 파싱
                            AnalysisResult analysisResult = gson.fromJson(jsonResponse, AnalysisResult.class);
                            callback.onAnalysisSuccess(analysisResult, claudeResponse.getText());
                        } catch (Exception e) {
                            Log.e(TAG, "JSON 파싱 실패: " + e.getMessage());
                            callback.onAnalysisFailure("JSON 파싱 실패: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "API 호출 실패: " + response.code());
                        callback.onAnalysisFailure("API 호출 실패: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<ClaudeResponse> call, Throwable t) {
                    Log.e(TAG, "API 호출 오류: " + t.getMessage());
                    callback.onAnalysisFailure("API 호출 오류: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "분석 요청 오류: " + e.getMessage());
            callback.onAnalysisFailure("분석 요청 오류: " + e.getMessage());
        }
    }

    /**
     * Claude 프롬프트 생성
     */
    private String buildPrompt(CoinInfo coinInfo, Map<String, Object> data, ExchangeType exchangeType) {
        StringBuilder prompt = new StringBuilder();

        String currencyUnit = "upbit".equalsIgnoreCase(exchangeType.getCode()) ? "원" : "달러(USD)";
        String currencySymbol = "upbit".equalsIgnoreCase(exchangeType.getCode()) ? "원" : "$";

        prompt.append("다음 데이터를 기반으로 ")
                .append(coinInfo.getDisplayName())
                .append("(")
                .append(coinInfo.getSymbol())
                .append(")에 대한 단기(24시간), 중기(1주일), 장기(1개월) 전망을 분석해주세요.\n\n");

        prompt.append("현재 사용 중인 거래소는 ").append(exchangeType.getDisplayName()).append("이며, 통화 단위는 ").append(currencyUnit).append("입니다. ");
        prompt.append("모든 가격 정보는 ").append(currencySymbol).append(" 단위로 표시해주세요.\n\n");

        prompt.append("현재 포지션이 없는 상태에서 매수/매도 확률(%)과 그 이유, 주요 지지/저항선, 위험 요소를 포함해주세요.\n");
        prompt.append("매수와 매도 확률의 합이 100%가 되어야 합니다. 매수나 매도가 70% 이상이면 해당 포지션을 추천하고, 둘 다 70% 미만이면 관망으로 추천해주세요.\n");
        prompt.append("매수를 추천하는 경우, 현재 진입 시 적정 수익실현 목표가와 손절매 가격을 구체적으로 제시해주세요. 추세와 변동성을 고려하여 리스크 대비 보상 비율도 계산해주세요.\n");
        prompt.append("신뢰도 점수(1-10)도 함께 제공해주세요.\n\n");

        prompt.append("데이터:\n").append(gson.toJson(data)).append("\n\n");

        prompt.append("다음 형식으로 응답해주세요:\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"통화단위\": \"").append(currencySymbol).append("\",\n");
        prompt.append("  \"거래소\": \"").append(exchangeType.getDisplayName().toUpperCase()).append("\",\n");
        prompt.append("  \"분석_요약\": \"핵심 분석 내용을 3-4문장으로 요약\",\n");
        prompt.append("  \"매수매도_추천\": {\n");
        prompt.append("    \"매수_확률\": 60,\n");
        prompt.append("    \"매도_확률\": 40,\n");
        prompt.append("    \"추천\": \"매수\" | \"매도\" | \"관망\",\n");
        prompt.append("    \"신뢰도\": 7.5,\n");
        prompt.append("    \"근거\": \"추천의 주요 근거 설명\"\n");
        prompt.append("  },\n");
        prompt.append("  \"매매_전략\": {\n");
        prompt.append("    \"수익실현_목표가\": [가격1, 가격2],\n");
        prompt.append("    \"손절매_라인\": 가격,\n");
        prompt.append("    \"리스크_보상_비율\": 2.5,\n");
        prompt.append("    \"전략_설명\": \"매매 전략에 대한 상세 설명\"\n");
        prompt.append("  },\n");
        prompt.append("  \"시간별_전망\": {\n");
        prompt.append("    \"단기_24시간\": \"상승/하락/횡보 예상과 이유\",\n");
        prompt.append("    \"중기_1주일\": \"상승/하락/횡보 예상과 이유\",\n");
        prompt.append("    \"장기_1개월\": \"상승/하락/횡보 예상과 이유\"\n");
        prompt.append("  },\n");
        prompt.append("  \"기술적_분석\": {\n");
        prompt.append("    \"주요_지지선\": [가격1, 가격2],\n");
        prompt.append("    \"주요_저항선\": [가격1, 가격2],\n");
        prompt.append("    \"추세_강도\": \"강/중/약\",\n");
        prompt.append("    \"주요_패턴\": \"설명\"\n");
        prompt.append("  },\n");
        prompt.append("  \"고급_지표_분석\": {\n");
        prompt.append("    \"MACD\": \"분석 및 신호\",\n");
        prompt.append("    \"볼린저밴드\": \"분석 및 신호\",\n");
        prompt.append("    \"피보나치\": \"주요 지지/저항 레벨\",\n");
        prompt.append("    \"ATR\": \"변동성 분석\",\n");
        prompt.append("    \"OBV\": \"거래량 추세 분석\"\n");
        prompt.append("  },\n");
        prompt.append("  \"최근_뉴스_요약\": {\n");
        prompt.append("    \"주요_뉴스\": [\"뉴스1 요약\", \"뉴스2 요약\"],\n");
        prompt.append("    \"뉴스_영향\": \"뉴스가 가격에 미치는 영향 분석\"\n");
        prompt.append("  },\n");
        prompt.append("  \"위험_요소\": [\n");
        prompt.append("    \"주요 위험 요소 1\",\n");
        prompt.append("    \"주요 위험 요소 2\"\n");
        prompt.append("  ]\n");
        prompt.append("}\n```\n\n");

        prompt.append("매수_확률과 매도_확률의 합은 반드시 100%가 되어야 합니다. 추천은 매수_확률이 70% 이상이면 '매수', 매도_확률이 70% 이상이면 '매도', 둘 다 70% 미만이면 '관망'으로 설정해주세요.");
        prompt.append("JSON 형식이 정확해야 합니다. 분석은 명확하고 구체적인 정보를 포함해야 하며, 두루뭉술한 표현은 피해주세요.");
        prompt.append("반드시 통화 단위(").append(currencySymbol).append(")를 고려하여 가격 정보를 제공해 주세요.");

        return prompt.toString();
    }

    /**
     * Claude 응답에서 JSON 부분만 추출
     */
    private String extractJsonFromResponse(String response) {
        try {
            int startIndex = response.indexOf("```json");
            int endIndex = response.lastIndexOf("```");

            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                // json 블록 시작 부분 이후부터 추출
                startIndex = response.indexOf("\n", startIndex) + 1;
                return response.substring(startIndex, endIndex).trim();
            }

            // JSON 형식이 아닌 경우
            return "{\"분석_요약\":\"" + response + "\"}";
        } catch (Exception e) {
            Log.e(TAG, "JSON 추출 실패: " + e.getMessage());
            return "{\"분석_요약\":\"JSON 추출 실패\"}";
        }
    }

    /**
     * 분석 콜백 인터페이스
     */
    public interface AnalysisCallback {
        void onAnalysisSuccess(AnalysisResult result, String rawResponse);
        void onAnalysisFailure(String error);
    }
}
