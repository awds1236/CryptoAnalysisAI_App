package com.example.cryptoanalysisai;

import android.app.Application;
import android.util.Log;

import com.example.cryptoanalysisai.workers.AnalysisWorker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class CryptoAnalysisApp extends Application {
    private static final String TAG = "CryptoAnalysisApp";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            // Firebase 자동 초기화 (google-services.json 사용)
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase 자동 초기화 성공");

            // Firestore 설정
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();

            FirebaseFirestore.getInstance().setFirestoreSettings(settings);
        } catch (Exception e) {
            Log.e(TAG, "Firebase 초기화 실패: " + e.getMessage());
        }

        // 시간별 분석 작업 스케줄링
        AnalysisWorker.scheduleHourlyAnalysis(this);
    }
}