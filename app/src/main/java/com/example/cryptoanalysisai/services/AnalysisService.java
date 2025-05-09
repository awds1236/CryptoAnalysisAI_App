package com.example.cryptoanalysisai.services;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

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
import com.example.cryptoanalysisai.utils.Constants;
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
            // API 키 검사
            if (Constants.CLAUDE_API_KEY == null || Constants.CLAUDE_API_KEY.isEmpty() ||
                    Constants.CLAUDE_API_KEY.equals("\"${localProperties.getProperty('claude.api.key')}\"")) {
                callback.onAnalysisFailure("Claude API 키가 설정되지 않았습니다. local.properties 파일을 확인하세요.");
                return;
            }

            // 필수 데이터 검사
            if (coinInfo == null || candles == null || candles.isEmpty()) {
                callback.onAnalysisFailure("분석에 필요한 필수 데이터가 누락되었습니다.");
                return;
            }

            // 분석에 필요한 데이터 준비
            Map<String, Object> analysisData = new HashMap<>();
            analysisData.put("market", coinInfo.getMarket());
            analysisData.put("exchange", exchangeType.getCode());

            // currentPrice 설정 - tickerData가 없는 경우 첫 번째 캔들의 종가 사용
            if (tickerData != null) {
                analysisData.put("currentPrice", gson.toJson(tickerData));
            } else if (coinInfo.getCurrentPrice() > 0) {
                analysisData.put("currentPrice", gson.toJson(coinInfo.getCurrentPrice()));
            } else if (!candles.isEmpty()) {
                analysisData.put("currentPrice", gson.toJson(candles.get(0).getTradePrice()));
            } else {
                callback.onAnalysisFailure("현재가 정보가 없습니다.");
                return;
            }

            // 캔들 데이터는 크기가 클 수 있으므로 최근 30개만 사용
            List<CandleData> recentCandles = candles;
            if (candles.size() > 30) {
                recentCandles = candles.subList(0, 30);
            }
            analysisData.put("candles", gson.toJson(recentCandles));

            // 기술적 지표 데이터 추가
            if (technicalIndicators != null && !technicalIndicators.isEmpty()) {
                analysisData.put("technicalIndicators", technicalIndicators);
            }

            // 프롬프트 생성
            String prompt = buildPrompt(coinInfo, analysisData, exchangeType);
            Log.d(TAG, "프롬프트 생성 완료: " + prompt.substring(0, Math.min(100, prompt.length())) + "...");

            // Claude API 요청 생성
            ClaudeRequest request = new ClaudeRequest(prompt);

            // API 호출 로그
            Log.d(TAG, "Claude API 호출 시작...");

            // API 호출
            claudeApiService.generateAnalysis(request).enqueue(new Callback<ClaudeResponse>() {
                @Override
                public void onResponse(Call<ClaudeResponse> call, Response<ClaudeResponse> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "Claude API 응답 성공");
                            ClaudeResponse claudeResponse = response.body();
                            String responseText = claudeResponse.getText();

                            if (responseText == null || responseText.isEmpty()) {
                                callback.onAnalysisFailure("API 응답이 비어있습니다.");
                                return;
                            }

                            // 응답에서 JSON 부분 추출
                            String jsonResponse = extractJsonFromResponse(responseText);
                            Log.d(TAG, "추출된 JSON: " + jsonResponse);

                            if (jsonResponse == null || jsonResponse.isEmpty()) {
                                callback.onAnalysisFailure("JSON 추출 실패");
                                return;
                            }

                            try {
                                // JSON 파싱
                                AnalysisResult analysisResult = gson.fromJson(jsonResponse, AnalysisResult.class);
                                if (analysisResult == null) {
                                    callback.onAnalysisFailure("분석 결과 파싱 실패");
                                    return;
                                }

                                callback.onAnalysisSuccess(analysisResult, responseText);
                            } catch (Exception e) {
                                Log.e(TAG, "JSON 파싱 실패: " + e.getMessage(), e);
                                callback.onAnalysisFailure("JSON 파싱 실패: " + e.getMessage());
                            }
                        } else {
                            String errorBody = "";
                            try {
                                if (response.errorBody() != null) {
                                    errorBody = response.errorBody().string();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "에러 바디 읽기 실패: " + e.getMessage());
                            }

                            Log.e(TAG, "API 호출 실패: " + response.code() + " - " + errorBody);
                            callback.onAnalysisFailure("API 호출 실패: " + response.code() + " - " + errorBody);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "API 응답 처리 오류: " + e.getMessage(), e);
                        callback.onAnalysisFailure("API 응답 처리 오류: " + e.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<ClaudeResponse> call, Throwable t) {
                    Log.e(TAG, "API 호출 오류: " + t.getMessage(), t);
                    callback.onAnalysisFailure("API 호출 오류: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "분석 요청 오류: " + e.getMessage(), e);
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
            if (response == null || response.isEmpty()) {
                Log.e(TAG, "응답이 비어 있습니다.");
                return "{}";
            }

            Log.d(TAG, "원본 응답 길이: " + response.length());

            // JSON 블록 찾기 (```json ... ``` 형식)
            int startIndex = response.indexOf("```json");
            int endIndex = response.lastIndexOf("```");

            String jsonString;
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                // json 블록 시작 부분 이후부터 추출
                startIndex = response.indexOf("\n", startIndex) + 1;
                if (startIndex <= 0 || startIndex >= endIndex) {
                    Log.e(TAG, "JSON 블록 추출 실패");
                    return "{}";
                }
                jsonString = response.substring(startIndex, endIndex).trim();
            } else {
                // JSON 중괄호로 찾기
                startIndex = response.indexOf("{");
                endIndex = response.lastIndexOf("}");

                if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                    jsonString = response.substring(startIndex, endIndex + 1).trim();
                } else {
                    Log.e(TAG, "JSON 형식을 찾을 수 없습니다: " + response);
                    return "{}";
                }
            }

            Log.d(TAG, "추출된 JSON 길이: " + jsonString.length());

            // 괄호 검사를 통한 JSON 완전성 확인
            if (!isBalancedBrackets(jsonString)) {
                Log.e(TAG, "불완전한 JSON (괄호가 맞지 않음): " + jsonString);
                return createDefaultJson(jsonString);
            }

            // JSON 문자열 정제
            String cleanedJson = cleanupJsonString(jsonString);

            return cleanedJson;
        } catch (Exception e) {
            Log.e(TAG, "JSON 추출 실패: " + e.getMessage(), e);
            return "{}";
        }
    }


    // 괄호 밸런스 확인 메서드
    private boolean isBalancedBrackets(String s) {
        int braces = 0;  // {}
        int brackets = 0;  // []

        for (char c : s.toCharArray()) {
            if (c == '{') braces++;
            else if (c == '}') braces--;
            else if (c == '[') brackets++;
            else if (c == ']') brackets--;

            // 음수가 되면 닫는 괄호가 먼저 나온 것
            if (braces < 0 || brackets < 0) return false;
        }

        // 0이어야 모든 괄호가 짝을 이룸
        return braces == 0 && brackets == 0;
    }

    // 불완전한 JSON을 기본 형태로 보완
    private String createDefaultJson(String partialJson) {
        try {
            // 1. 필수 필드 확인
            boolean hasSummary = partialJson.contains("\"분석_요약\"");
            boolean hasRecommendation = partialJson.contains("\"매수매도_추천\"");

            // 2. 최소한의 필수 필드가 있으면서 불완전한 경우
            if (hasSummary && hasRecommendation) {
                // 끝부분 보완
                if (!partialJson.endsWith("}")) {
                    partialJson = partialJson + "}}";
                }
                return partialJson;
            }

            // 3. 너무 불완전하면 기본 JSON 반환
            return "{\"분석_요약\":\"분석 결과 처리 중 오류가 발생했습니다. 다시 시도해주세요.\"}";
        } catch (Exception e) {
            Log.e(TAG, "기본 JSON 생성 실패: " + e.getMessage(), e);
            return "{}";
        }
    }


    // JSON 문자열 정제 메서드
    private String cleanupJsonString(String jsonString) {
        try {
            // 1. 줄바꿈 문자와 탭 문자 제거
            jsonString = jsonString.replaceAll("\\r\\n|\\r|\\n|\\t", " ");

            // 2. 연속된 공백 제거
            jsonString = jsonString.replaceAll("\\s+", " ");

            // 3. 잘못된 숫자 키 수정 (숫자로 시작하는 키를 문자열로 변환)
            // 예: "매매_전략": { 700: 값 } -> "매매_전략": { "700": 값 }
            jsonString = jsonString.replaceAll("(\\{\\s*)(\\d+)(\\s*:)", "$1\"$2\"$3");

            // 4. 쉼표로 끝나는 객체 항목 수정 - 정규식 수정
            // 문제의 정규식을 수정합니다
            jsonString = jsonString.replace(",}", "}");
            jsonString = jsonString.replace(", }", "}");
            jsonString = jsonString.replace(",]", "]");
            jsonString = jsonString.replace(", ]", "]");

            return jsonString;
        } catch (Exception e) {
            Log.e(TAG, "JSON 문자열 정제 중 오류: " + e.getMessage(), e);
            return jsonString; // 원본 반환
        }
    }

    // JSON 유효성 검사 메서드
    private boolean isValidJson(String jsonString) {
        try {
            new JSONObject(jsonString);
            return true;
        } catch (Exception e) {
            try {
                new JSONArray(jsonString);
                return true;
            } catch (Exception e2) {
                return false;
            }
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
