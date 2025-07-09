package com.coinsense.cryptoanalysisai.ui.activities;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.coinsense.cryptoanalysisai.utils.LocaleHelper;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // 1. 먼저 언어 설정 적용
        Context localeContext = LocaleHelper.wrap(newBase);

        // 2. 그 다음 폰트 크기 고정 적용
        Context fixedFontContext = createFixedFontScaleContext(localeContext);

        super.attachBaseContext(fixedFontContext);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleHelper.wrap(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * 시스템 폰트 크기 설정을 무시하고 앱의 폰트 크기를 항상 기본값(1.0)으로 고정
     */
    private Context createFixedFontScaleContext(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.fontScale = 1.0f; // 항상 기본 크기로 고정
        return context.createConfigurationContext(configuration);
    }
}