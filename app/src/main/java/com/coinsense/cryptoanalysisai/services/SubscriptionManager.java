package com.coinsense.cryptoanalysisai.services;

import android.content.Context;
import android.content.SharedPreferences;

import com.coinsense.cryptoanalysisai.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 구독 상태를 관리하는 클래스
 */
public class SubscriptionManager {

    private static final String TAG = "SubscriptionManager";
    private static final String PREF_USER_SUBSCRIPTION_PREFIX = "subscription_";
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

    // 사용자 ID 기반으로 구독 상태 확인
    public boolean isSubscribed(String userId) {
        if (userId == null || userId.isEmpty()) {
            return isSubscribed(); // 기존 방식으로 폴백
        }

        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean isSubscribed = prefs.getBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId, false);

        if (isSubscribed) {
            // 구독 만료일 확인
            long expiryTimestamp = prefs.getLong(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_expiry", 0);
            return System.currentTimeMillis() < expiryTimestamp;
        }

        return false;
    }

    /**
     * 사용자가 구독 중인지 확인
     */
    // 기존 isSubscribed() 메소드는 유지하되 현재 로그인한 사용자 ID 확인
    public boolean isSubscribed() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String currentUserId = prefs.getString(Constants.PREF_USER_ID, "");

        if (currentUserId != null && !currentUserId.isEmpty()) {
            return isSubscribed(currentUserId);
        }

        // 기존 코드 (사용자 ID 없는 경우 기기 단위 구독 확인)
        boolean isSubscribed = prefs.getBoolean(Constants.PREF_IS_SUBSCRIBED, false);
        if (isSubscribed) {
            long expiryTimestamp = prefs.getLong(Constants.PREF_SUBSCRIPTION_EXPIRY, 0);
            return System.currentTimeMillis() < expiryTimestamp;
        }
        return false;
    }

    // 사용자 ID 기반 구독 상태 설정
    public void setSubscribed(boolean subscribed, long expiryTimestamp, String subscriptionType, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // 사용자 ID 기반 구독 정보 저장
        editor.putBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId, subscribed);
        editor.putLong(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_expiry", expiryTimestamp);
        editor.putString(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_type", subscriptionType);

        // 구독 시작 시간 저장 (새로 구독하는 경우)
        if (subscribed && !prefs.getBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId, false)) {
            editor.putLong(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_start", System.currentTimeMillis());
        }

        // 기존 방식과의 호환성 유지
        editor.putBoolean(Constants.PREF_IS_SUBSCRIBED, subscribed);
        editor.putLong(Constants.PREF_SUBSCRIPTION_EXPIRY, expiryTimestamp);
        editor.putString(Constants.PREF_SUBSCRIPTION_TYPE, subscriptionType);

        editor.apply();
    }

    // 기존 setSubscribed 메소드 오버로드 (하위 호환성 유지)
    public void setSubscribed(boolean subscribed, long expiryTimestamp, String subscriptionType) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String currentUserId = prefs.getString(Constants.PREF_USER_ID, "");

        if (currentUserId != null && !currentUserId.isEmpty()) {
            setSubscribed(subscribed, expiryTimestamp, subscriptionType, currentUserId);
        } else {
            // 기존 코드 (사용자 ID 없는 경우)
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(Constants.PREF_IS_SUBSCRIBED, subscribed);
            editor.putLong(Constants.PREF_SUBSCRIPTION_EXPIRY, expiryTimestamp);
            editor.putString(Constants.PREF_SUBSCRIPTION_TYPE, subscriptionType);

            if (subscribed && !prefs.getBoolean(Constants.PREF_IS_SUBSCRIBED, false)) {
                editor.putLong(Constants.PREF_SUBSCRIPTION_START_TIME, System.currentTimeMillis());
            }

            editor.apply();
        }
    }

    // 구독 유형 반환 (사용자 ID 기반)
    public String getSubscriptionType(String userId) {
        if (userId == null || userId.isEmpty()) {
            return getSubscriptionType(); // 기존 방식으로 폴백
        }

        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_type", Constants.SUBSCRIPTION_NONE);
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
        return prefs.getString(Constants.PREF_MONTHLY_PRICE, "월 ₩15,000");
    }

    /**
     * 연간 구독 가격 반환
     */
    public String getYearlyPrice() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_YEARLY_PRICE, "연 126,000원 (월 10,500원)");
    }

    // SubscriptionManager.java의 isAutoRenewing() 메서드 수정
    public boolean isAutoRenewing() {
        // 먼저 BillingManager가 초기화되었는지 확인
        BillingManager billingManager = BillingManager.getInstance(context);
        if (billingManager.isReady()) {
            // 현재 구독 유형 확인
            String subscriptionType = getSubscriptionType();
            String subscriptionId;

            // 구독 유형에 따른 상품 ID 설정
            if (Constants.SUBSCRIPTION_MONTHLY.equals(subscriptionType)) {
                subscriptionId = BillingManager.MONTHLY_SUBSCRIPTION_ID;
            } else if (Constants.SUBSCRIPTION_YEARLY.equals(subscriptionType)) {
                subscriptionId = BillingManager.YEARLY_SUBSCRIPTION_ID;
            } else {
                // 구독이 없는 경우
                return false;
            }

            // BillingManager를 통해 자동 갱신 상태 확인
            return billingManager.isSubscriptionAutoRenewing(subscriptionId);
        }

        // BillingClient가 준비되지 않은 경우 저장된 값 반환
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