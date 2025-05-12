package com.example.cryptoanalysisai.api;

import android.util.Log;

import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.utils.Constants;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;

public class RetrofitClient {

    private static final int TIMEOUT = 30; // 초

    private static Retrofit upbitRetrofit;
    private static Retrofit binanceRetrofit;
    private static Retrofit lambdaRetrofit;

    private static UpbitApiService upbitApiService;
    private static BinanceApiService binanceApiService;
    private static LambdaApiService lambdaApiService;



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

    // AWS Lambda API 클라이언트 생성
    public static synchronized LambdaApiService getLambdaApiService() {
        if (lambdaApiService == null) {
            if (lambdaRetrofit == null) {
                // 상세 로깅 설정
                HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

                OkHttpClient client = new OkHttpClient.Builder()
                        // 요청 URL을 로깅하는 인터셉터 추가
                        .addInterceptor(chain -> {
                            Request original = chain.request();

                            // 요청 URL을 로그에 출력
                            String url = original.url().toString();
                            Log.d("RETROFIT_URL", "요청 URL: " + url);

                            // 헤더 정보도 로깅 (선택사항)
                            Log.d("RETROFIT_HEADERS", "요청 헤더: " + original.headers());

                            // 원래 요청 진행
                            return chain.proceed(original);
                        })
                        .addInterceptor(loggingInterceptor)  // 기존 로깅 인터셉터도 유지
                        .connectTimeout(TIMEOUT, TimeUnit.SECONDS)
                        .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                        .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                        .build();

                lambdaRetrofit = new Retrofit.Builder()
                        .baseUrl(Constants.LAMBDA_API_URL)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
            }
            lambdaApiService = lambdaRetrofit.create(LambdaApiService.class);
        }
        return lambdaApiService;
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

    public interface ExchangeRateApiService {
        @GET("api/exchange-rate")
        Call<ExchangeRateResponse> getExchangeRate();
    }

    // RetrofitClient 클래스에 메서드 추가
    public static ExchangeRateApiService getExchangeRateApiService() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://479kz18ike.execute-api.ap-northeast-2.amazonaws.com/prod/")
                .client(createOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(ExchangeRateApiService.class);
    }
}