package com.coinsense.cryptoanalysisai.api;

import com.coinsense.cryptoanalysisai.models.CandleData;
import com.coinsense.cryptoanalysisai.models.CoinInfo;
import com.coinsense.cryptoanalysisai.models.TickerData;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface UpbitApiService {

    // 코인 목록 조회
    @GET("market/all")
    Call<List<CoinInfo>> getMarkets(@Query("isDetails") boolean isDetails);

    // 현재가 조회
    @GET("ticker")
    Call<List<TickerData>> getTicker(@Query("markets") String markets);

    // 일봉 조회
    @GET("candles/days")
    Call<List<CandleData>> getDayCandles(
            @Query("market") String market,
            @Query("count") int count
    );

    // 주봉 조회
    @GET("candles/weeks")
    Call<List<CandleData>> getWeekCandles(
            @Query("market") String market,
            @Query("count") int count
    );

    // 월봉 조회
    @GET("candles/months")
    Call<List<CandleData>> getMonthCandles(
            @Query("market") String market,
            @Query("count") int count
    );

    // 시간봉 조회
    @GET("candles/minutes/60")
    Call<List<CandleData>> getHourCandles(
            @Query("market") String market,
            @Query("count") int count
    );

    // 분봉 조회
    @GET("candles/minutes/{unit}")
    Call<List<CandleData>> getMinuteCandles(
            @Query("market") String market,
            @Query("unit") int unit,
            @Query("count") int count
    );
}