package com.example.cryptoanalysisai.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.cryptoanalysisai.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 구독 상태를 관리하는 클래스
 */
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

    /**
     * 사용자가 구독 중인지 확인
     */
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

    /**
     * 구독 상태 설정
     *
     * @param subscribed 구독 상태
     * @param expiryTimestamp 만료일 타임스탬프
     * @param subscriptionType 구독 유형 (월간, 연간)
     */
    public void setSubscribed(boolean subscribed, long expiryTimestamp, String subscriptionType) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Constants.PREF_IS_SUBSCRIBED, subscribed);
        editor.putLong(Constants.PREF_SUBSCRIPTION_EXPIRY, expiryTimestamp);
        editor.putString(Constants.PREF_SUBSCRIPTION_TYPE, subscriptionType);

        // 구독 시작 시간 저장 (새로 구독하는 경우)
        if (subscribed && !prefs.getBoolean(Constants.PREF_IS_SUBSCRIBED, false)) {
            editor.putLong(Constants.PREF_SUBSCRIPTION_START_TIME, System.currentTimeMillis());
        }

        editor.apply();
    }

    /**
     * 구독 취소 - 실제로는 BillingClient로 구글 플레이 구독 취소로 이동
     * 여기서는 테스트용으로 바로 구독 해제
     */
    public void cancelSubscription() {
        // 실제 구현에서는 Google Play 구독 관리 화면으로 이동
        // 지금은 테스트용으로 직접 구독 취소
        setSubscribed(false, 0, Constants.SUBSCRIPTION_NONE);
    }

    /**
     * 구독 만료까지 남은 일수
     */
    public int getRemainingDays() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        long expiryTimestamp = prefs.getLong(Constants.PREF_SUBSCRIPTION_EXPIRY, 0);

        if (expiryTimestamp <= System.currentTimeMillis()) {
            return 0;
        }

        long remainingTime = expiryTimestamp - System.currentTimeMillis();
        return (int) (remainingTime / (24 * 60 * 60 * 1000L));
    }

    /**
     * 구독 만료일 문자열 (yyyy-MM-dd 형식)
     */
    public String getExpiryDateString() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        long expiryTimestamp = prefs.getLong(Constants.PREF_SUBSCRIPTION_EXPIRY, 0);

        if (expiryTimestamp <= 0) {
            return "구독 없음";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(expiryTimestamp));
    }

    /**
     * 구독 시작일 문자열 (yyyy-MM-dd 형식)
     */
    public String getStartDateString() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        long startTimestamp = prefs.getLong(Constants.PREF_SUBSCRIPTION_START_TIME, 0);

        if (startTimestamp <= 0) {
            return "정보 없음";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(startTimestamp));
    }

    /**
     * 구독 유형 반환
     */
    public String getSubscriptionType() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_SUBSCRIPTION_TYPE, Constants.SUBSCRIPTION_NONE);
    }

    /**
     * 구독 상품의 현지화된 가격 설정
     */
    public void setSubscriptionPrices(String monthlyPrice, String yearlyPrice) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREF_MONTHLY_PRICE, monthlyPrice);
        editor.putString(Constants.PREF_YEARLY_PRICE, yearlyPrice);
        editor.apply();
    }

    /**
     * 월간 구독 가격 반환
     */
    public String getMonthlyPrice() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_MONTHLY_PRICE, "월 ₩9,900");
    }

    /**
     * 연간 구독 가격 반환
     */
    public String getYearlyPrice() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_YEARLY_PRICE, "연 ₩95,000 (월 ₩7,920)");
    }

    /**
     * 구독이 자동 갱신 상태인지 확인
     * 이 메서드는 실제로는 Google Play 구독 API에서 확인해야 함
     */
    public boolean isAutoRenewing() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(Constants.PREF_SUBSCRIPTION_AUTO_RENEWING, true);
    }

    /**
     * 다음 결제일 문자열 반환
     */
    public String getNextBillingDateString() {
        if (!isSubscribed()) {
            return "구독 없음";
        }

        // 구독 만료일이 다음 결제일
        return getExpiryDateString();
    }
}