package com.coinsense.cryptoanalysisai.ui.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.core.splashscreen.SplashScreen;

import com.coinsense.cryptoanalysisai.MainActivity;
import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.coinsense.cryptoanalysisai.utils.LocaleHelper;

public class SplashActivity extends BaseActivity {

    private static final long SPLASH_DELAY = 1500; // 1.5초

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Android 12+ 스플래시 화면 설정 - 액티비티 생성 전에 설치
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // 시스템 스플래시 화면이 즉시 사라지지 않도록 설정
        splashScreen.setKeepOnScreenCondition(() -> false); // 바로 커스텀 스플래시로 전환

        // 앱이 처음 실행되는지 확인
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean(getString(R.string.is_first_run), true);

        if (isFirstRun) {
            // 앱 첫 실행 시 언어 선택 다이얼로그 표시
            showLanguageSelectionDialog();
        } else {
            // 일반적인 스플래시 화면 처리
            proceedToNextScreen();
        }
    }

    private void showLanguageSelectionDialog() {
        // 다이얼로그 레이아웃 인플레이트
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_language_selection, null);
        RadioGroup rgLanguage = dialogView.findViewById(R.id.rgLanguage);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);

        // 다이얼로그 생성
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false) // 뒤로가기 버튼으로 취소 방지
                .create();

        // 확인 버튼 클릭 리스너
        btnConfirm.setOnClickListener(v -> {
            // 선택된 언어 저장
            RadioButton selectedRadioButton = dialogView.findViewById(rgLanguage.getCheckedRadioButtonId());
            String language;

            if (selectedRadioButton.getId() == R.id.rbEnglish) {
                language = "en";
            } else {
                language = "ko"; // 기본값은 한국어
            }

            // 언어 설정 저장
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("pref_language", language);
            editor.putBoolean(getString(R.string.is_first_run), false); // 첫 실행 표시 제거
            editor.apply();

            // 언어 적용
            LocaleHelper.setLocale(SplashActivity.this, language);

            // 다이얼로그 닫기
            dialog.dismiss();

            // 다음 화면으로 진행
            proceedToNextScreen();
        });

        // 다이얼로그 표시
        dialog.show();
    }

    private void proceedToNextScreen() {
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