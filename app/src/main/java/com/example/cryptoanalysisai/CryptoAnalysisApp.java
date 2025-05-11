package com.example.cryptoanalysisai;

import android.app.Application;
import android.util.Log;

public class CryptoAnalysisApp extends Application {
    private static final String TAG = "CryptoAnalysisApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "앱 초기화 완료");
    }
}