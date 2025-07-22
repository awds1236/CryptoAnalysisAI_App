package com.coinsense.cryptoanalysisai.services;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BillingManager implements PurchasesUpdatedListener {
    private static final String TAG = "BillingManager";

    // 구독 상태 모니터링을 위한 핸들러
    private Handler subscriptionMonitorHandler;
    private Runnable subscriptionMonitorRunnable;
    private static final int MONITOR_INTERVAL = 30000; // 30초마다 확인

    // 마지막으로 확인된 구독 상태
    private boolean lastKnownAutoRenewing = false;
    private String lastKnownPurchaseToken = null;

    // 구독 상품 ID
    public static final String MONTHLY_SUBSCRIPTION_ID = "com.coinsense.cryptoanalysisai.monthly";
    public static final String YEARLY_SUBSCRIPTION_ID = "com.coinsense.cryptoanalysisai.yearly";

    private static BillingManager instance;
    private final Context context;
    private BillingClient billingClient;
    private List<ProductDetails> productDetailsList = new ArrayList<>();
    private BillingStatusListener billingStatusListener;

    // Google Play Store 계정 정보 저장
    private String currentPlayStoreAccount = null;

    private BillingManager(Context context) {
        this.context = context.getApplicationContext();
        setupBillingClient();
        detectPlayStoreAccount();
    }

    /**
     * Google Play Store 계정 감지
     */
    private void detectPlayStoreAccount() {
        try {
            AccountManager accountManager = AccountManager.get(context);
            Account[] accounts = accountManager.getAccountsByType("com.google");

            if (accounts.length > 0) {
                currentPlayStoreAccount = accounts[0].name;
                Log.d(TAG, "감지된 Play Store 계정: " + currentPlayStoreAccount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Play Store 계정 감지 실패: " + e.getMessage());
        }
    }

    public static synchronized BillingManager getInstance(Context context) {
        if (instance == null) {
            instance = new BillingManager(context);
        }
        return instance;
    }

    public void setBillingStatusListener(BillingStatusListener listener) {
        this.billingStatusListener = listener;
    }

    /**
     * BillingClient 설정
     */
    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases()
                .build();

        connectToPlayBillingService();
    }

    /**
     * 🔧 새로운 메서드: 구독 상태 실시간 모니터링 시작
     */
    public void startSubscriptionMonitoring() {
        if (subscriptionMonitorHandler == null) {
            subscriptionMonitorHandler = new Handler(Looper.getMainLooper());
        }

        subscriptionMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "🔍 정기 구독 상태 확인 중...");
                queryPurchases(); // 구독 상태 다시 확인

                // 다음 확인 예약
                subscriptionMonitorHandler.postDelayed(this, MONITOR_INTERVAL);
            }
        };

        // 모니터링 시작
        subscriptionMonitorHandler.postDelayed(subscriptionMonitorRunnable, MONITOR_INTERVAL);
        Log.d(TAG, "✅ 구독 상태 모니터링 시작 (30초 간격)");
    }

    /**
     * 🔧 새로운 메서드: 구독 상태 모니터링 중지
     */
    public void stopSubscriptionMonitoring() {
        if (subscriptionMonitorHandler != null && subscriptionMonitorRunnable != null) {
            subscriptionMonitorHandler.removeCallbacks(subscriptionMonitorRunnable);
            Log.d(TAG, "구독 상태 모니터링 중지");
        }
    }

    /**
     * Google Play 결제 서비스에 연결
     */
    public void connectToPlayBillingService() {
        if (!billingClient.isReady()) {
            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingServiceDisconnected() {
                    Log.d(TAG, "결제 서비스 연결 끊김. 재연결 시도중...");
                }

                @Override
                public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "결제 서비스 연결 성공");
                        querySubscriptionProducts();
                        queryPurchases();
                    } else {
                        Log.e(TAG, "결제 서비스 연결 실패: " + billingResult.getResponseCode());
                    }
                }
            });
        }
    }

    /**
     * 구독 상품 정보 조회
     */
    private void querySubscriptionProducts() {
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();

        productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(MONTHLY_SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
        );

        productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(YEARLY_SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
        );

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(
                params,
                new ProductDetailsResponseListener() {
                    @Override
                    public void onProductDetailsResponse(@NonNull BillingResult billingResult, @NonNull List<ProductDetails> productDetailsList) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "상품 정보 조회 성공: " + productDetailsList.size());
                            if (productDetailsList.isEmpty()) {
                                Log.w(TAG, "구독 상품이 없습니다. Google Play Console에서 인앱 상품을 설정하세요.");
                            } else {
                                BillingManager.this.productDetailsList = productDetailsList;
                                if (billingStatusListener != null) {
                                    billingStatusListener.onProductDetailsReceived(productDetailsList);
                                }
                            }
                        } else {
                            Log.e(TAG, "상품 정보 조회 실패: " + billingResult.getResponseCode());
                        }
                    }
                }
        );
    }

    /**
     * 구매 내역 조회 및 검증
     */
    public void queryPurchases() {
        if (!billingClient.isReady()) {
            Log.e(TAG, "BillingClient가 준비되지 않았습니다.");
            return;
        }

        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> purchasesList) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            processPurchases(purchasesList);
                        } else {
                            Log.e(TAG, "구매 내역 조회 실패: " + billingResult.getResponseCode());
                        }
                    }
                }
        );
    }

    /**
     * 🔧 개선된 processPurchases 메서드
     */
    private void processPurchases(List<Purchase> purchases) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "Firebase 사용자가 로그인되어 있지 않습니다");
            return;
        }

        Log.d(TAG, "현재 Firebase 사용자 ID: " + user.getUid());
        Log.d(TAG, "Google Play에서 확인된 구매 내역 수: " + (purchases != null ? purchases.size() : 0));

        if (purchases != null && !purchases.isEmpty()) {
            for (Purchase purchase : purchases) {
                Log.d(TAG, "🔍 구매 처리 시작: " + purchase.getOrderId());
                Log.d(TAG, "🔍 구매 상태 값: " + purchase.getPurchaseState());
                Log.d(TAG, "🔍 자동갱신 상태: " + purchase.isAutoRenewing());

                // 🔧 핵심: 자동갱신 상태 변화 감지
                boolean currentAutoRenewing = purchase.isAutoRenewing();
                String currentPurchaseToken = purchase.getPurchaseToken();

                // 이전 상태와 비교
                if (lastKnownPurchaseToken != null &&
                        lastKnownPurchaseToken.equals(currentPurchaseToken) &&
                        lastKnownAutoRenewing != currentAutoRenewing) {

                    if (currentAutoRenewing) {
                        Log.d(TAG, "🎉 구독 재활성화 감지!");
                    } else {
                        Log.d(TAG, "⚠️ 구독 취소 감지!");
                    }
                }

                // 상태 업데이트
                lastKnownAutoRenewing = currentAutoRenewing;
                lastKnownPurchaseToken = currentPurchaseToken;

                // 즉시 Firebase 업데이트
                handlePurchaseImmediately(purchase);
            }
        } else {
            // 🔧 구매가 없는 경우 - 완전히 만료되었을 수 있음
            Log.d(TAG, "구매 내역이 없음 - 구독 완전 만료 가능성");
            handleNoActivePurchases(user);
        }
    }

    /**
     * 🔧 새로운 메서드: 구매 즉시 처리
     */
    private void handlePurchaseImmediately(Purchase purchase) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        boolean isPurchased = purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED;
        boolean isAutoRenewing = purchase.isAutoRenewing();
        String subscriptionType = determineSubscriptionType(purchase);

        Log.d(TAG, "📊 구매 상태: " + isPurchased);
        Log.d(TAG, "📊 자동갱신: " + isAutoRenewing);
        Log.d(TAG, "📊 구독 타입: " + subscriptionType);

        long expiryTimestamp;
        boolean isSubscribed;
        boolean isCancelled;

        if (isPurchased) {
            // 구매 상태이지만 자동갱신 여부에 따라 처리
            isSubscribed = true; // 유예기간이므로 여전히 구독 중
            isCancelled = !isAutoRenewing; // 자동갱신이 꺼져있으면 취소됨

            if (isAutoRenewing) {
                // 활성 구독
                expiryTimestamp = calculateActualExpiryTime(purchase,
                        subscriptionType.equals(Constants.SUBSCRIPTION_YEARLY) ? 365 : 30);
                Log.d(TAG, "✅ 활성 구독: 만료일 " + new Date(expiryTimestamp));
            } else {
                // 취소된 구독 (유예기간)
                expiryTimestamp = calculateGracePeriodExpiryTime(purchase,
                        subscriptionType.equals(Constants.SUBSCRIPTION_YEARLY) ? 365 : 30);
                Log.d(TAG, "⚠️ 취소된 구독 (유예기간): 만료일 " + new Date(expiryTimestamp));
            }
        } else {
            // 구매 상태가 아님 - 완전히 만료됨
            isSubscribed = false;
            isCancelled = true;
            expiryTimestamp = 0;
            subscriptionType = Constants.SUBSCRIPTION_NONE;
            Log.d(TAG, "❌ 구독 완전 만료됨");
        }

        // Firebase 즉시 업데이트
        updateFirebaseSubscriptionDetailed(user, isSubscribed, expiryTimestamp,
                subscriptionType, isAutoRenewing, isCancelled);
    }

    /**
     * 🔧 새로운 메서드: 활성 구매가 없을 때 처리
     */
    private void handleNoActivePurchases(FirebaseUser user) {
        Log.d(TAG, "활성 구매 없음 - 구독 상태를 비활성화로 설정");

        // 기존 데이터 확인
        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions").child(user.getUid());

        subscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Long expiryTimestamp = snapshot.child("expiryTimestamp").getValue(Long.class);

                    if (expiryTimestamp != null && System.currentTimeMillis() < expiryTimestamp) {
                        // 아직 만료되지 않았는데 구매 내역이 없음 - 취소된 상태
                        Log.d(TAG, "구매 내역 없지만 아직 만료 전 - 취소된 상태로 처리");
                        updateFirebaseSubscriptionDetailed(user, true, expiryTimestamp,
                                snapshot.child("subscriptionType").getValue(String.class),
                                false, true);
                    } else {
                        // 완전히 만료됨
                        Log.d(TAG, "구독 완전 만료 - 비활성화 처리");
                        updateFirebaseSubscriptionDetailed(user, false, 0,
                                Constants.SUBSCRIPTION_NONE, false, true);
                    }
                } else {
                    Log.d(TAG, "구독 데이터 없음 - 신규 사용자로 처리");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "기존 구독 데이터 확인 실패: " + error.getMessage());
            }
        });
    }

    /**
     * 🔧 새로운 메서드: 상세한 Firebase 업데이트
     */
    private void updateFirebaseSubscriptionDetailed(FirebaseUser user, boolean isSubscribed,
                                                    long expiryTimestamp, String subscriptionType,
                                                    boolean isAutoRenewing, boolean isCancelled) {
        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions")
                .child(user.getUid());

        Map<String, Object> subscriptionData = new HashMap<>();
        subscriptionData.put("subscribed", isSubscribed);
        subscriptionData.put("expiryTimestamp", expiryTimestamp);
        subscriptionData.put("subscriptionType", subscriptionType);
        subscriptionData.put("autoRenewing", isAutoRenewing);  // 🔧 정확한 자동갱신 상태
        subscriptionData.put("cancelled", isCancelled);        // 🔧 정확한 취소 상태
        subscriptionData.put("lastUpdated", System.currentTimeMillis());
        subscriptionData.put("lastLocalCheck", System.currentTimeMillis());

        subscriptionRef.updateChildren(subscriptionData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Firebase 상세 업데이트 성공:");
                    Log.d(TAG, "   구독상태: " + isSubscribed);
                    Log.d(TAG, "   자동갱신: " + isAutoRenewing);
                    Log.d(TAG, "   취소여부: " + isCancelled);
                    Log.d(TAG, "   만료일: " + (expiryTimestamp > 0 ? new Date(expiryTimestamp) : "없음"));

                    // SubscriptionManager에도 반영
                    SubscriptionManager subscriptionManager = SubscriptionManager.getInstance(context);
                    subscriptionManager.setSubscribed(isSubscribed, expiryTimestamp, subscriptionType);

                    // UI 업데이트 알림
                    if (billingStatusListener != null) {
                        billingStatusListener.onPurchaseComplete();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Firebase 업데이트 실패: " + e.getMessage());
                    if (billingStatusListener != null) {
                        billingStatusListener.onBillingError("구독 상태 동기화 실패: " + e.getMessage());
                    }
                });
    }

    /**
     * 🔧 앱 시작 시 즉시 구독 상태 확인
     */
    public void checkSubscriptionStatusNow() {
        Log.d(TAG, "🔍 구독 상태 즉시 확인 시작");
        queryPurchases();

        // 5초 후 한 번 더 확인 (Google Play 동기화 대기)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "🔍 구독 상태 재확인");
            queryPurchases();
        }, 5000);
    }

    /**
     * 🔧 새 메서드: 서버 검증을 포함한 구매 처리
     */
    private void handlePurchaseVerification(Purchase purchase) {
        Log.d(TAG, "🔍 구매 서버 검증 시작: " + purchase.getOrderId());

        // 로컬 검증 결과 먼저 저장
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            boolean isSubscribed = purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED;
            boolean isAutoRenewing = purchase.isAutoRenewing();
            String subscriptionType = determineSubscriptionType(purchase);

            // 취소된 구독이지만 유예기간 내인 경우 처리
            long expiryTimestamp;
            if (isSubscribed && !isAutoRenewing) {
                // 구독 취소되었지만 아직 유효한 상태
                expiryTimestamp = calculateGracePeriodExpiryTime(purchase,
                        subscriptionType.equals(Constants.SUBSCRIPTION_YEARLY) ? 365 : 30);
                Log.d(TAG, "취소된 구독 (유예기간): 만료일 " + new Date(expiryTimestamp) +
                        ", 자동갱신: " + isAutoRenewing);
            } else if (!isSubscribed) {
                // 완전히 만료된 구독
                expiryTimestamp = 0;
                subscriptionType = Constants.SUBSCRIPTION_NONE;
            } else {
                // 활성 구독
                expiryTimestamp = calculateActualExpiryTime(purchase,
                        subscriptionType.equals(Constants.SUBSCRIPTION_YEARLY) ? 365 : 30);
            }

            // Firebase에 로컬 검증 결과 반영
            updateFirebaseSubscription(user, isSubscribed, expiryTimestamp, subscriptionType, isAutoRenewing);

            // SubscriptionManager에도 반영
            SubscriptionManager subscriptionManager = SubscriptionManager.getInstance(context);
            subscriptionManager.setSubscribed(isSubscribed, expiryTimestamp, subscriptionType);

            Log.d(TAG, "로컬 검증 완료 및 Firebase 업데이트 성공");
        }

        // 서버 검증 실행 (비동기, fallback 방식)
        SubscriptionValidator validator = new SubscriptionValidator(context);
        validator.verifySubscriptionWithServer(purchase, new SubscriptionValidator.OnSubscriptionValidatedListener() {
            @Override
            public void onValidationSuccess(SubscriptionValidator.SubscriptionInfo subscriptionInfo) {
                Log.d(TAG, "🎉 서버 검증 성공: " + subscriptionInfo.getOrderId());

                // 서버 검증 결과로 최종 업데이트 (덮어쓰기)
                if (user != null) {
                    updateFirebaseSubscription(user,
                            subscriptionInfo.isValid(),
                            subscriptionInfo.getExpiryTimeMillis(),
                            determineSubscriptionType(purchase),
                            subscriptionInfo.isAutoRenewing());

                    SubscriptionManager subscriptionManager = SubscriptionManager.getInstance(context);
                    subscriptionManager.setSubscribed(subscriptionInfo.isValid(),
                            subscriptionInfo.getExpiryTimeMillis(),
                            determineSubscriptionType(purchase));

                    Log.d(TAG, "✅ 서버 검증 결과로 최종 업데이트 완료");
                }
            }

            @Override
            public void onValidationFailed(String error) {
                Log.w(TAG, "⚠️ 서버 검증 실패 (로컬 검증 결과 유지): " + error);

                // 서버 검증 실패 시에도 로컬 검증 결과는 그대로 유지
                // 이미 위에서 Firebase와 SubscriptionManager에 업데이트했므로 추가 작업 불필요

                // 옵션: 서버 검증 재시도 로직
                scheduleRetryServerVerification(purchase, 3); // 3번 재시도
            }
        });
    }

    // 서버 검증 재시도 로직
    private void scheduleRetryServerVerification(Purchase purchase, int remainingRetries) {
        if (remainingRetries <= 0) {
            Log.w(TAG, "서버 검증 재시도 횟수 초과. 로컬 검증 결과 최종 유지");
            return;
        }

        // 5초 후 재시도
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "서버 검증 재시도 중... 남은 횟수: " + (remainingRetries - 1));

            SubscriptionValidator validator = new SubscriptionValidator(context);
            validator.verifySubscriptionWithServer(purchase, new SubscriptionValidator.OnSubscriptionValidatedListener() {
                @Override
                public void onValidationSuccess(SubscriptionValidator.SubscriptionInfo subscriptionInfo) {
                    Log.d(TAG, "🎉 재시도 서버 검증 성공!");
                    // 성공 시 최종 업데이트
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        updateFirebaseSubscription(user,
                                subscriptionInfo.isValid(),
                                subscriptionInfo.getExpiryTimeMillis(),
                                determineSubscriptionType(purchase),
                                subscriptionInfo.isAutoRenewing());
                    }
                }

                @Override
                public void onValidationFailed(String error) {
                    Log.w(TAG, "재시도 서버 검증도 실패: " + error);
                    scheduleRetryServerVerification(purchase, remainingRetries - 1);
                }
            });
        }, 5000);
    }

    /**
     * 🔧 새 메서드: 구매에서 구독 유형 결정
     */
    private String determineSubscriptionType(Purchase purchase) {
        List<String> skus = purchase.getProducts();
        if (skus.contains(MONTHLY_SUBSCRIPTION_ID)) {
            return Constants.SUBSCRIPTION_MONTHLY;
        } else if (skus.contains(YEARLY_SUBSCRIPTION_ID)) {
            return Constants.SUBSCRIPTION_YEARLY;
        }
        return Constants.SUBSCRIPTION_NONE;
    }

    /**
     * 🔧 수정: 기존 processValidPurchases 메서드 수정
     * 서버 검증을 추가하되 기존 로직은 유지
     */
    private void processValidPurchases(List<Purchase> purchases) {
        Log.d(TAG, "유효한 구매 처리 시작: " + (purchases != null ? purchases.size() : 0) + "개");

        boolean isSubscribed = false;
        long expiryTimestamp = 0;
        String subscriptionType = Constants.SUBSCRIPTION_NONE;
        boolean isAutoRenewing = false;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "사용자 로그인 상태 확인 필요");
            return;
        }

        if (purchases != null && !purchases.isEmpty()) {
            for (Purchase purchase : purchases) {
                Log.d(TAG, "🔍 구매 처리 시작: " + purchase.getOrderId());
                Log.d(TAG, "🔍 구매 상태 값: " + purchase.getPurchaseState());
                Log.d(TAG, "🔍 PURCHASED 상수 값: " + Purchase.PurchaseState.PURCHASED);
                Log.d(TAG, "🔍 구매 상태 비교 결과: " + (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED));

                // 구매 확인 처리
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    Log.d(TAG, "✅ PURCHASED 조건문 통과!");

                    if (!purchase.isAcknowledged()) {
                        Log.d(TAG, "🔍 구매 확인 필요 - acknowledgePurchase 호출");
                        acknowledgePurchase(purchase);
                    } else {
                        Log.d(TAG, "🔍 이미 확인된 구매");
                    }

                    // 🔍 이 라인들이 실행되는지 확인
                    Log.d(TAG, "🔍 서버 검증 호출 시작: " + purchase.getOrderId());
                    handlePurchaseVerification(purchase);

                    // 기존 로컬 검증 로직도 유지 (fallback용)
                    List<String> skus = purchase.getProducts();
                    boolean purchaseAutoRenewing = purchase.isAutoRenewing();

                    if (skus.contains(MONTHLY_SUBSCRIPTION_ID)) {
                        subscriptionType = Constants.SUBSCRIPTION_MONTHLY;
                        isAutoRenewing = purchaseAutoRenewing;

                        if (purchaseAutoRenewing) {
                            expiryTimestamp = calculateActualExpiryTime(purchase, 30);
                            isSubscribed = true;
                            Log.d(TAG, "월간 구독 활성 (자동 갱신): 만료일 " + new Date(expiryTimestamp));
                        } else {
                            expiryTimestamp = calculateGracePeriodExpiryTime(purchase, 30);
                            isSubscribed = isWithinGracePeriod(expiryTimestamp);
                            Log.d(TAG, "월간 구독 취소됨 (유예 기간): 만료일 " + new Date(expiryTimestamp) + ", 현재 유효: " + isSubscribed);
                        }

                    } else if (skus.contains(YEARLY_SUBSCRIPTION_ID)) {
                        subscriptionType = Constants.SUBSCRIPTION_YEARLY;
                        isAutoRenewing = purchaseAutoRenewing;

                        if (purchaseAutoRenewing) {
                            expiryTimestamp = calculateActualExpiryTime(purchase, 365);
                            isSubscribed = true;
                            Log.d(TAG, "연간 구독 활성 (자동 갱신): 만료일 " + new Date(expiryTimestamp));
                        } else {
                            expiryTimestamp = calculateGracePeriodExpiryTime(purchase, 365);
                            isSubscribed = isWithinGracePeriod(expiryTimestamp);
                            Log.d(TAG, "연간 구독 취소됨 (유예 기간): 만료일 " + new Date(expiryTimestamp) + ", 현재 유효: " + isSubscribed);
                        }
                    }
                } else {
                    Log.w(TAG, "❌ PURCHASED 조건문 통과 못함 - 구매 상태: " + purchase.getPurchaseState());
                }
            }
        }


        // 로컬 검증 결과로 임시 업데이트 (서버 검증이 완료되면 덮어써짐)
        if (!isSubscribed && expiryTimestamp > 0) {
            Log.d(TAG, "구독이 만료되었습니다. 만료일: " + new Date(expiryTimestamp));
            subscriptionType = Constants.SUBSCRIPTION_NONE;
            expiryTimestamp = 0;
        }

        // Firebase 구독 상태 임시 업데이트 (서버 검증 결과로 최종 업데이트됨)
        updateFirebaseSubscription(user, isSubscribed, expiryTimestamp, subscriptionType, isAutoRenewing);

        // SubscriptionManager 임시 업데이트
        SubscriptionManager subscriptionManager = SubscriptionManager.getInstance(context);
        subscriptionManager.setSubscribed(isSubscribed, expiryTimestamp, subscriptionType);

        Log.d(TAG, "로컬 검증 완료. 서버 검증 대기 중...");
    }

    /**
     * 🔧 새 메서드: 실제 구독 만료 시간 계산 (자동 갱신 활성화)
     */
    private long calculateActualExpiryTime(Purchase purchase, int days) {
        // 구매 시간으로부터 구독 기간만큼 더한 시간
        // 실제로는 Google Play Developer API를 통해 정확한 만료 시간을 가져오는 것이 좋습니다
        return purchase.getPurchaseTime() + (days * 24L * 60 * 60 * 1000);
    }

    /**
     * 🔧 새 메서드: 유예 기간 만료 시간 계산 (자동 갱신 비활성화)
     */
    private long calculateGracePeriodExpiryTime(Purchase purchase, int days) {
        // 취소된 구독의 경우, 구매 시간 + 구독 기간까지만 유효
        return purchase.getPurchaseTime() + (days * 24L * 60 * 60 * 1000);
    }

    /**
     * 🔧 새 메서드: 유예 기간 내인지 확인
     */
    private boolean isWithinGracePeriod(long expiryTimestamp) {
        return System.currentTimeMillis() < expiryTimestamp;
    }

    /**
     * 🔧 새 메서드: Firebase 구독 상태 업데이트
     */
    private void updateFirebaseSubscription(FirebaseUser user, boolean isSubscribed,
                                            long expiryTimestamp, String subscriptionType,
                                            boolean isAutoRenewing) {
        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions")
                .child(user.getUid());

        Map<String, Object> subscriptionData = new HashMap<>();
        subscriptionData.put("subscribed", isSubscribed);
        subscriptionData.put("expiryTimestamp", expiryTimestamp);
        subscriptionData.put("subscriptionType", subscriptionType);
        subscriptionData.put("autoRenewing", isAutoRenewing);
        subscriptionData.put("cancelled", !isAutoRenewing && isSubscribed); // 취소되었지만 아직 유효
        subscriptionData.put("lastUpdated", System.currentTimeMillis());
        subscriptionData.put("lastVerifiedLocally", System.currentTimeMillis());

        subscriptionRef.updateChildren(subscriptionData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Firebase 구독 상태 업데이트 성공: " +
                            "구독=" + isSubscribed +
                            ", 자동갱신=" + isAutoRenewing +
                            ", 취소여부=" + (!isAutoRenewing && isSubscribed));

                    // UI 업데이트 알림
                    if (billingStatusListener != null) {
                        billingStatusListener.onSubscriptionStatusChanged(isSubscribed, isAutoRenewing);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase 구독 상태 업데이트 실패: " + e.getMessage());
                    if (billingStatusListener != null) {
                        billingStatusListener.onBillingError("구독 상태 동기화 실패: " + e.getMessage());
                    }
                });
    }

    /**
     * 다른 기기에서의 구독 확인
     */
    private void checkCrossDeviceSubscription(FirebaseUser user) {
        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions")
                .child(user.getUid());

        subscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isSubscribed = snapshot.child("subscribed").getValue(Boolean.class);
                    Long expiryTimestamp = snapshot.child("expiryTimestamp").getValue(Long.class);
                    String subscriptionType = snapshot.child("subscriptionType").getValue(String.class);
                    Boolean autoRenewing = snapshot.child("autoRenewing").getValue(Boolean.class);

                    if (isSubscribed != null && isSubscribed && expiryTimestamp != null) {
                        // 🔧 수정: 만료 시간 확인 후 구독 상태 결정
                        boolean stillValid = System.currentTimeMillis() < expiryTimestamp;

                        if (stillValid) {
                            Log.d(TAG, "다른 기기에서 활성 구독 발견: " + subscriptionType);
                            SubscriptionManager subscriptionManager = SubscriptionManager.getInstance(context);
                            subscriptionManager.setSubscribed(true, expiryTimestamp,
                                    subscriptionType != null ? subscriptionType : Constants.SUBSCRIPTION_NONE);
                        } else {
                            Log.d(TAG, "다른 기기의 구독이 만료됨: " + new Date(expiryTimestamp));
                            // 만료된 구독 정보 삭제
                            updateFirebaseSubscription(user, false, 0, Constants.SUBSCRIPTION_NONE, false);
                        }
                    } else {
                        Log.d(TAG, "다른 기기에서도 활성 구독 없음");
                    }
                } else {
                    Log.d(TAG, "Firebase에 구독 정보 없음");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "다른 기기 구독 확인 실패: " + error.getMessage());
            }
        });
    }

    /**
     * 구매 확인 처리
     */
    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();

        billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
            @Override
            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "구매 확인 완료: " + purchase.getOrderId());
                } else {
                    Log.e(TAG, "구매 확인 실패: " + billingResult.getResponseCode());
                }
            }
        });
    }

    /**
     * 구매 소유자 정보 저장
     */
    private void savePurchaseOwner(Purchase purchase) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            SharedPreferences prefs = context.getSharedPreferences("billing_purchase_info", Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(purchase.getOrderId(), currentUser.getEmail())
                    .apply();
            Log.d(TAG, "구매 소유자 저장: " + purchase.getOrderId() + " -> " + currentUser.getEmail());
        }
    }

    /**
     * 구독 시작
     */
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
                billingStatusListener.onBillingError("상품 정보를 찾을 수 없습니다. 다시 시도해주세요.");
            }
            return;
        }

        List<ProductDetails.SubscriptionOfferDetails> offerDetailsList =
                productDetails.getSubscriptionOfferDetails();

        if (offerDetailsList == null || offerDetailsList.isEmpty()) {
            Log.e(TAG, "구독 옵션이 없습니다: " + subscriptionId);
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("구독 옵션이 없습니다. 다시 시도해주세요.");
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
                billingStatusListener.onBillingError("결제를 시작할 수 없습니다. 오류 코드: " + billingResult.getResponseCode());
            }
        }
    }

    /**
     * 구매 업데이트 리스너
     */
    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
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

    /**
     * 리스너 인터페이스
     */
    public interface BillingStatusListener {
        void onPurchaseComplete();
        void onBillingError(String error);
        void onProductDetailsReceived(List<ProductDetails> productDetailsList);

        // 🔧 새로 추가된 메서드들 (기본 구현 제공)
        default void onSubscriptionStatusChanged(boolean isSubscribed, boolean isAutoRenewing) {
            // 기본 구현: onPurchaseComplete() 호출
            onPurchaseComplete();
        }

        default void onSubscriptionCancelled(long remainingDays) {
            // 기본 구현: 빈 메서드
        }

        default void onSubscriptionExpired() {
            // 기본 구현: 빈 메서드
        }
    }
}