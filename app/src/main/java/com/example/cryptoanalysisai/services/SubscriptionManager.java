package com.example.cryptoanalysisai.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.cryptoanalysisai.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SubscriptionManager {

    private static final String TAG = "SubscriptionManager";
    private static SubscriptionManager instance;
    private final Context context;

    private SubscriptionManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SubscriptionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SubscriptionManager(context);
        }
        return instance;
    }

    public boolean isSubscribed() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean isSubscribed = prefs.getBoolean(Constants.PREF_IS_SUBSCRIBED, false);

        if (isSubscribed) {
            // 구독 만료일 확인
            long expiryTimestamp = prefs.getLong(Constants.PREF_SUBSCRIPTION_EXPIRY, 0);
            return System.currentTimeMillis() < expiryTimestamp;
        }

        return false;
    }

    public void setSubscribed(boolean subscribed, long expiryTimestamp, String subscriptionType) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Constants.PREF_IS_SUBSCRIBED, subscribed);
        editor.putLong(Constants.PREF_SUBSCRIPTION_EXPIRY, expiryTimestamp);
        editor.putString(Constants.PREF_SUBSCRIPTION_TYPE, subscriptionType);
        editor.apply();
    }

    // 테스트용 메서드 - 1달 구독 활성화
    public void activateMonthlySubscription() {
        // 현재 시간 + 30일
        long expiryTimestamp = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000L);
        setSubscribed(true, expiryTimestamp, Constants.SUBSCRIPTION_MONTHLY);
    }

    // 테스트용 메서드 - 1년 구독 활성화
    public void activateYearlySubscription() {
        // 현재 시간 + 365일
        long expiryTimestamp = System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000L);
        setSubscribed(true, expiryTimestamp, Constants.SUBSCRIPTION_YEARLY);
    }

    // 구독 취소
    public void cancelSubscription() {
        setSubscribed(false, 0, Constants.SUBSCRIPTION_NONE);
    }

    // 구독 만료까지 남은 일수
    public int getRemainingDays() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        long expiryTimestamp = prefs.getLong(Constants.PREF_SUBSCRIPTION_EXPIRY, 0);

        if (expiryTimestamp <= System.currentTimeMillis()) {
            return 0;
        }

        long remainingTime = expiryTimestamp - System.currentTimeMillis();
        return (int) (remainingTime / (24 * 60 * 60 * 1000L));
    }

    // 구독 만료일 문자열 (yyyy-MM-dd 형식)
    public String getExpiryDateString() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        long expiryTimestamp = prefs.getLong(Constants.PREF_SUBSCRIPTION_EXPIRY, 0);

        if (expiryTimestamp <= 0) {
            return "구독 없음";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(expiryTimestamp));
    }

    // 구독 유형 반환
    public String getSubscriptionType() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_SUBSCRIPTION_TYPE, Constants.SUBSCRIPTION_NONE);
    }
}