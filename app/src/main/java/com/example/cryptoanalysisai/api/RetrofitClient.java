package com.example.cryptoanalysisai.api;

import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.utils.Constants;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final int TIMEOUT = 30; // 초

    private static Retrofit upbitRetrofit;
    private static Retrofit binanceRetrofit;
    private static Retrofit claudeRetrofit;

    private static UpbitApiService upbitApiService;
    private static BinanceApiService binanceApiService;
    private static ClaudeApiService claudeApiService;

    // OkHttpClient 생성
    private static OkHttpClient createOkHttpClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        return new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                .build();
    }

    // 업비트 API 클라이언트 생성
    public static synchronized UpbitApiService getUpbitApiService() {
        if (upbitApiService == null) {
            if (upbitRetrofit == null) {
                upbitRetrofit = new Retrofit.Builder()
                        .baseUrl(Constants.UPBIT_API_URL)
                        .client(createOkHttpClient())
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
            }
            upbitApiService = upbitRetrofit.create(UpbitApiService.class);
        }
        return upbitApiService;
    }

    // 바이낸스 API 클라이언트 생성
    public static synchronized BinanceApiService getBinanceApiService() {
        if (binanceApiService == null) {
            if (binanceRetrofit == null) {
                binanceRetrofit = new Retrofit.Builder()
                        .baseUrl(Constants.BINANCE_API_URL)
                        .client(createOkHttpClient())
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
            }
            binanceApiService = binanceRetrofit.create(BinanceApiService.class);
        }
        return binanceApiService;
    }

    // Claude API 클라이언트 생성
    public static synchronized ClaudeApiService getClaudeApiService() {
        if (claudeApiService == null) {
            if (claudeRetrofit == null) {
                HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

                OkHttpClient client = new OkHttpClient.Builder()
                        .addInterceptor(chain -> {
                            okhttp3.Request original = chain.request();

                            // API 키 헤더 추가
                            okhttp3.Request.Builder requestBuilder = original.newBuilder()
                                    .header("x-api-key", Constants.CLAUDE_API_KEY)
                                    .header("anthropic-version", "2023-06-01")
                                    .header("Content-Type", "application/json");

                            return chain.proceed(requestBuilder.build());
                        })
                        .addInterceptor(loggingInterceptor)
                        .connectTimeout(60, TimeUnit.SECONDS) // Claude 응답이 더 오래 걸릴 수 있어 타임아웃 증가
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .build();

                claudeRetrofit = new Retrofit.Builder()
                        .baseUrl(Constants.CLAUDE_API_URL)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
            }
            claudeApiService = claudeRetrofit.create(ClaudeApiService.class);
        }
        return claudeApiService;
    }

    // 거래소 타입에 따른 API 서비스 선택
    public static Object getApiService(ExchangeType exchangeType) {
        switch (exchangeType) {
            case UPBIT:
                return getUpbitApiService();
            case BINANCE:
                return getBinanceApiService();
            default:
                return getUpbitApiService();
        }
    }
}
