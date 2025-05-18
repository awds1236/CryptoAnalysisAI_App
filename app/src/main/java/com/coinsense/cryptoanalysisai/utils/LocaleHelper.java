package com.coinsense.cryptoanalysisai.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.content.SharedPreferences;
import android.content.ContextWrapper;

import java.util.Locale;

public class LocaleHelper {

    // Context 래핑 메서드 - 모든 Activity에서 호출
    public static ContextWrapper wrap(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String language = prefs.getString("pref_language", "ko");

        Locale newLocale = new Locale(language);

        // API 수준에 따라 다른 처리
        Resources res = context.getResources();
        Configuration configuration = res.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(newLocale);
            LocaleList localeList = new LocaleList(newLocale);
            LocaleList.setDefault(localeList);
            configuration.setLocales(localeList);
            context = context.createConfigurationContext(configuration);
        } else {
            configuration.locale = newLocale;
            res.updateConfiguration(configuration, res.getDisplayMetrics());
        }

        return new ContextWrapper(context);
    }

    // 기존 메서드도 유지
    public static void setLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        } else {
            config.locale = locale;
        }

        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}