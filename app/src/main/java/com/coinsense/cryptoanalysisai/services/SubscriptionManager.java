package com.coinsense.cryptoanalysisai.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.coinsense.cryptoanalysisai.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

    // 현재 로그인한 사용자 ID 가져오기 (여러 메서드에서 공통으로 사용)
    private String getCurrentUserId() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_USER_ID, "");
    }

    /**
     * 사용자 ID 기반으로 구독 상태 확인
     */
    public boolean isSubscribed(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false; // 사용자 ID가 없으면 구독 안 된 것으로 처리
        }

        // 1. 먼저 로컬 캐시 확인 (빠른 응답 위해)
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean localSubscribed = prefs.getBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId, false);
        long localExpiryTimestamp = prefs.getLong(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_expiry", 0);

        // 2. 로컬 캐시에 있고 만료되지 않았으면 바로 반환
        if (localSubscribed && System.currentTimeMillis() < localExpiryTimestamp) {
            return true;
        }

        // 3. Firebase에서 최신 구독 정보 확인 (비동기 작업이지만 여기서는 간단하게 처리)
        final boolean[] firebaseSubscribed = {false};
        final boolean[] checkCompleted = {false};

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference userSubscriptionRef = database.getReference("subscriptions").child(userId);

        userSubscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    boolean isSubscribed = dataSnapshot.child("isSubscribed").getValue(Boolean.class);
                    long expiryTimestamp = dataSnapshot.child("expiryTimestamp").getValue(Long.class);
                    boolean isCancelled = dataSnapshot.child("isCancelled").getValue(Boolean.class);

                    // 중요: 여기서 취소된 구독이어도 만료일까지는 서비스를 제공
                    if (isSubscribed && System.currentTimeMillis() < expiryTimestamp) {
                        firebaseSubscribed[0] = true;

                        // 로컬 캐시 업데이트
                        String subscriptionType = dataSnapshot.child("subscriptionType").getValue(String.class);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId, true);
                        editor.putLong(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_expiry", expiryTimestamp);
                        editor.putString(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_type", subscriptionType);
                        editor.putBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_cancelled", isCancelled);
                        editor.apply();
                    }
                }
                checkCompleted[0] = true;
                synchronized (checkCompleted) {
                    checkCompleted.notify();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase 구독 정보 로드 실패: " + databaseError.getMessage());
                checkCompleted[0] = true;
                synchronized (checkCompleted) {
                    checkCompleted.notify();
                }
            }
        });

        // 응답을 기다림 (최대 3초)
        try {
            synchronized (checkCompleted) {
                if (!checkCompleted[0]) {
                    checkCompleted.wait(3000);
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "구독 상태 확인 중 인터럽트 발생", e);
        }

        return firebaseSubscribed[0];
    }

    /**
     * 구독 취소 처리 - 만료 기간까지는 서비스 유지
     */
    public void cancelSubscription(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }

        // Firebase에서 현재 구독 정보 가져오기
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference userSubscriptionRef = database.getReference("subscriptions").child(userId);

        userSubscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    boolean isSubscribed = dataSnapshot.child("isSubscribed").getValue(Boolean.class);
                    long expiryTimestamp = dataSnapshot.child("expiryTimestamp").getValue(Long.class);

                    if (isSubscribed && System.currentTimeMillis() < expiryTimestamp) {
                        // 구독은 여전히 유효하지만 취소 상태로 표시
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("isCancelled", true);
                        updates.put("autoRenewing", false);

                        userSubscriptionRef.updateChildren(updates)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "구독 취소 처리 완료 (만료일: " + new Date(expiryTimestamp) + ")");

                                    // 로컬 캐시도 업데이트
                                    SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_cancelled", true);
                                    editor.putBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_auto_renewing", false);
                                    editor.apply();
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "구독 취소 처리 실패: " + e.getMessage()));
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Firebase 구독 정보 로드 실패: " + databaseError.getMessage());
            }
        });
    }

    /**
     * 사용자가 구독 중인지 확인
     */
    public boolean isSubscribed() {
        String currentUserId = getCurrentUserId();
        if (!currentUserId.isEmpty()) {
            return isSubscribed(currentUserId);
        }
        return false;
    }

    // 사용자 ID 기반 구독 상태 설정
    public void setSubscribed(boolean subscribed, long expiryTimestamp, String subscriptionType, String userId) {
        if (userId == null || userId.isEmpty()) {
            return; // 사용자 ID가 없으면 저장하지 않음
        }

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

        // 자동 갱신 상태 저장
        editor.putBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_auto_renewing", true);

        editor.apply();
    }

    // 기존 setSubscribed 메소드 오버로드 (현재 로그인 사용자로 설정)
    public void setSubscribed(boolean subscribed, long expiryTimestamp, String subscriptionType) {
        String currentUserId = getCurrentUserId();
        if (!currentUserId.isEmpty()) {
            setSubscribed(subscribed, expiryTimestamp, subscriptionType, currentUserId);
        }
    }

    // 구독 유형 반환 (사용자 ID 기반)
    public String getSubscriptionType(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Constants.SUBSCRIPTION_NONE;
        }

        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_type", Constants.SUBSCRIPTION_NONE);
    }

    /**
     * 구독 유형 반환 (현재 로그인 사용자)
     */
    public String getSubscriptionType() {
        String currentUserId = getCurrentUserId();
        if (!currentUserId.isEmpty()) {
            return getSubscriptionType(currentUserId);
        }
        return Constants.SUBSCRIPTION_NONE;
    }

    /**
     * 구독 만료까지 남은 일수 (사용자 ID 기반)
     */
    public int getRemainingDays(String userId) {
        if (userId == null || userId.isEmpty()) {
            return 0;
        }

        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        long expiryTimestamp = prefs.getLong(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_expiry", 0);

        if (expiryTimestamp <= System.currentTimeMillis()) {
            return 0;
        }

        long remainingTime = expiryTimestamp - System.currentTimeMillis();
        return (int) (remainingTime / (24 * 60 * 60 * 1000L));
    }

    /**
     * 구독 만료까지 남은 일수 (현재 로그인 사용자)
     */
    public int getRemainingDays() {
        String currentUserId = getCurrentUserId();
        if (!currentUserId.isEmpty()) {
            return getRemainingDays(currentUserId);
        }
        return 0;
    }

    /**
     * 구독 만료일 문자열 (yyyy-MM-dd 형식) - 사용자 ID 기반
     */
    public String getExpiryDateString(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "구독 없음";
        }

        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        long expiryTimestamp = prefs.getLong(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_expiry", 0);

        if (expiryTimestamp <= 0) {
            return "구독 없음";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(expiryTimestamp));
    }

    /**
     * 구독 만료일 문자열 (현재 로그인 사용자)
     */
    public String getExpiryDateString() {
        String currentUserId = getCurrentUserId();
        if (!currentUserId.isEmpty()) {
            return getExpiryDateString(currentUserId);
        }
        return "구독 없음";
    }

    /**
     * 구독 시작일 문자열 (yyyy-MM-dd 형식) - 사용자 ID 기반
     */
    public String getStartDateString(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "정보 없음";
        }

        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        long startTimestamp = prefs.getLong(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_start", 0);

        if (startTimestamp <= 0) {
            return "정보 없음";
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(new Date(startTimestamp));
    }

    /**
     * 구독 시작일 문자열 (현재 로그인 사용자)
     */
    public String getStartDateString() {
        String currentUserId = getCurrentUserId();
        if (!currentUserId.isEmpty()) {
            return getStartDateString(currentUserId);
        }
        return "정보 없음";
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

    /**
     * 자동 갱신 상태 확인 (사용자 ID 기반)
     */
    public boolean isAutoRenewing(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        // 먼저 BillingManager가 초기화되었는지 확인
        BillingManager billingManager = BillingManager.getInstance(context);
        if (billingManager.isReady()) {
            // 현재 구독 유형 확인
            String subscriptionType = getSubscriptionType(userId);
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
        return prefs.getBoolean(PREF_USER_SUBSCRIPTION_PREFIX + userId + "_auto_renewing", false);
    }

    /**
     * 자동 갱신 상태 확인 (현재 로그인 사용자)
     */
    public boolean isAutoRenewing() {
        String currentUserId = getCurrentUserId();
        if (!currentUserId.isEmpty()) {
            return isAutoRenewing(currentUserId);
        }
        return false;
    }

    /**
     * 다음 결제일 문자열 반환 (사용자 ID 기반)
     */
    public String getNextBillingDateString(String userId) {
        if (userId == null || userId.isEmpty() || !isSubscribed(userId)) {
            return "구독 없음";
        }

        // 구독 만료일이 다음 결제일
        return getExpiryDateString(userId);
    }

    /**
     * 다음 결제일 문자열 반환 (현재 로그인 사용자)
     */
    public String getNextBillingDateString() {
        String currentUserId = getCurrentUserId();
        if (!currentUserId.isEmpty()) {
            return getNextBillingDateString(currentUserId);
        }
        return "구독 없음";
    }
}