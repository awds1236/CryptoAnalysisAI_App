package com.coinsense.cryptoanalysisai.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.databinding.ActivitySettingsBinding;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.coinsense.cryptoanalysisai.utils.LocaleHelper;

public class SettingsActivity extends BaseActivity {

    private ActivitySettingsBinding binding;
    private static final String PREF_DARK_MODE = "pref_dark_mode";
    private static final String PREF_LANGUAGE = "pref_language";

    // 임시 변수 추가
    private boolean tempIsDarkMode;
    private String tempLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 툴바 설정
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.action_settings);
        }

        // 현재 설정 불러오기
        tempIsDarkMode = isDarkModeEnabled();
        tempLanguage = getCurrentLanguage();

        // UI 초기 상태 설정
        updateUI();

        // 테마 라디오 버튼 리스너
        binding.rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            // 즉시 적용하지 않고 임시 변수에 저장
            tempIsDarkMode = (checkedId == R.id.rbDarkMode);
        });

        // 언어 라디오 버튼 리스너
        binding.rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            // 즉시 적용하지 않고 임시 변수에 저장
            tempLanguage = (checkedId == R.id.rbEnglish) ? "en" : "ko";
        });

        // 저장 버튼
        binding.btnSave.setOnClickListener(v -> {
            // 모든 설정을 한 번에 저장하고 앱 재시작
            saveAllSettings();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isDarkModeEnabled() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_DARK_MODE, true); // 기본값은 다크 모드
    }

    private String getCurrentLanguage() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        return prefs.getString(PREF_LANGUAGE, "ko"); // 기본값은 한국어
    }

    // UI 상태 업데이트 메서드 추가
    private void updateUI() {
        // 테마 설정
        if (tempIsDarkMode) {
            binding.rbDarkMode.setChecked(true);
        } else {
            binding.rbLightMode.setChecked(true);
        }

        // 언어 설정
        if ("en".equals(tempLanguage)) {
            binding.rbEnglish.setChecked(true);
        } else {
            binding.rbKorean.setChecked(true);
        }
    }

    // 모든 설정을 한 번에 저장하고 앱을 재시작하는 메서드
    private void saveAllSettings() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // 변경된 설정 저장
        editor.putBoolean(PREF_DARK_MODE, tempIsDarkMode);
        editor.putString(PREF_LANGUAGE, tempLanguage);
        editor.apply();

        // 앱 재시작
        recreateApp();
    }

    private void recreateApp() {
        Intent intent = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            overridePendingTransition(0, 0);
        }
    }

}