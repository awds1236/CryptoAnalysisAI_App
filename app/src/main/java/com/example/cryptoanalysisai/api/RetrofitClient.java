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

    private static UpbitApiService upbitApiService;
    private static BinanceApiService binanceApiService;

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