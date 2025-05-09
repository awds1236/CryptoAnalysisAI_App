package com.example.cryptoanalysisai.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface BinanceApiService {

    // 거래소 정보 및 심볼 목록 조회
    @GET("exchangeInfo")
    Call<Object> getExchangeInfo();

    // 현재가 조회 (전체)
    @GET("ticker/price")
    Call<List<Object>> getAllTickers();

    // 현재가 조회 (개별)
    @GET("ticker/price")
    Call<Object> getTicker(@Query("symbol") String symbol);

    // 24시간 변동 정보
    @GET("ticker/24hr")
    Call<Object> get24hTicker(@Query("symbol") String symbol);

    // 캔들스틱 데이터 조회
    @GET("klines")
    Call<List<List<Object>>> getKlines(
            @Query("symbol") String symbol,
            @Query("interval") String interval,
            @Query("limit") Integer limit
    );

    // 최근 거래 내역
    @GET("trades")
    Call<List<Object>> getRecentTrades(
            @Query("symbol") String symbol,
            @Query("limit") Integer limit
    );
}