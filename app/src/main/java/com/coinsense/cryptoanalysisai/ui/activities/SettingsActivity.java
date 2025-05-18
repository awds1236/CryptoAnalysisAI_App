// SettingsActivity.java 파일 생성
package com.coinsense.cryptoanalysisai.ui.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.databinding.ActivitySettingsBinding;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.coinsense.cryptoanalysisai.utils.LocaleHelper;

import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private static final String PREF_DARK_MODE = "pref_dark_mode";
    private static final String PREF_LANGUAGE = "pref_language";

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

        // 테마 설정 상태 불러오기
        boolean isDarkMode = isDarkModeEnabled();
        if (isDarkMode) {
            binding.rbDarkMode.setChecked(true);
        } else {
            binding.rbLightMode.setChecked(true);
        }

        // 언어 설정 상태 불러오기
        String currentLanguage = getCurrentLanguage();
        if ("en".equals(currentLanguage)) {
            binding.rbEnglish.setChecked(true);
        } else {
            binding.rbKorean.setChecked(true);
        }

        // 테마 라디오 버튼 리스너
        binding.rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            boolean darkModeEnabled = (checkedId == R.id.rbDarkMode);
            setDarkMode(darkModeEnabled);
        });

        // 언어 라디오 버튼 리스너
        binding.rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            String languageCode = (checkedId == R.id.rbEnglish) ? "en" : "ko";
            setLanguage(languageCode);
        });

        // 저장 버튼
        binding.btnSave.setOnClickListener(v -> {
            // 설정이 즉시 적용되므로 바로 종료
            finish();
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

    private void setDarkMode(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_DARK_MODE, enabled);
        editor.apply();

        // 테마 모드 설정
        if (enabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // 액티비티 재생성
        recreate();
    }

    private void setLanguage(String languageCode) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_LANGUAGE, languageCode);
        editor.apply();

        // 언어 변경 및 앱 재시작
        LocaleHelper.setLocale(this, languageCode);
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