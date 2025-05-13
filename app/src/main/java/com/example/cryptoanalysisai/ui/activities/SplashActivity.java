package com.example.cryptoanalysisai.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.example.cryptoanalysisai.MainActivity;
import com.example.cryptoanalysisai.R;
import com.example.cryptoanalysisai.utils.Constants;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY = 1500; // 1.5초

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Android 12+ 스플래시 화면 설정 - 액티비티 생성 전에 설치
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 시스템 스플래시 화면이 즉시 사라지지 않도록 설정
        // true인 동안 시스템 스플래시가 유지됨
        splashScreen.setKeepOnScreenCondition(() -> false); // 바로 커스텀 스플래시로 전환

        // 커스텀 스플래시 화면 표시 후 다음 화면으로 이동
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 로그인 상태 확인
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            boolean isLoggedIn = prefs.getBoolean(Constants.PREF_IS_LOGGED_IN, false);

            // 로그인 상태에 따라 적절한 화면으로 이동
            Intent intent;
            if (isLoggedIn) {
                intent = new Intent(SplashActivity.this, MainActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, LoginActivity.class);
            }

            startActivity(intent);
            finish(); // 스플래시 액티비티 종료
        }, SPLASH_DELAY);
    }
}