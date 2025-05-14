package com.coinsense.cryptoanalysisai.api;

import com.coinsense.cryptoanalysisai.models.BinanceModels;
import com.coinsense.cryptoanalysisai.models.BinanceTicker;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface BinanceApiService {

    // 특정 심볼의 정보 조회 (심볼 전체 정보가 아닌 단일 심볼만 가져옴)
    @GET("exchangeInfo")
    Call<BinanceModels.BinanceExchangeInfo> getSymbolInfo(@Query("symbol") String symbol);

    // 주요 코인에 대한 정보만 조회 (symbols 파라미터 사용)
    @GET("exchangeInfo")
    Call<BinanceModels.BinanceExchangeInfo> getTopSymbolsInfo(@Query("symbols") String symbolsJson);

    // 거래소 정보 조회 (추가)
    @GET("exchangeInfo")
    Call<BinanceModels.BinanceExchangeInfo> getExchangeInfo();

    // 모든 심볼의 현재가 조회 (추가)
    @GET("ticker/price")
    Call<List<BinanceTicker>> getAllTickers();

    // 현재가 조회 (전체) - 이 메서드는 사용하지 말 것!
    // @GET("ticker/price")
    // Call<List<BinanceTicker>> getAllTickers();

    // 특정 심볼 목록에 대한 현재가만 조회
    @GET("ticker/price")
    Call<List<BinanceTicker>> getMultipleTickers(@Query("symbols") String symbolsJson);

    // 현재가 조회 (개별)
    @GET("ticker/price")
    Call<BinanceTicker> getTicker(@Query("symbol") String symbol);

    // 24시간 변동 정보
    @GET("ticker/24hr")
    Call<BinanceTicker> get24hTicker(@Query("symbol") String symbol);

    // 심볼 목록의 24시간 변동 정보
    @GET("ticker/24hr")
    Call<List<BinanceTicker>> getMultiple24hTickers(@Query("symbols") String symbolsJson);

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