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
     */
    @GET("api/analyses/{coin_symbol}")
    Call<AnalysisResult> getLatestAnalysis(
            @Path("coin_symbol") String coinSymbol,
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