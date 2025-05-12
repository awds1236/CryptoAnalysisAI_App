package com.example.cryptoanalysisai.services;

import com.example.cryptoanalysisai.api.ExchangeRateResponse;
import com.example.cryptoanalysisai.api.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ExchangeRateManager {
    private static ExchangeRateManager instance;
    private double usdToKrwRate = 0;
    private String lastUpdateDate = "";
    private boolean isLoading = false;

    private ExchangeRateManager() {}

    public static synchronized ExchangeRateManager getInstance() {
        if (instance == null) {
            instance = new ExchangeRateManager();
        }
        return instance;
    }

    public void fetchExchangeRate(final OnExchangeRateListener listener) {
        if (isLoading) return;
        isLoading = true;

        RetrofitClient.ExchangeRateApiService apiService = RetrofitClient.getExchangeRateApiService();
        apiService.getExchangeRate().enqueue(new Callback<ExchangeRateResponse>() {
            @Override
            public void onResponse(Call<ExchangeRateResponse> call, Response<ExchangeRateResponse> response) {
                isLoading = false;
                if (response.isSuccessful() && response.body() != null) {
                    usdToKrwRate = response.body().getRate();
                    lastUpdateDate = response.body().getFetch_date();
                    if (listener != null) {
                        listener.onExchangeRateUpdated(usdToKrwRate);
                    }
                } else {
                    if (listener != null) {
                        listener.onError("환율 정보를 가져오는데 실패했습니다");
                    }
                }
            }

            @Override
            public void onFailure(Call<ExchangeRateResponse> call, Throwable t) {
                isLoading = false;
                if (listener != null) {
                    listener.onError("네트워크 오류: " + t.getMessage());
                }
            }
        });
    }

    public double convertUsdToKrw(double usdAmount) {
        return usdAmount * usdToKrwRate;
    }

    public String formatUsdWithKrw(double usdAmount) {
        if (usdToKrwRate <= 0) return String.format("$%.2f", usdAmount);

        double krwAmount = convertUsdToKrw(usdAmount);
        return String.format("$%.2f (₩%,.0f)", usdAmount, krwAmount);
    }

    public interface OnExchangeRateListener {
        void onExchangeRateUpdated(double rate);
        void onError(String errorMessage);
    }

    public double getUsdToKrwRate() {
        return usdToKrwRate;
    }

    public String getLastUpdateDate() {
        return lastUpdateDate;
    }
}