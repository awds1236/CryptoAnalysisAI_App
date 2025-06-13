package com.coinsense.cryptoanalysisai.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.coinsense.cryptoanalysisai.api.LambdaApiService;
import com.coinsense.cryptoanalysisai.api.RetrofitClient;
import com.coinsense.cryptoanalysisai.models.AnalysisResult;
import com.coinsense.cryptoanalysisai.utils.Constants;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * AWS Lambda API를 통해 분석 결과를 가져오는 서비스
 * 기존의 직접 RDS 접근 대신 API 호출 방식 사용
 */
public class AnalysisApiService {
    private static final String TAG = "AnalysisApiService";
    private static final String EXCHANGE = "binance";

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static AnalysisApiService instance;
    private final LambdaApiService apiService;
    private Context context;  // 언어 설정을 가져오기 위한 컨텍스트 추가

    private AnalysisApiService(Context context) {
        this.context = context.getApplicationContext();
        apiService = RetrofitClient.getLambdaApiService();
    }

    public static synchronized AnalysisApiService getInstance(Context context) {
        if (instance == null) {
            instance = new AnalysisApiService(context);
        }
        return instance;
    }

    /**
     * 특정 코인의 최신 분석 결과 가져오기 (바이낸스 거래소만)
     */
    public void getLatestAnalysis(String coinSymbol, OnAnalysisRetrievedListener listener) {
        if (coinSymbol == null || listener == null) {
            Log.e(TAG, "파라미터가 null입니다");
            return;
        }

        // 현재 설정된 언어 가져오기
        String language = getCurrentLanguage();

        // 쿼리 파라미터를 URL에 직접 추가 (언어 파라미터 포함)
        apiService.getLatestAnalysis(coinSymbol, "binance", language)
                .enqueue(new Callback<AnalysisResult>() {
                    @Override
                    public void onResponse(Call<AnalysisResult> call, Response<AnalysisResult> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            listener.onAnalysisRetrieved(response.body());
                        } else {
                            int errorCode = response.code();
                            if (errorCode == 404) {
                                listener.onNoAnalysisFound();
                            } else {
                                String errorMessage = "서버 오류: " + errorCode;
                                listener.onFailure(errorMessage);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<AnalysisResult> call, Throwable t) {
                        String errorMessage = "네트워크 오류: " + t.getMessage();
                        Log.e(TAG, errorMessage, t);
                        listener.onFailure(errorMessage);
                    }
                });
    }

    /**
     * 모든 코인의 최신 분석 결과 가져오기 (바이낸스 거래소만)
     */
    public void getAllLatestAnalyses(OnAllAnalysesRetrievedListener listener) {
        if (listener == null) {
            Log.e(TAG, "리스너가 null입니다");
            return;
        }

        // 현재 설정된 언어 가져오기
        String language = getCurrentLanguage();

        apiService.getAllLatestAnalyses("binance", language)
                .enqueue(new Callback<List<AnalysisResult>>() {
                    @Override
                    public void onResponse(Call<List<AnalysisResult>> call, Response<List<AnalysisResult>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            listener.onAllAnalysesRetrieved(response.body());
                        } else {
                            String errorMessage = "서버 오류: " + response.code();
                            listener.onFailure(errorMessage);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<AnalysisResult>> call, Throwable t) {
                        String errorMessage = "네트워크 오류: " + t.getMessage();
                        Log.e(TAG, errorMessage, t);
                        listener.onFailure(errorMessage);
                    }
                });
    }

    /**
     * 현재 설정된 언어 코드 반환 (기본값: ko)
     */
    private String getCurrentLanguage() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("pref_language", "ko"); // 기본값은 한국어
    }

    // 콜백 인터페이스
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