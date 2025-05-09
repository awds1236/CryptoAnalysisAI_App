package com.example.cryptoanalysisai.api;

import com.example.cryptoanalysisai.models.ClaudeRequest;
import com.example.cryptoanalysisai.models.ClaudeResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ClaudeApiService {

    @POST("v1/messages")
    Call<ClaudeResponse> generateAnalysis(@Body ClaudeRequest request);
}