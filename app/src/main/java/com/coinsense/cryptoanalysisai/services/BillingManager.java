package com.coinsense.cryptoanalysisai.services;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;


import com.google.firebase.functions.FirebaseFunctions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 충돌 없는 새로운 BillingManager
 * 단일 Firebase 업데이트 메서드와 우선순위 기반 상태 관리
 */
public class BillingManager implements PurchasesUpdatedListener {
    private static final String TAG = "BillingManager";

    // 구독 상품 ID
    public static final String MONTHLY_SUBSCRIPTION_ID = "com.coinsense.cryptoanalysisai.monthly";
    public static final String YEARLY_SUBSCRIPTION_ID = "com.coinsense.cryptoanalysisai.yearly";

    // 싱글톤 인스턴스
    private static BillingManager instance;
    private final Context context;

    // Billing Client
    private BillingClient billingClient;
    private List<ProductDetails> productDetailsList = new ArrayList<>();

    // 리스너 및 상태 관리
    private BillingStatusListener billingStatusListener;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // 구독 모니터링
    private boolean isMonitoring = false;
    private Runnable monitoringTask;

    private BillingManager(Context context) {
        this.context = context.getApplicationContext();
        initializeBillingClient();
    }

    public static synchronized BillingManager getInstance(Context context) {
        if (instance == null) {
            instance = new BillingManager(context);
        }
        return instance;
    }

    // ==================== Firebase 업데이트 (단일 메서드) ====================

    /**
     * 🔧 Lambda 변수 final 오류가 수정된 보호된 Firebase 업데이트 메서드
     */
    private void updateFirebaseSubscriptionSafe(boolean isSubscribed, boolean isAutoRenewing,
                                                String subscriptionType, long expiryTimestamp) {


        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "사용자가 로그인되어 있지 않음");
            return;
        }

        // 동시 업데이트 방지
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "다른 Firebase 업데이트가 진행 중 - 요청 무시");
            return;
        }

        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions")
                .child(user.getUid());

        // 🔧 기존 취소 상태 확인 후 보호된 업데이트
        subscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    // 🔧 Lambda에서 사용할 변수들을 final로 선언
                    final boolean finalIsSubscribed;
                    final boolean finalIsAutoRenewing;
                    final long finalExpiryTimestamp;
                    final boolean finalIsCancelled;

                    // 기존 데이터 확인
                    if (snapshot.exists()) {
                        Boolean existingCancelled = snapshot.child("cancelled").getValue(Boolean.class);
                        Boolean existingAutoRenewing = snapshot.child("autoRenewing").getValue(Boolean.class);
                        Long existingExpiry = snapshot.child("expiryTimestamp").getValue(Long.class);
                        Boolean existingSubscribed = snapshot.child("subscribed").getValue(Boolean.class);

                        Log.d(TAG, "🔍 기존 구독 상태 확인:");
                        Log.d(TAG, "   기존 취소여부: " + existingCancelled);
                        Log.d(TAG, "   기존 자동갱신: " + existingAutoRenewing);
                        Log.d(TAG, "   새로운 자동갱신: " + isAutoRenewing);

                        // 🔧 핵심 보호 로직: 이미 취소된 상태라면 활성 상태로 되돌리지 않음
                        if (existingCancelled != null && existingCancelled &&
                                existingExpiry != null && System.currentTimeMillis() < existingExpiry) {

                            Log.w(TAG, "⚠️ 이미 취소된 구독을 활성 상태로 되돌리려는 시도 차단");
                            Log.w(TAG, "   취소된 구독은 만료까지 취소 상태 유지");

                            // 취소 상태 강제 유지 (final 변수 설정)
                            finalIsCancelled = true;
                            finalIsAutoRenewing = false;
                            finalIsSubscribed = true; // 만료 전까지는 구독 상태 유지
                            finalExpiryTimestamp = existingExpiry; // 기존 만료 시간 유지

                            isProcessing.set(false);

                            // 바로 업데이트 실행
                            performProtectedUpdate(subscriptionRef, finalIsSubscribed, finalIsAutoRenewing,
                                    finalIsCancelled, subscriptionType, finalExpiryTimestamp);
                            return;
                        }
                    }

                    // 🔧 새로운 취소 상태 감지 (final 변수 설정)
                    if (isSubscribed && !isAutoRenewing) {
                        Log.d(TAG, "🔴 구독 취소 감지! 취소 상태로 설정");
                        finalIsCancelled = true;
                        finalIsAutoRenewing = false;
                        finalIsSubscribed = isSubscribed;
                        finalExpiryTimestamp = expiryTimestamp;
                    } else if (!isSubscribed) {
                        Log.d(TAG, "🔴 구독 완전 만료");
                        finalIsCancelled = true;
                        finalIsAutoRenewing = false;
                        finalIsSubscribed = false;
                        finalExpiryTimestamp = 0;
                    } else {
                        // 정상 활성 구독
                        finalIsCancelled = false;
                        finalIsAutoRenewing = isAutoRenewing;
                        finalIsSubscribed = isSubscribed;
                        finalExpiryTimestamp = expiryTimestamp;
                    }

                    // 보호된 업데이트 실행
                    performProtectedUpdate(subscriptionRef, finalIsSubscribed, finalIsAutoRenewing,
                            finalIsCancelled, subscriptionType, finalExpiryTimestamp);

                } catch (Exception e) {
                    Log.e(TAG, "보호된 업데이트 중 예외 발생: " + e.getMessage());
                    isProcessing.set(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "기존 구독 상태 확인 실패: " + error.getMessage());
                isProcessing.set(false);
            }
        });
    }

    /**
     * 🔧 별도 메서드로 분리된 실제 업데이트 로직
     */
    private void performProtectedUpdate(DatabaseReference subscriptionRef,
                                        boolean finalIsSubscribed,
                                        boolean finalIsAutoRenewing,
                                        boolean finalIsCancelled,
                                        String subscriptionType,
                                        long finalExpiryTimestamp) {

        // 📊 최종 업데이트 데이터 준비
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("subscribed", finalIsSubscribed);
        updateData.put("autoRenewing", finalIsAutoRenewing);
        updateData.put("cancelled", finalIsCancelled);
        updateData.put("subscriptionType", subscriptionType);
        updateData.put("expiryTimestamp", finalExpiryTimestamp);
        updateData.put("lastUpdated", System.currentTimeMillis());
        updateData.put("updateSource", "BillingManager");
        updateData.put("protectionApplied", finalIsCancelled && finalIsSubscribed); // 보호 적용 여부

        Log.d(TAG, "🔧 보호된 Firebase 업데이트:");
        Log.d(TAG, "   구독상태: " + finalIsSubscribed);
        Log.d(TAG, "   자동갱신: " + finalIsAutoRenewing);
        Log.d(TAG, "   취소여부: " + finalIsCancelled);
        Log.d(TAG, "   구독타입: " + subscriptionType);
        Log.d(TAG, "   만료일: " + (finalExpiryTimestamp > 0 ? new Date(finalExpiryTimestamp) : "없음"));
        Log.d(TAG, "   보호적용: " + updateData.get("protectionApplied"));

        subscriptionRef.updateChildren(updateData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ 보호된 Firebase 업데이트 성공");

                    // SubscriptionManager 동기화
                    SubscriptionManager subscriptionManager = SubscriptionManager.getInstance(context);
                    subscriptionManager.setSubscribed(finalIsSubscribed, finalExpiryTimestamp, subscriptionType);

                    // UI 알림
                    if (billingStatusListener != null) {
                        billingStatusListener.onSubscriptionStatusChanged(finalIsSubscribed, finalIsAutoRenewing);
                    }

                    isProcessing.set(false);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ 보호된 Firebase 업데이트 실패: " + e.getMessage());
                    isProcessing.set(false);
                });
    }

    // ==================== Billing Client 초기화 ====================

    private void initializeBillingClient() {
        billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build();
    }

    public void connectToPlayBillingService() {
        if (billingClient.isReady()) {
            Log.d(TAG, "BillingClient 이미 연결됨");
            querySubscriptionProducts();
            queryPurchases();
            return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "결제 서비스 연결 끊어짐 - 재연결 시도중...");
                connectToPlayBillingService();
            }

            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "✅ 결제 서비스 연결 성공");
                    querySubscriptionProducts();
                    queryPurchases();
                } else {
                    Log.e(TAG, "❌ 결제 서비스 연결 실패: " + billingResult.getResponseCode());
                }
            }
        });
    }

    // 🔧 추가: 더 간단한 강제 취소 상태 설정 메서드
    public void forceCancelledState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions").child(user.getUid());

        subscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Long expiryTimestamp = snapshot.child("expiryTimestamp").getValue(Long.class);
                    String subscriptionType = snapshot.child("subscriptionType").getValue(String.class);

                    // final 변수로 선언
                    final long finalExpiry = (expiryTimestamp != null) ? expiryTimestamp : 0;
                    final String finalType = (subscriptionType != null) ? subscriptionType : Constants.SUBSCRIPTION_MONTHLY;

                    if (finalExpiry > System.currentTimeMillis()) {
                        Map<String, Object> updateData = new HashMap<>();
                        updateData.put("autoRenewing", false);
                        updateData.put("cancelled", true);
                        updateData.put("subscribed", true); // 만료 전까지는 구독 상태 유지
                        updateData.put("lastUpdated", System.currentTimeMillis());
                        updateData.put("forceCancelled", true); // 강제 취소 표시

                        subscriptionRef.updateChildren(updateData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "✅ 구독 취소 상태 강제 설정 완료");

                                    // UI 업데이트
                                    if (billingStatusListener != null) {
                                        billingStatusListener.onSubscriptionStatusChanged(true, false);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "❌ 강제 취소 설정 실패: " + e.getMessage());
                                });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "강제 취소 설정 실패: " + error.getMessage());
            }
        });
    }

    // ==================== 상품 정보 조회 ====================

    private void querySubscriptionProducts() {
        Log.d(TAG, "🔍 상품 정보 조회 시작");

        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();

        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(MONTHLY_SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());

        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(YEARLY_SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());

        Log.d(TAG, "🔍 조회할 상품 목록:");
        Log.d(TAG, "   월간: " + MONTHLY_SUBSCRIPTION_ID);
        Log.d(TAG, "   연간: " + YEARLY_SUBSCRIPTION_ID);

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(@NonNull BillingResult billingResult,
                                                 @NonNull List<ProductDetails> productDetailsList) {

                Log.d(TAG, "📡 상품 정보 응답:");
                Log.d(TAG, "   응답 코드: " + billingResult.getResponseCode());
                Log.d(TAG, "   상품 개수: " + productDetailsList.size());

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (productDetailsList.isEmpty()) {
                        Log.e(TAG, "❌ 상품 목록이 비어있음!");
                        Log.e(TAG, "   Google Play Console에서 상품을 확인하세요");
                    } else {
                        Log.d(TAG, "✅ 상품 정보 조회 성공:");
                        for (ProductDetails product : productDetailsList) {
                            Log.d(TAG, "   상품 ID: " + product.getProductId());
                            Log.d(TAG, "   상품 제목: " + product.getTitle());

                            List<ProductDetails.SubscriptionOfferDetails> offers = product.getSubscriptionOfferDetails();
                            if (offers != null && !offers.isEmpty()) {
                                Log.d(TAG, "   오퍼 개수: " + offers.size());

                                // 가격 정보 로그
                                ProductDetails.SubscriptionOfferDetails offer = offers.get(0);
                                List<ProductDetails.PricingPhase> phases = offer.getPricingPhases().getPricingPhaseList();
                                if (!phases.isEmpty()) {
                                    ProductDetails.PricingPhase phase = phases.get(0);
                                    Log.d(TAG, "   가격: " + phase.getFormattedPrice());
                                }
                            } else {
                                Log.e(TAG, "   ❌ 구독 오퍼가 없음!");
                            }
                        }

                        BillingManager.this.productDetailsList = productDetailsList;

                        if (billingStatusListener != null) {
                            billingStatusListener.onProductDetailsReceived(productDetailsList);
                        }
                    }
                } else {
                    Log.e(TAG, "❌ 상품 정보 조회 실패:");
                    Log.e(TAG, "   오류 코드: " + billingResult.getResponseCode());
                    Log.e(TAG, "   디버그 메시지: " + billingResult.getDebugMessage());

                    if (billingStatusListener != null) {
                        billingStatusListener.onBillingError("상품 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.");
                    }
                }
            }
        });
    }

    // ==================== 구매 내역 조회 및 처리 ====================

    public void queryPurchases() {
        if (!billingClient.isReady()) {
            Log.w(TAG, "BillingClient가 준비되지 않음 - 연결 시도");
            connectToPlayBillingService();
            return;
        }

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build();

        billingClient.queryPurchasesAsync(params, new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(@NonNull BillingResult billingResult,
                                                 @NonNull List<Purchase> purchases) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "🔍 구매 내역 조회 성공: " + purchases.size() + "개");
                    processPurchases(purchases);
                } else {
                    Log.e(TAG, "❌ 구매 내역 조회 실패: " + billingResult.getResponseCode());
                }
            }
        });
    }

    private void processPurchases(List<Purchase> purchases) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "사용자가 로그인되어 있지 않음");
            return;
        }

        Log.d(TAG, "📋 구매 내역 처리 시작: " + purchases.size() + "개");

        if (purchases.isEmpty()) {
            handleNoPurchases();
            return;
        }

        // 유효한 구매만 처리
        Purchase validPurchase = null;
        for (Purchase purchase : purchases) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                validPurchase = purchase;
                break;
            }
        }

        if (validPurchase != null) {
            handleValidPurchase(validPurchase);
        } else {
            handleNoPurchases();
        }
    }

    // calculateExpiryTimestamp 메서드와 관련 코드 수정


// ==================== 정확한 만료 시간 계산 ====================

    // 🔧 추가: 구독 취소 감지 시 즉시 보호 적용
    private void handleValidPurchase(Purchase purchase) {
        Log.d(TAG, "✅ 유효한 구매 처리: " + purchase.getOrderId());

        // 구매 확인 처리
        if (!purchase.isAcknowledged()) {
            acknowledgePurchase(purchase);
        }

        // 소유자 정보 저장
        savePurchaseOwner(purchase);

        // 🔧 구독 취소 감지 로직 강화
        boolean isAutoRenewing = purchase.isAutoRenewing();
        String subscriptionType = determineSubscriptionType(purchase);
        boolean isSubscribed = purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED;

        // 🔴 구독 취소 즉시 감지
        if (isSubscribed && !isAutoRenewing) {
            Log.w(TAG, "🔴 구독 취소 즉시 감지 - 취소 상태 보호 적용");

            // 취소 상태를 즉시 Firebase에 강제 설정
            long expiryTimestamp = calculateLocalExpiryTimestamp(purchase, subscriptionType);

            // 보호된 업데이트 (취소 상태 유지)
            updateFirebaseSubscriptionSafe(true, false, subscriptionType, expiryTimestamp);
            return;
        }

        // 🔧 서버에서 정확한 구독 정보 가져오기 (기존 로직)
        getAccurateSubscriptionInfo(purchase, new SubscriptionInfoCallback() {
            @Override
            public void onSuccess(AccurateSubscriptionInfo info) {
                Log.d(TAG, "✅ 정확한 구독 정보 수신: autoRenewing=" + info.autoRenewing);

                // 보호된 업데이트 사용
                updateFirebaseSubscriptionSafe(
                        info.isValid,
                        info.autoRenewing,
                        info.subscriptionType,
                        info.expiryTimestamp
                );
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "⚠️ 서버 검증 실패, 로컬 계산 사용: " + error);

                long expiryTimestamp = calculateLocalExpiryTimestamp(purchase, subscriptionType);

                // 보호된 업데이트 사용
                updateFirebaseSubscriptionSafe(isSubscribed, isAutoRenewing, subscriptionType, expiryTimestamp);
            }
        });
    }

    /**
     * 🔧 Firebase Functions를 통한 정확한 구독 정보 조회
     */
    private void getAccurateSubscriptionInfo(Purchase purchase, SubscriptionInfoCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("사용자가 로그인되어 있지 않음");
            return;
        }

        // Firebase Functions에 전송할 데이터
        Map<String, Object> data = new HashMap<>();
        data.put("purchaseToken", purchase.getPurchaseToken());
        data.put("productId", purchase.getProducts().get(0));
        data.put("userId", user.getUid());
        data.put("orderId", purchase.getOrderId());

        Log.d(TAG, "🔍 서버에서 정확한 구독 정보 요청: " + purchase.getOrderId());

        FirebaseFunctions.getInstance()
                .getHttpsCallable("verifySubscription")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> response = (Map<String, Object>) result.getData();
                        Log.d(TAG, "📡 서버 응답 수신: " + response);

                        // 응답 파싱
                        Boolean isValid = (Boolean) response.get("valid");
                        Boolean autoRenewing = (Boolean) response.get("autoRenewing");
                        Long expiryTimeMillis = null;
                        String orderId = (String) response.get("orderId");

                        // expiryTimeMillis 안전하게 파싱
                        Object expiryObj = response.get("expiryTimeMillis");
                        if (expiryObj instanceof Number) {
                            expiryTimeMillis = ((Number) expiryObj).longValue();
                        } else if (expiryObj instanceof String) {
                            try {
                                expiryTimeMillis = Long.parseLong((String) expiryObj);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "만료 시간 파싱 실패: " + expiryObj);
                            }
                        }

                        if (isValid != null && expiryTimeMillis != null && autoRenewing != null) {
                            String subscriptionType = determineSubscriptionType(purchase);

                            AccurateSubscriptionInfo info = new AccurateSubscriptionInfo(
                                    isValid,
                                    autoRenewing,
                                    expiryTimeMillis,
                                    subscriptionType,
                                    orderId
                            );

                            callback.onSuccess(info);
                        } else {
                            callback.onError("서버 응답 데이터 불완전: valid=" + isValid +
                                    ", expiry=" + expiryTimeMillis + ", autoRenew=" + autoRenewing);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "서버 응답 파싱 오류", e);
                        callback.onError("응답 파싱 실패: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase Functions 호출 실패", e);
                    callback.onError("서버 통신 실패: " + e.getMessage());
                });
    }

    /**
     * 🔧 로컬 계산 (fallback용)
     */
    private long calculateLocalExpiryTimestamp(Purchase purchase, String subscriptionType) {
        long purchaseTime = purchase.getPurchaseTime();
        long durationMillis;

        if (Constants.SUBSCRIPTION_YEARLY.equals(subscriptionType)) {
            durationMillis = 365L * 24 * 60 * 60 * 1000; // 1년
        } else {
            durationMillis = 30L * 24 * 60 * 60 * 1000; // 1개월
        }

        Log.d(TAG, "📅 로컬 만료 시간 계산:");
        Log.d(TAG, "   구매일: " + new Date(purchaseTime));
        Log.d(TAG, "   만료일: " + new Date(purchaseTime + durationMillis));

        return purchaseTime + durationMillis;
    }



    private void handleNoPurchases() {
        Log.d(TAG, "🔍 활성 구매 없음 - 기존 데이터 확인");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions").child(user.getUid());

        subscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Long expiryTimestamp = snapshot.child("expiryTimestamp").getValue(Long.class);
                    String subscriptionType = snapshot.child("subscriptionType").getValue(String.class);

                    if (expiryTimestamp != null && System.currentTimeMillis() < expiryTimestamp) {
                        // 🔧 서버에서 최신 상태 확인 (구매 토큰 없이)
                        verifyExistingSubscription(user.getUid(), subscriptionType, expiryTimestamp);
                    } else {
                        // 완전히 만료됨
                        Log.d(TAG, "🔍 구독 완전 만료");
                        updateFirebaseSubscriptionSafe(false, false, Constants.SUBSCRIPTION_NONE, 0);
                    }
                } else {
                    Log.d(TAG, "🔍 구독 데이터 없음 - 신규 사용자");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "기존 구독 데이터 확인 실패: " + error.getMessage());
            }
        });
    }

    /**
     * 🔧 기존 구독 상태 서버 검증
     */
    private void verifyExistingSubscription(String userId, String subscriptionType, long expiryTimestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("subscriptionType", subscriptionType);
        data.put("checkCancellation", true);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("checkSubscriptionStatus")
                .call(data)
                .addOnSuccessListener(result -> {
                    try {
                        Map<String, Object> response = (Map<String, Object>) result.getData();
                        Boolean isActive = (Boolean) response.get("isActive");
                        Boolean autoRenewing = (Boolean) response.get("autoRenewing");

                        if (isActive != null && autoRenewing != null) {
                            Log.d(TAG, "🔍 기존 구독 서버 검증 결과: active=" + isActive + ", autoRenew=" + autoRenewing);
                            updateFirebaseSubscriptionSafe(isActive, autoRenewing, subscriptionType, expiryTimestamp);
                        } else {
                            // 서버 검증 실패 시 취소된 상태로 가정
                            Log.d(TAG, "🔍 서버 검증 불완전, 취소된 상태로 설정");
                            updateFirebaseSubscriptionSafe(true, false, subscriptionType, expiryTimestamp);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "기존 구독 검증 파싱 오류", e);
                        // fallback: 취소된 상태로 설정
                        updateFirebaseSubscriptionSafe(true, false, subscriptionType, expiryTimestamp);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "기존 구독 서버 검증 실패", e);
                    // fallback: 취소된 상태로 설정
                    updateFirebaseSubscriptionSafe(true, false, subscriptionType, expiryTimestamp);
                });
    }

    // ==================== 콜백 인터페이스 및 데이터 클래스 ====================

    /**
     * 정확한 구독 정보 콜백
     */
    interface SubscriptionInfoCallback {
        void onSuccess(AccurateSubscriptionInfo info);
        void onError(String error);
    }

    /**
     * 정확한 구독 정보 클래스
     */
    static class AccurateSubscriptionInfo {
        final boolean isValid;
        final boolean autoRenewing;
        final long expiryTimestamp;
        final String subscriptionType;
        final String orderId;

        AccurateSubscriptionInfo(boolean isValid, boolean autoRenewing, long expiryTimestamp,
                                 String subscriptionType, String orderId) {
            this.isValid = isValid;
            this.autoRenewing = autoRenewing;
            this.expiryTimestamp = expiryTimestamp;
            this.subscriptionType = subscriptionType;
            this.orderId = orderId;
        }

        @Override
        public String toString() {
            return "AccurateSubscriptionInfo{" +
                    "isValid=" + isValid +
                    ", autoRenewing=" + autoRenewing +
                    ", expiryTimestamp=" + new Date(expiryTimestamp) +
                    ", subscriptionType='" + subscriptionType + '\'' +
                    ", orderId='" + orderId + '\'' +
                    '}';
        }
    }

    // ==================== 추가: 타임아웃 처리 ====================

    /**
     * 🔧 타임아웃이 있는 서버 검증
     */
    private void getAccurateSubscriptionInfoWithTimeout(Purchase purchase, SubscriptionInfoCallback callback) {
        CompletableFuture<Void> serverCall = CompletableFuture.runAsync(() -> {
            getAccurateSubscriptionInfo(purchase, callback);
        });

        // 10초 타임아웃
        CompletableFuture<Void> timeout = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(10000); // 10초 대기
                callback.onError("서버 응답 타임아웃 (10초)");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 둘 중 먼저 완료되는 것 사용
        CompletableFuture.anyOf(serverCall, timeout);
    }

    // ==================== 유틸리티 메서드 ====================

    private String determineSubscriptionType(Purchase purchase) {
        List<String> productIds = purchase.getProducts();
        if (productIds.contains(MONTHLY_SUBSCRIPTION_ID)) {
            return Constants.SUBSCRIPTION_MONTHLY;
        } else if (productIds.contains(YEARLY_SUBSCRIPTION_ID)) {
            return Constants.SUBSCRIPTION_YEARLY;
        }
        return Constants.SUBSCRIPTION_NONE;
    }

    private long calculateExpiryTimestamp(Purchase purchase, String subscriptionType) {
        // 간단한 계산 (실제로는 Google Play Developer API에서 정확한 정보를 받아와야 함)
        long purchaseTime = purchase.getPurchaseTime();
        long durationMillis;

        if (Constants.SUBSCRIPTION_YEARLY.equals(subscriptionType)) {
            durationMillis = 365L * 24 * 60 * 60 * 1000; // 1년
        } else {
            durationMillis = 30L * 24 * 60 * 60 * 1000; // 1개월
        }

        return purchaseTime + durationMillis;
    }

    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.acknowledgePurchase(params, new AcknowledgePurchaseResponseListener() {
            @Override
            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "✅ 구매 확인 완료");
                } else {
                    Log.e(TAG, "❌ 구매 확인 실패: " + billingResult.getResponseCode());
                }
            }
        });
    }

    private void savePurchaseOwner(Purchase purchase) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            SharedPreferences prefs = context.getSharedPreferences("billing_purchase_info", Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(purchase.getOrderId(), currentUser.getEmail())
                    .apply();
            Log.d(TAG, "💾 구매 소유자 저장: " + purchase.getOrderId() + " -> " + currentUser.getEmail());
        }
    }

    // ==================== 구독 시작 ====================

    public void startSubscription(Activity activity, String subscriptionId) {
        if (!billingClient.isReady()) {
            Log.e(TAG, "BillingClient가 준비되지 않았습니다.");
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("결제 서비스가 준비되지 않았습니다. 다시 시도해주세요.");
            }
            return;
        }

        ProductDetails productDetails = null;
        for (ProductDetails details : productDetailsList) {
            if (details.getProductId().equals(subscriptionId)) {
                productDetails = details;
                break;
            }
        }

        if (productDetails == null) {
            Log.e(TAG, "상품 정보를 찾을 수 없습니다: " + subscriptionId);
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("상품 정보를 찾을 수 없습니다.");
            }
            return;
        }

        List<ProductDetails.SubscriptionOfferDetails> offerDetailsList =
                productDetails.getSubscriptionOfferDetails();

        if (offerDetailsList == null || offerDetailsList.isEmpty()) {
            Log.e(TAG, "구독 옵션이 없습니다: " + subscriptionId);
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("구독 옵션이 없습니다.");
            }
            return;
        }

        String offerToken = offerDetailsList.get(0).getOfferToken();

        BillingFlowParams.ProductDetailsParams productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build();

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(List.of(productDetailsParams))
                .build();

        BillingResult billingResult = billingClient.launchBillingFlow(activity, billingFlowParams);

        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "구매 플로우 시작 실패: " + billingResult.getResponseCode());
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("결제를 시작할 수 없습니다.");
            }
        }
    }

    // ==================== 구매 업데이트 리스너 ====================

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            Log.d(TAG, "🎉 새 구매 감지: " + purchases.size() + "개");

            for (Purchase purchase : purchases) {
                savePurchaseOwner(purchase);
            }

            processPurchases(purchases);

            if (billingStatusListener != null) {
                billingStatusListener.onPurchaseComplete();
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "사용자가 구매를 취소했습니다.");
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("결제가 취소되었습니다.");
            }
        } else {
            Log.e(TAG, "구매 업데이트 오류: " + billingResult.getResponseCode());
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("결제 중 오류가 발생했습니다.");
            }
        }
    }

    // ==================== 구독 모니터링 ====================

    public void startSubscriptionMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "구독 모니터링이 이미 실행 중입니다.");
            return;
        }

        isMonitoring = true;
        monitoringTask = new Runnable() {
            @Override
            public void run() {
                if (isMonitoring) {
                    queryPurchases();
                    mainHandler.postDelayed(this, 60000); // 1분마다 확인
                }
            }
        };

        mainHandler.post(monitoringTask);
        Log.d(TAG, "✅ 구독 모니터링 시작 (1분 간격)");
    }

    public void stopSubscriptionMonitoring() {
        isMonitoring = false;
        if (monitoringTask != null) {
            mainHandler.removeCallbacks(monitoringTask);
            monitoringTask = null;
        }
        Log.d(TAG, "⏹️ 구독 모니터링 중지");
    }

    public void checkSubscriptionStatusNow() {
        Log.d(TAG, "🔍 구독 상태 즉시 확인");
        queryPurchases();
    }

    // ==================== 리스너 설정 ====================

    public void setBillingStatusListener(BillingStatusListener listener) {
        this.billingStatusListener = listener;
    }

    public List<ProductDetails> getProductDetailsList() {
        return new ArrayList<>(productDetailsList);
    }

    // ==================== 리스너 인터페이스 ====================

    public interface BillingStatusListener {
        void onPurchaseComplete();
        void onBillingError(String error);
        void onProductDetailsReceived(List<ProductDetails> productDetailsList);

        default void onSubscriptionStatusChanged(boolean isSubscribed, boolean isAutoRenewing) {
            onPurchaseComplete();
        }

        default void onSubscriptionCancelled(long remainingDays) {
            // 기본 구현
        }

        default void onSubscriptionExpired() {
            // 기본 구현
        }
    }
}