package com.coinsense.cryptoanalysisai;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.coinsense.cryptoanalysisai.services.AdManager;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.coinsense.cryptoanalysisai.utils.LocaleHelper;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class CryptoAnalysisApp extends Application {
    private static final String TAG = "CryptoAnalysisApp";
    private static final String PREF_DARK_MODE = "pref_dark_mode";
    private static final String PREF_LANGUAGE = "pref_language";


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "앱 초기화 완료");

        // Firebase Auth 상태 확인 및 SharedPreferences 동기화
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(Constants.PREF_IS_LOGGED_IN, true);
            editor.putString(Constants.PREF_USER_EMAIL, currentUser.getEmail());
            editor.putString(Constants.PREF_USER_DISPLAY_NAME, currentUser.getDisplayName());
            editor.putString(Constants.PREF_USER_ID, currentUser.getUid());
            editor.apply();
        }

        // 테마 설정 적용
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean darkModeEnabled = prefs.getBoolean(PREF_DARK_MODE, true); // 기본값은 다크 모드
        if (darkModeEnabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // AdMob 초기화
        MobileAds.initialize(this, initializationStatus -> {
            Log.d(TAG, "AdMob 초기화 완료");
        });

        // AdManager 초기화
        AdManager.getInstance(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 시스템 설정 변경 시 저장된 언어 설정 유지
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String languageCode = prefs.getString(PREF_LANGUAGE, "ko");
        LocaleHelper.setLocale(this, languageCode);
    }
}