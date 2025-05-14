// CryptoAnalysisApp.java
package com.example.cryptoanalysisai;

import android.app.Application;
import android.util.Log;

import com.example.cryptoanalysisai.services.AdManager;
import com.google.android.gms.ads.MobileAds;

public class CryptoAnalysisApp extends Application {
    private static final String TAG = "CryptoAnalysisApp";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "앱 초기화 완료");

        // AdMob 초기화
        MobileAds.initialize(this, initializationStatus -> {
            Log.d(TAG, "AdMob 초기화 완료");
        });

        // AdManager 초기화
        AdManager.getInstance(this);
    }
}