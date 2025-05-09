package com.example.cryptoanalysisai.utils;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * JSON 작업을 위한 유틸리티 클래스
 */
public class JsonUtils {

    private static final String TAG = "JsonUtils";
    private static final Gson gson = new Gson();

    /**
     * 문자열에서 JSON 부분 추출
     *
     * @param text JSON을 포함할 수 있는 텍스트
     * @return 추출된 JSON 문자열, 추출 실패 시 원본 텍스트 반환
     */
    public static String extractJsonFromText(String text) {
        try {
            if (text == null || text.isEmpty()) {
                return "{}";
            }

            // JSON 블록 찾기
            int startIndex = text.indexOf("```json");
            int endIndex = text.lastIndexOf("```");

            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                // json 블록 시작 부분 이후부터 추출
                startIndex = text.indexOf("\n", startIndex) + 1;
                return text.substring(startIndex, endIndex).trim();
            }

            // JSON 중괄호로 찾기
            startIndex = text.indexOf("{");
            endIndex = text.lastIndexOf("}");

            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                return text.substring(startIndex, endIndex + 1).trim();
            }

            // JSON 블록이 없는 경우
            return text;
        } catch (Exception e) {
            Log.e(TAG, "JSON 추출 실패: " + e.getMessage());
            return text;
        }
    }

    /**
     * 문자열을 JSON 객체로 파싱
     *
     * @param jsonString JSON 문자열
     * @return JSONObject 객체, 파싱 실패 시 빈 JSONObject 반환
     */
    public static JSONObject parseJson(String jsonString) {
        try {
            return new JSONObject(jsonString);
        } catch (JSONException e) {
            Log.e(TAG, "JSON 파싱 실패: " + e.getMessage());
            try {
                return new JSONObject("{}");
            } catch (JSONException ex) {
                return null;
            }
        }
    }

    /**
     * JSON 문자열을 특정 클래스로 변환
     *
     * @param jsonString JSON 문자열
     * @param classOfT 변환할 클래스 타입
     * @return 변환된 객체, 변환 실패 시 null 반환
     */
    public static <T> T fromJson(String jsonString, Class<T> classOfT) {
        try {
            return gson.fromJson(jsonString, classOfT);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "JSON 변환 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * 객체를 JSON 문자열로 변환
     *
     * @param object 변환할 객체
     * @return JSON 문자열, 변환 실패 시 빈 JSON 객체 문자열 반환
     */
    public static String toJson(Object object) {
        try {
            return gson.toJson(object);
        } catch (Exception e) {
            Log.e(TAG, "객체→JSON 변환 실패: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * JSON이 유효한지 검사
     *
     * @param jsonString 검사할 JSON 문자열
     * @return 유효 여부
     */
    public static boolean isValidJson(String jsonString) {
        try {
            JsonParser.parseString(jsonString);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    /**
     * JSON 문자열에서 특정 키의 값을 추출
     *
     * @param jsonString JSON 문자열
     * @param key 추출할 키
     * @return 추출된 값, 추출 실패 시 null 반환
     */
    public static String getStringValue(String jsonString, String key) {
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonString).getAsJsonObject();
            JsonElement element = jsonObject.get(key);
            return element != null ? element.getAsString() : null;
        } catch (Exception e) {
            Log.e(TAG, "JSON 값 추출 실패: " + e.getMessage());
            return null;
        }
    }
}