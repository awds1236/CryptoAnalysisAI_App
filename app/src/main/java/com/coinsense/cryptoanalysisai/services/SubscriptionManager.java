package com.coinsense.cryptoanalysisai.services;

import com.coinsense.cryptoanalysisai.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.coinsense.cryptoanalysisai.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
    private final FirebaseSubscriptionManager firebaseSubscriptionManager;
    private final BillingManager billingManager; // BillingManager 인스턴스 추가

    private SubscriptionManager(Context context) {
        this.context = context.getApplicationContext();
        this.firebaseSubscriptionManager = FirebaseSubscriptionManager.getInstance();
        this.billingManager = BillingManager.getInstance(context); // BillingManager 초기화

        // 로그인된 사용자 설정
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            firebaseSubscriptionManager.setCurrentUser(currentUser);
        }
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
        return firebaseSubscriptionManager.isSubscribed();
    }

    /**
     * 구독 상태 설정
     *
     * @param subscribed 구독 상태
     * @param expiryTimestamp 만료일 타임스탬프
     * @param subscriptionType 구독 유형 (월간, 연간)
     */
    public void setSubscribed(boolean subscribed, long expiryTimestamp, String subscriptionType) {
        firebaseSubscriptionManager.setSubscribed(subscribed, expiryTimestamp, subscriptionType,
                new FirebaseSubscriptionManager.OnSubscriptionDataChangeListener() {
                    @Override
                    public void onSubscriptionDataChanged(FirebaseSubscriptionManager.SubscriptionData subscriptionData) {
                        Log.d(TAG, "구독 상태 업데이트 완료: " + subscriptionData.isSubscribed());
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "구독 상태 업데이트 실패: " + errorMessage);
                    }
                });
    }

    /**
     * 현재 구독 상태를 Google Play에서 직접 확인
     */
    public void verifySubscription() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.d(TAG, "로그인된 사용자 없음, 구독 검증 불가");
            return;
        }

        // Google Play에서 현재 구독 상태 확인 (구매 내역 쿼리)
        billingManager.queryPurchases();
    }

    /**
     * 로컬 구독 정보 초기화
     * 로그인/로그아웃 시 호출됨
     */
    public void clearLocalSubscriptionData() {
        // Firebase 구독 관리자의 캐시 데이터 초기화
        firebaseSubscriptionManager.clearCachedData();

        // SharedPreferences에서 구독 관련 정보 삭제
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(Constants.PREF_IS_SUBSCRIBED)
                .remove(Constants.PREF_SUBSCRIPTION_EXPIRY)
                .remove(Constants.PREF_SUBSCRIPTION_TYPE)
                .remove(Constants.PREF_SUBSCRIPTION_START_TIME)
                .remove(Constants.PREF_SUBSCRIPTION_AUTO_RENEWING)
                .apply();

        Log.d(TAG, "로컬 구독 정보가 초기화되었습니다.");
    }

    /**
     * 구독 취소 - Google Play 구독 관리 페이지로 이동
     */
    public void cancelSubscription() {
        // Google Play 구독 관리 페이지로 이동
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://play.google.com/store/account/subscriptions"));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // 실제 구독 취소는 Google Play에서 이루어짐
        // BillingClient가 다음 실행 시 구독 상태 확인 후 업데이트
    }

    /**
     * 테스트를 위한 구독 즉시 취소 (실제 앱에서는 사용하지 않음)
     */
    public void cancelSubscriptionForTesting() {
        firebaseSubscriptionManager.cancelSubscription(
                new FirebaseSubscriptionManager.OnSubscriptionDataChangeListener() {
                    @Override
                    public void onSubscriptionDataChanged(FirebaseSubscriptionManager.SubscriptionData subscriptionData) {
                        Log.d(TAG, "구독이 즉시 취소되었습니다");
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.e(TAG, "구독 취소 실패: " + errorMessage);
                    }
                });
    }

    /**
     * 구독 만료까지 남은 일수
     */
    public int getRemainingDays() {
        FirebaseSubscriptionManager.SubscriptionData data =
                firebaseSubscriptionManager.getCachedSubscriptionData();

        if (data == null || data.getExpiryTimestamp() <= System.currentTimeMillis()) {
            return 0;
        }

        long remainingTime = data.getExpiryTimestamp() - System.currentTimeMillis();
        return (int) (remainingTime / (24 * 60 * 60 * 1000L));
    }

    /**
     * 구독 만료일 문자열 (yyyy-MM-dd 형식)
     */
    public String getExpiryDateString() {
        FirebaseSubscriptionManager.SubscriptionData data =
                firebaseSubscriptionManager.getCachedSubscriptionData();

        if (data == null || data.getExpiryTimestamp() <= 0) {
            return "구독 없음";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(data.getExpiryTimestamp()));
    }

    /**
     * 구독 시작일 문자열 (yyyy-MM-dd 형식)
     */
    public String getStartDateString() {
        FirebaseSubscriptionManager.SubscriptionData data =
                firebaseSubscriptionManager.getCachedSubscriptionData();

        if (data == null || data.getStartTimestamp() <= 0) {
            return "정보 없음";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(data.getStartTimestamp()));
    }

    /**
     * 구독 유형 반환
     */
    public String getSubscriptionType() {
        FirebaseSubscriptionManager.SubscriptionData data =
                firebaseSubscriptionManager.getCachedSubscriptionData();

        if (data == null) {
            return Constants.SUBSCRIPTION_NONE;
        }

        return data.getSubscriptionType();
    }

    /**
     * 구독 상품의 현지화된 가격 설정
     */
    public void setSubscriptionPrices(String monthlyPrice, String yearlyPrice) {
        firebaseSubscriptionManager.setSubscriptionPrices(monthlyPrice, yearlyPrice, null);
    }

    /**
     * 월간 구독 가격 반환
     */
    // getMonthlyPrice() 메서드 수정
    public String getMonthlyPrice() {
        // 현재 언어 설정 확인
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String language = prefs.getString("pref_language", "ko"); // 기본값은 한국어

        if ("ko".equals(language)) {
            return "월 ₩15,000원";
        } else {
            return "$10.7/month";
        }
    }

    public String getYearlyPrice() {
        // 현재 언어 설정 확인
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String language = prefs.getString("pref_language", "ko"); // 기본값은 한국어

        if ("ko".equals(language)) {
            return "연 ₩125,000원 (월 ₩10,400원)";
        } else {
            return "$90.00/year ($7.5/month)";
        }
    }
    /**
     * 구독이 자동 갱신 상태인지 확인
     */
    public boolean isAutoRenewing() {
        FirebaseSubscriptionManager.SubscriptionData data =
                firebaseSubscriptionManager.getCachedSubscriptionData();

        if (data == null) {
            return false;
        }

        return data.isAutoRenewing();
    }

    /**
     * 자동 갱신 상태 설정
     */
    public void setAutoRenewing(boolean autoRenewing) {
        firebaseSubscriptionManager.setAutoRenewing(autoRenewing, null);
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

    /**
     * 사용자 변경 시 구독 정보 업데이트
     */
    public void updateUser(FirebaseUser user) {
        firebaseSubscriptionManager.setCurrentUser(user);
    }
}