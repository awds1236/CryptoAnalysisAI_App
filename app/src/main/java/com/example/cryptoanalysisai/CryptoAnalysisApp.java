package com.example.cryptoanalysisai;

import android.app.Application;
import android.util.Log;

import com.example.cryptoanalysisai.workers.AnalysisWorker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class CryptoAnalysisApp extends Application {
    private static final String TAG = "CryptoAnalysisApp";
    private static boolean firebaseInitialized = false;

    @Override
    public void onCreate() {
        super.onCreate();

        // Firebase 초기화 시도 (최대 1회만 시도)
        initializeFirebaseIfNeeded();

        // 시간별 분석 작업 스케줄링 (Firebase 사용 여부와 상관없이 진행)
        AnalysisWorker.scheduleHourlyAnalysis(this);
    }

    /**
     * Firebase를 초기화 (안전하게 시도)
     */
    private void initializeFirebaseIfNeeded() {
        if (firebaseInitialized) {
            return;
        }

        try {
            // Firebase 자동 초기화 (google-services.json 사용)
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase 초기화 성공");

            // Firestore 설정
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();

            try {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.setFirestoreSettings(settings);
                firebaseInitialized = true;
            } catch (Exception e) {
                Log.e(TAG, "Firestore 설정 실패: " + e.getMessage());
            }
        } catch (IllegalStateException e) {
            // 이미 초기화된 경우
            Log.d(TAG, "Firebase가 이미 초기화되어 있습니다");
            firebaseInitialized = true;
        } catch (Exception e) {
            Log.e(TAG, "Firebase 초기화 실패: " + e.getMessage());
            // Firebase 없이도 앱 실행 가능하도록 설정
            makeFirebaseOptional();
        }
    }

    /**
     * Firebase 없이도 앱이 동작하도록 설정
     */
    private void makeFirebaseOptional() {
        Log.d(TAG, "Firebase 없이 앱 실행 모드로 전환");

        // FirebaseManager에 Firebase 상태 알림
        try {
            com.example.cryptoanalysisai.services.FirebaseManager firebaseManager =
                    com.example.cryptoanalysisai.services.FirebaseManager.getInstance();
            firebaseManager.setFirebaseAvailable(false);
        } catch (Exception e) {
            Log.e(TAG, "FirebaseManager 설정 실패: " + e.getMessage());
        }
    }
}