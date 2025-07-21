package com.coinsense.cryptoanalysisai.services;

import android.content.Context;
import android.util.Log;

import com.android.billingclient.api.Purchase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

/**
 * 구독 검증을 위한 유틸리티 클래스
 * Firebase Functions를 통한 서버 측 검증 지원
 */
public class SubscriptionValidator {
    private static final String TAG = "SubscriptionValidator";

    private final Context context;
    private final FirebaseFunctions firebaseFunctions;

    public SubscriptionValidator(Context context) {
        this.context = context;
        this.firebaseFunctions = FirebaseFunctions.getInstance();
    }

    /**
     * 🔧 Firebase Functions를 통한 구독 검증 (서버 사이드 검증)
     */
    public void verifySubscriptionWithServer(Purchase purchase, OnSubscriptionValidatedListener listener) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            listener.onValidationFailed("사용자 인증 실패");
            return;
        }

        // Firebase Functions에 전송할 데이터 준비
        Map<String, Object> data = new HashMap<>();
        data.put("purchaseToken", purchase.getPurchaseToken());
        data.put("productId", purchase.getProducts().get(0)); // 첫 번째 상품 ID
        data.put("userId", user.getUid());

        Log.d(TAG, "서버 구독 검증 요청 시작: " + purchase.getOrderId());
        Log.d(TAG, "사용자: " + user.getUid());
        Log.d(TAG, "상품 ID: " + purchase.getProducts().get(0));

        // Firebase Functions 호출
        firebaseFunctions
                .getHttpsCallable("verifySubscription")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> response = (Map<String, Object>) result.getData();
                        Log.d(TAG, "서버 검증 응답 받음: " + response.toString());

                        // 응답 데이터 파싱 (null 체크 포함)
                        Boolean isValid = (Boolean) response.get("valid");
                        Long expiryTime = null;
                        Boolean autoRenewing = false;
                        String orderId = null;

                        // null 체크 후 형변환
                        Object expiryObj = response.get("expiryTimeMillis");
                        if (expiryObj instanceof Number) {
                            expiryTime = ((Number) expiryObj).longValue();
                        }

                        Object renewingObj = response.get("autoRenewing");
                        if (renewingObj instanceof Boolean) {
                            autoRenewing = (Boolean) renewingObj;
                        }

                        Object orderObj = response.get("orderId");
                        if (orderObj instanceof String) {
                            orderId = (String) orderObj;
                        }

                        if (isValid != null && isValid && expiryTime != null) {
                            // 검증 성공
                            SubscriptionInfo subscriptionInfo = new SubscriptionInfo(
                                    true,
                                    expiryTime,
                                    autoRenewing,
                                    orderId
                            );
                            listener.onValidationSuccess(subscriptionInfo);
                            Log.d(TAG, "서버 구독 검증 성공: " + orderId +
                                    ", 만료일: " + new java.util.Date(expiryTime) +
                                    ", 자동갱신: " + autoRenewing);
                        } else {
                            // 검증 실패
                            String errorMsg = (String) response.get("message");
                            listener.onValidationFailed(errorMsg != null ? errorMsg : "구독이 유효하지 않습니다");
                            Log.w(TAG, "서버 구독 검증 실패: " + errorMsg);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "서버 응답 파싱 오류", e);
                        listener.onValidationFailed("서버 응답을 처리할 수 없습니다: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase Functions 호출 실패", e);
                    listener.onValidationFailed("서버와 통신할 수 없습니다: " + e.getMessage());
                });
    }

    /**
     * 구독 정보 클래스
     */
    public static class SubscriptionInfo {
        private final boolean isValid;
        private final long expiryTimeMillis;
        private final boolean autoRenewing;
        private final String orderId;

        public SubscriptionInfo(boolean isValid, long expiryTimeMillis, boolean autoRenewing, String orderId) {
            this.isValid = isValid;
            this.expiryTimeMillis = expiryTimeMillis;
            this.autoRenewing = autoRenewing;
            this.orderId = orderId;
        }

        public boolean isValid() {
            return isValid;
        }

        public long getExpiryTimeMillis() {
            return expiryTimeMillis;
        }

        public boolean isAutoRenewing() {
            return autoRenewing;
        }

        public String getOrderId() {
            return orderId;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expiryTimeMillis;
        }

        public boolean isCancelled() {
            return !autoRenewing;
        }

        public long getRemainingTimeMillis() {
            return Math.max(0, expiryTimeMillis - System.currentTimeMillis());
        }
    }

    /**
     * 구독 검증 결과 리스너
     */
    public interface OnSubscriptionValidatedListener {
        void onValidationSuccess(SubscriptionInfo subscriptionInfo);
        void onValidationFailed(String error);
    }
}