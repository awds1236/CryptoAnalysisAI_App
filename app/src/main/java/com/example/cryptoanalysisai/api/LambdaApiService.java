package com.example.cryptoanalysisai.api;

import com.example.cryptoanalysisai.models.AnalysisResult;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * API interface for AWS Lambda endpoints through API Gateway
 */
public interface LambdaApiService {
    /**
     * Get the latest analysis for a specific coin
     * @param coinPath 코인 심볼을 포함한 경로 (예: BTC/)
     */
    @GET("analyses")
    Call<AnalysisResult> getLatestAnalysis(
            @Query(value = "", encoded = true) String coinPath,
            @Query("exchange") String exchange
    );

    /**
     * Get the latest analyses for all coins
     */
    @GET("api/analyses")
    Call<List<AnalysisResult>> getAllLatestAnalyses(
            @Query("exchange") String exchange
    );
}