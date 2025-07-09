package com.coinsense.cryptoanalysisai.services;

import android.content.Context;
import android.util.Log;

import com.android.billingclient.api.Purchase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 구독 검증을 위한 유틸리티 클래스
 * Google Play Developer API를 통한 서버 측 검증 지원
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
     * 🔧 서버를 통한 구독 검증 (권장 방법)
     * Firebase Functions 또는 백엔드 서버에서 Google Play Developer API 호출
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

        // Firebase Functions 호출
        firebaseFunctions
                .getHttpsCallable("verifySubscription")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> response = (Map<String, Object>) result.getData();
                        boolean isValid = (Boolean) response.get("valid");
                        long expiryTime = ((Number) response.get("expiryTimeMillis")).longValue();
                        boolean autoRenewing = (Boolean) response.get("autoRenewing");
                        String orderId = (String) response.get("orderId");

                        if (isValid) {
                            SubscriptionInfo subscriptionInfo = new SubscriptionInfo(
                                    isValid, expiryTime, autoRenewing, orderId
                            );
                            listener.onValidationSuccess(subscriptionInfo);
                        } else {
                            listener.onValidationFailed("서버에서 구독이 유효하지 않다고 응답");
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "서버 응답 파싱 오류: " + e.getMessage());
                        listener.onValidationFailed("서버 응답 파싱 실패");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "서버 검증 실패: " + e.getMessage());
                    listener.onValidationFailed("서버 검증 실패: " + e.getMessage());
                });
    }

    /**
     * 🔧 로컬 구독 유효성 검사 (보조 방법)
     * 서버 검증이 실패했을 때 사용하는 fallback 검증
     */
    public SubscriptionInfo validateSubscriptionLocally(Purchase purchase) {
        try {
            // 구매 토큰 기본 검증
            if (purchase.getPurchaseToken() == null || purchase.getPurchaseToken().isEmpty()) {
                return new SubscriptionInfo(false, 0, false, null);
            }

            // 구매 상태 확인
            if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
                return new SubscriptionInfo(false, 0, false, null);
            }

            // 자동 갱신 상태 확인
            boolean autoRenewing = purchase.isAutoRenewing();

            // 구매 시간 기반 대략적인 만료 시간 계산 (정확하지 않음)
            long purchaseTime = purchase.getPurchaseTime();
            long estimatedExpiryTime;

            // 상품 ID에 따른 기간 계산
            String productId = purchase.getProducts().get(0);
            if (BillingManager.MONTHLY_SUBSCRIPTION_ID.equals(productId)) {
                estimatedExpiryTime = purchaseTime + (30L * 24 * 60 * 60 * 1000); // 30일
            } else if (BillingManager.YEARLY_SUBSCRIPTION_ID.equals(productId)) {
                estimatedExpiryTime = purchaseTime + (365L * 24 * 60 * 60 * 1000); // 365일
            } else {
                return new SubscriptionInfo(false, 0, false, null);
            }

            // 현재 시간과 비교하여 유효성 확인
            boolean isValid = System.currentTimeMillis() < estimatedExpiryTime;

            // 자동 갱신이 비활성화된 경우 (구독 취소된 경우)
            if (!autoRenewing) {
                // 현재 기간이 끝날 때까지만 유효
                isValid = System.currentTimeMillis() < estimatedExpiryTime;
                Log.w(TAG, "구독이 취소됨 - 현재 기간까지만 유효: " + isValid);
            }

            return new SubscriptionInfo(isValid, estimatedExpiryTime, autoRenewing, purchase.getOrderId());

        } catch (Exception e) {
            Log.e(TAG, "로컬 구독 검증 오류: " + e.getMessage());
            return new SubscriptionInfo(false, 0, false, null);
        }
    }

    /**
     * 🔧 구독 만료 시간 정확도 개선을 위한 히스토리 추적
     */
    public void trackSubscriptionHistory(Purchase purchase, SubscriptionInfo validatedInfo) {
        // SharedPreferences 또는 로컬 데이터베이스에 구독 히스토리 저장
        // 이를 통해 갱신 패턴을 파악하고 더 정확한 만료 시간 예측 가능

        try {
            JSONObject historyEntry = new JSONObject();
            historyEntry.put("purchaseTime", purchase.getPurchaseTime());
            historyEntry.put("orderID", purchase.getOrderId());
            historyEntry.put("autoRenewing", validatedInfo.autoRenewing);
            historyEntry.put("expiryTime", validatedInfo.expiryTimeMillis);
            historyEntry.put("validationTime", System.currentTimeMillis());

            // 히스토리 저장 로직 구현
            // saveSubscriptionHistory(historyEntry);

        } catch (Exception e) {
            Log.e(TAG, "구독 히스토리 추적 오류: " + e.getMessage());
        }
    }

    /**
     * 구독 정보 클래스
     */
    public static class SubscriptionInfo {
        public final boolean isValid;
        public final long expiryTimeMillis;
        public final boolean autoRenewing;
        public final String orderId;

        public SubscriptionInfo(boolean isValid, long expiryTimeMillis, boolean autoRenewing, String orderId) {
            this.isValid = isValid;
            this.expiryTimeMillis = expiryTimeMillis;
            this.autoRenewing = autoRenewing;
            this.orderId = orderId;
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

/*
Firebase Functions 예시 코드 (JavaScript):

const functions = require('firebase-functions');
const {google} = require('googleapis');

// Google Play Developer API 설정
const androidpublisher = google.androidpublisher({
    version: 'v3',
    auth: 'YOUR_SERVICE_ACCOUNT_KEY'
});

exports.verifySubscription = functions.https.onCall(async (data, context) => {
    try {
        const {purchaseToken, productId, userId} = data;

        // Google Play Developer API 호출
        const result = await androidpublisher.purchases.subscriptions.get({
            packageName: 'com.coinsense.cryptoanalysisai',
            subscriptionId: productId,
            token: purchaseToken
        });

        const subscription = result.data;

        return {
            valid: subscription.paymentState === 1, // 결제 완료
            expiryTimeMillis: parseInt(subscription.expiryTimeMillis),
            autoRenewing: subscription.autoRenewing,
            orderId: subscription.orderId,
            paymentState: subscription.paymentState
        };

    } catch (error) {
        console.error('구독 검증 오류:', error);
        throw new functions.https.HttpsError('internal', '구독 검증 실패');
    }
});
*/