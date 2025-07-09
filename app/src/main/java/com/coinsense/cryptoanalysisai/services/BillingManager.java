package com.coinsense.cryptoanalysisai.services;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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

    // 구독 상품 ID
    public static final String MONTHLY_SUBSCRIPTION_ID = "com.coinsense.cryptoanalysisai.monthly";
    public static final String YEARLY_SUBSCRIPTION_ID = "com.coinsense.cryptoanalysisai.yearly";

    private static BillingManager instance;
    private final Context context;
    private BillingClient billingClient;
    private List<ProductDetails> productDetailsList = new ArrayList<>();
    private BillingStatusListener billingStatusListener;

    // 🔧 Google Play Store 계정 정보 저장
    private String currentPlayStoreAccount = null;

    private BillingManager(Context context) {
        this.context = context.getApplicationContext();
        setupBillingClient();

        // 🔧 현재 Play Store 계정 정보 확인
        detectPlayStoreAccount();
    }

    /**
     * 🔧 Google Play Store 계정 감지
     */
    private void detectPlayStoreAccount() {
        try {
            AccountManager accountManager = AccountManager.get(context);
            Account[] accounts = accountManager.getAccountsByType("com.google");

            if (accounts.length > 0) {
                // 첫 번째 Google 계정을 Play Store 계정으로 간주
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

        // 결제 서비스에 연결
        connectToPlayBillingService();
    }

    /**
     * Google Play 결제 서비스에 연결
     */
    public void connectToPlayBillingService() {
        if (!billingClient.isReady()) {
            billingClient.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingServiceDisconnected() {
                    // 연결이 끊어졌을 때 재연결 시도
                    Log.d(TAG, "결제 서비스 연결 끊김. 재연결 시도중...");
                }

                @Override
                public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "결제 서비스 연결 성공");
                        // 구독 상품 정보 로드
                        querySubscriptionProducts();
                        // 구매 내역 확인
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

        // 월간 구독 상품
        productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(MONTHLY_SUBSCRIPTION_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
        );

        // 연간 구독 상품
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

        // 구독 상품 구매 내역 조회
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
     * 기존 processPurchases 메서드 - 최소 수정 버전
     * 핵심: 구매 소유자 확인 로직 개선 + 크로스 디바이스 구독 확인
     */
    private void processPurchases(List<Purchase> purchases) {
        // 현재 로그인한 Firebase 사용자 확인
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "Firebase 사용자가 로그인되어 있지 않습니다");
            return;
        }

        Log.d(TAG, "현재 Firebase 사용자 ID: " + user.getUid());
        Log.d(TAG, "현재 Firebase 사용자 Email: " + user.getEmail());
        Log.d(TAG, "Google Play에서 확인된 구매 내역 수: " + (purchases != null ? purchases.size() : 0));

        // 🔧 수정: 구매 소유자 확인 및 필터링 - 로직 개선
        SharedPreferences prefs = context.getSharedPreferences("billing_purchase_info", Context.MODE_PRIVATE);
        List<Purchase> validPurchases = new ArrayList<>();

        if (purchases != null && !purchases.isEmpty()) {
            for (Purchase purchase : purchases) {
                String purchaseOwner = prefs.getString(purchase.getOrderId(), null);

                // 🔧 수정: 첫 실행 또는 마이그레이션 - 구매 소유자 정보가 없으면 현재 사용자로 저장
                if (purchaseOwner == null) {
                    // 🔧 개선: Firebase 계정으로 소유권 설정 (기존과 동일)
                    purchaseOwner = user.getEmail();
                    prefs.edit().putString(purchase.getOrderId(), purchaseOwner).apply();
                    Log.d(TAG, "구매 소유자 정보 저장: " + purchase.getOrderId() + " -> " + purchaseOwner);
                }

                // 🔧 수정: 구매 소유자와 현재 사용자 일치 여부 확인
                if (purchaseOwner.equals(user.getEmail())) {
                    validPurchases.add(purchase);
                    Log.d(TAG, "유효한 구매 발견: " + purchase.getOrderId() + " 소유자: " + purchaseOwner);
                } else {
                    Log.w(TAG, "다른 계정의 구매 무시: " + purchase.getOrderId() +
                            " 현재 사용자: " + user.getEmail() +
                            " 구매 소유자: " + purchaseOwner);
                }
            }

            // 🔧 추가: 유효한 구매가 없으면 다른 기기에서의 구독 확인
            if (validPurchases.isEmpty()) {
                Log.d(TAG, "현재 기기에서 유효한 구매 없음 - 다른 기기 구독 확인");
                checkCrossDeviceSubscription(user);
                return;
            }

            // 🔧 수정: 유효한 구매만 처리 (기존 로직과 동일)
            purchases = validPurchases;
        } else {
            // 🔧 추가: 구매 내역이 없으면 다른 기기에서의 구독 확인
            Log.d(TAG, "구매 내역 없음 - 다른 기기 구독 확인");
            checkCrossDeviceSubscription(user);
            return;
        }

        // ========== 기존 로직 그대로 유지 ==========
        SubscriptionManager subscriptionManager = SubscriptionManager.getInstance(context);
        boolean isSubscribed = false;
        long expiryTimestamp = 0;
        String subscriptionType = Constants.SUBSCRIPTION_NONE;
        boolean isAutoRenewing = false;

        // Google Play에서 받은 구매 정보 처리 (기존과 동일)
        if (purchases != null && !purchases.isEmpty()) {
            for (Purchase purchase : purchases) {
                Log.d(TAG, "구매 상태 검토: " + purchase.getProducts() + ", 상태: " + purchase.getPurchaseState());

                // 구매 상태 확인 (PURCHASED = 완료된 구매)
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    // 구매 확인(Acknowledge) 상태 확인 - 미확인 시 확인 처리
                    if (!purchase.isAcknowledged()) {
                        acknowledgePurchase(purchase);
                    }

                    // 구독 상품 ID 확인
                    List<String> skus = purchase.getProducts();
                    if (skus.contains(MONTHLY_SUBSCRIPTION_ID)) {
                        isSubscribed = true;
                        subscriptionType = Constants.SUBSCRIPTION_MONTHLY;
                        isAutoRenewing = purchase.isAutoRenewing();

                        // 만료 시간 설정 (현재 시간으로부터 30일 후)
                        expiryTimestamp = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000);
                        Log.d(TAG, "월간 구독 확인됨: 만료일 " + new Date(expiryTimestamp));

                    } else if (skus.contains(YEARLY_SUBSCRIPTION_ID)) {
                        isSubscribed = true;
                        subscriptionType = Constants.SUBSCRIPTION_YEARLY;
                        isAutoRenewing = purchase.isAutoRenewing();

                        // 만료 시간 설정 (현재 시간으로부터 365일 후)
                        expiryTimestamp = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);
                        Log.d(TAG, "연간 구독 확인됨: 만료일 " + new Date(expiryTimestamp));
                    }
                }
            }
        } else {
            Log.d(TAG, "활성 구독이 확인되지 않았습니다");
        }

        // ========== 기존 Firebase 업데이트 로직 그대로 유지 ==========
        // Firebase 구독 데이터베이스 참조
        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions")
                .child(user.getUid());

        // 람다에서 사용할 변수들의 최종 값을 캡처
        final boolean finalIsSubscribed = isSubscribed;
        final String finalSubscriptionType = subscriptionType;
        final List<Purchase> finalPurchases = purchases;

        // ✅ 수정: 기존 구독 정보를 먼저 조회하여 만료일 유지
        subscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                FirebaseSubscriptionManager.SubscriptionData existingData = null;
                long finalExpiryTimestamp = 0;
                boolean finalIsAutoRenewing = false;

                // 기존 구독 데이터 가져오기
                if (snapshot.exists()) {
                    try {
                        existingData = snapshot.getValue(FirebaseSubscriptionManager.SubscriptionData.class);
                    } catch (Exception e) {
                        Log.e(TAG, "구독 데이터 파싱 오류: " + e.getMessage());
                    }
                }

                // Google Play에서 받은 구매 정보 처리
                if (finalPurchases != null && !finalPurchases.isEmpty()) {
                    for (Purchase purchase : finalPurchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                            if (!purchase.isAcknowledged()) {
                                acknowledgePurchase(purchase);
                            }

                            List<String> skus = purchase.getProducts();
                            finalIsAutoRenewing = purchase.isAutoRenewing();

                            if (skus.contains(MONTHLY_SUBSCRIPTION_ID)) {
                                // ✅ 수정: 기존 만료일이 유효하면 그것을 사용, 없으면 새로 설정
                                if (existingData != null &&
                                        existingData.isSubscribed() &&
                                        existingData.getExpiryTimestamp() > System.currentTimeMillis()) {

                                    // 기존 만료일 유지
                                    finalExpiryTimestamp = existingData.getExpiryTimestamp();
                                    Log.d(TAG, "기존 월간 구독 만료일 유지: " + new Date(finalExpiryTimestamp));
                                } else {
                                    // 신규 구독 또는 만료된 구독 - 새로 설정
                                    finalExpiryTimestamp = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000);
                                    Log.d(TAG, "새 월간 구독 만료일 설정: " + new Date(finalExpiryTimestamp));
                                }

                            } else if (skus.contains(YEARLY_SUBSCRIPTION_ID)) {
                                // ✅ 수정: 기존 만료일이 유효하면 그것을 사용, 없으면 새로 설정
                                if (existingData != null &&
                                        existingData.isSubscribed() &&
                                        existingData.getExpiryTimestamp() > System.currentTimeMillis()) {

                                    // 기존 만료일 유지
                                    finalExpiryTimestamp = existingData.getExpiryTimestamp();
                                    Log.d(TAG, "기존 연간 구독 만료일 유지: " + new Date(finalExpiryTimestamp));
                                } else {
                                    // 신규 구독 또는 만료된 구독 - 새로 설정
                                    finalExpiryTimestamp = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);
                                    Log.d(TAG, "새 연간 구독 만료일 설정: " + new Date(finalExpiryTimestamp));
                                }
                            }
                        }
                    }
                }

                // 업데이트할 데이터 구성
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("subscriptionType", finalSubscriptionType);
                updateData.put("expiryTimestamp", finalExpiryTimestamp);  // ✅ 수정된 로직으로 설정된 만료일
                updateData.put("autoRenewing", finalIsAutoRenewing);
                updateData.put("subscribed", finalIsSubscribed);
                updateData.put("isCancelled", false);
                updateData.put("lastUpdated", System.currentTimeMillis());

                // 시작 시간 설정 (기존 로직 유지)
                if (existingData != null) {
                    if (!finalIsSubscribed && existingData.isSubscribed()) {
                        updateData.put("startTimestamp", existingData.getStartTimestamp());
                    } else if (finalIsSubscribed && !existingData.isSubscribed()) {
                        updateData.put("startTimestamp", System.currentTimeMillis());
                    } else {
                        updateData.put("startTimestamp", existingData.getStartTimestamp());
                    }
                } else {
                    updateData.put("startTimestamp", System.currentTimeMillis());
                }

                // Firebase에 구독 정보 업데이트
                subscriptionRef.updateChildren(updateData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Firebase 구독 정보 업데이트 성공: " + user.getUid());
                            if (billingStatusListener != null) {
                                billingStatusListener.onSubscriptionStatusUpdated(finalIsSubscribed, finalSubscriptionType);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Firebase 구독 정보 업데이트 실패: " + e.getMessage());
                        });

                // 구매 기록 저장
                try {
                    saveFirebasePurchaseRecord(finalPurchases);
                } catch (Exception e) {
                    Log.e(TAG, "구독 정보 처리 중 오류: " + e.getMessage(), e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Firebase 구독 정보 조회 실패: " + error.getMessage());
            }
        });
    }

    /**
     * 🔧 Play Store 계정과 Firebase 계정 연결 정보 저장
     */
    private void linkPlayStoreWithFirebase(String firebaseEmail, String playStoreAccount, Purchase purchase) {
        if (playStoreAccount == null || firebaseEmail == null) return;

        // Firebase에 연결 정보 저장
        DatabaseReference linkRef = FirebaseDatabase.getInstance()
                .getReference("account_links")
                .child(sanitizeEmail(firebaseEmail));

        Map<String, Object> linkData = new HashMap<>();
        linkData.put("playStoreAccount", playStoreAccount);
        linkData.put("firebaseAccount", firebaseEmail);
        linkData.put("lastPurchaseOrderId", purchase.getOrderId());
        linkData.put("linkTimestamp", System.currentTimeMillis());

        linkRef.setValue(linkData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "계정 연결 정보 저장 완료"))
                .addOnFailureListener(e -> Log.e(TAG, "계정 연결 정보 저장 실패: " + e.getMessage()));
    }

    /**
     * 🔧 새로 추가: 다른 기기에서의 구독 확인
     */
    private void checkCrossDeviceSubscription(FirebaseUser user) {
        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions")
                .child(user.getUid());

        subscriptionRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    try {
                        FirebaseSubscriptionManager.SubscriptionData data =
                                snapshot.getValue(FirebaseSubscriptionManager.SubscriptionData.class);

                        if (data != null && data.isSubscribed()) {
                            long currentTime = System.currentTimeMillis();

                            // 구독이 만료되지 않았다면 유효한 구독으로 처리
                            if (data.getExpiryTimestamp() > currentTime) {
                                Log.d(TAG, "다른 기기에서 활성 구독 발견 - Firebase에서 구독 상태 유지");

                                if (billingStatusListener != null) {
                                    billingStatusListener.onSubscriptionStatusUpdated(
                                            true, data.getSubscriptionType());
                                }
                                return;
                            } else {
                                Log.d(TAG, "다른 기기 구독이 만료됨");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "구독 데이터 파싱 오류: " + e.getMessage());
                    }
                }

                // 활성 구독이 없는 경우 기본 상태로 설정
                setupDefaultSubscriptionData(user);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "구독 정보 조회 실패: " + error.getMessage());
                setupDefaultSubscriptionData(user);
            }
        });
    }

    /**
     * 🔧 Firebase 구독 정보 업데이트
     */
    private void updateFirebaseSubscription(FirebaseUser user, boolean isSubscribed,
                                            String subscriptionType, long expiryTimestamp,
                                            boolean isAutoRenewing) {
        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions")
                .child(user.getUid());

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("subscriptionType", subscriptionType);
        updateData.put("expiryTimestamp", expiryTimestamp);
        updateData.put("autoRenewing", isAutoRenewing);
        updateData.put("subscribed", isSubscribed);
        updateData.put("isCancelled", false);
        updateData.put("lastUpdated", System.currentTimeMillis());
        updateData.put("playStoreAccount", currentPlayStoreAccount); // 🔧 Play Store 계정 정보 추가

        // 🔧 기존 시작 시간 유지 또는 새로 설정
        subscriptionRef.child("startTimestamp").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || (Long) snapshot.getValue() == 0) {
                    updateData.put("startTimestamp", System.currentTimeMillis());
                }

                subscriptionRef.updateChildren(updateData)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Firebase 구독 정보 업데이트 성공");
                            if (billingStatusListener != null) {
                                billingStatusListener.onSubscriptionStatusUpdated(isSubscribed, subscriptionType);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Firebase 구독 정보 업데이트 실패: " + e.getMessage());
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "시작 시간 조회 실패: " + error.getMessage());
            }
        });
    }

    /**
     * 🔧 이메일을 Firebase 키로 사용 가능하게 변환
     */
    private String sanitizeEmail(String email) {
        if (email == null) return "unknown";
        return email.replace(".", "_")
                .replace("#", "_")
                .replace("$", "_")
                .replace("[", "_")
                .replace("]", "_");
    }



    /**
     * 🔧 계정 전환 시 구독 없음 상태로 설정
     */
    private void setupDefaultSubscriptionData(FirebaseUser user) {
        if (user == null) return;

        DatabaseReference subscriptionRef = FirebaseDatabase.getInstance()
                .getReference("subscriptions")
                .child(user.getUid());

        Map<String, Object> defaultData = new HashMap<>();
        defaultData.put("subscriptionType", Constants.SUBSCRIPTION_NONE);
        defaultData.put("expiryTimestamp", 0);
        defaultData.put("startTimestamp", System.currentTimeMillis());
        defaultData.put("autoRenewing", false);
        defaultData.put("subscribed", false);
        defaultData.put("isCancelled", false);
        defaultData.put("lastUpdated", System.currentTimeMillis());
        defaultData.put("playStoreAccount", currentPlayStoreAccount);

        subscriptionRef.updateChildren(defaultData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "기본 구독 정보 설정 완료");
                    if (billingStatusListener != null) {
                        billingStatusListener.onSubscriptionStatusUpdated(false, Constants.SUBSCRIPTION_NONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "기본 구독 정보 설정 실패: " + e.getMessage());
                });
    }

    /**
     * Firebase에 구매 기록 저장 (추적용)
     */
    private void saveFirebasePurchaseRecord(List<Purchase> purchases) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || purchases == null || purchases.isEmpty()) {
            return;
        }

        // Firebase 데이터베이스 참조
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference purchaseRef = database.getReference("purchase_history")
                .child(user.getUid());

        for (Purchase purchase : purchases) {
            // 구매 정보를 맵으로 변환
            Map<String, Object> purchaseData = new HashMap<>();
            purchaseData.put("orderId", purchase.getOrderId());
            purchaseData.put("products", purchase.getProducts());
            purchaseData.put("purchaseTime", purchase.getPurchaseTime());
            purchaseData.put("purchaseState", purchase.getPurchaseState());
            purchaseData.put("autoRenewing", purchase.isAutoRenewing());
            purchaseData.put("purchaseToken", purchase.getPurchaseToken());
            purchaseData.put("recordTime", System.currentTimeMillis());

            // 주문 ID에서 Firebase 경로에 사용할 수 없는 문자 치환
            String safeOrderId = purchase.getOrderId()
                    .replace(".", "_")
                    .replace("#", "_")
                    .replace("$", "_")
                    .replace("[", "_")
                    .replace("]", "_");

            // 구매 ID로 저장 (중복 방지)
            purchaseRef.child(safeOrderId).setValue(purchaseData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "구매 기록 저장 성공"))
                    .addOnFailureListener(e -> Log.e(TAG, "구매 기록 저장 실패: " + e.getMessage()));
        }
    }


    /**
     * 구매 인정 (Acknowledge) - Google Play 요구사항
     */
    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();

        billingClient.acknowledgePurchase(
                acknowledgePurchaseParams,
                billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "구매 인정 완료: " + purchase.getOrderId());
                    } else {
                        Log.e(TAG, "구매 인정 실패: " + billingResult.getResponseCode() +
                                " - " + billingResult.getDebugMessage());
                    }
                });
    }

    // 구매한 계정 이메일 확인
    private String getAccountNameFromSharedPreferences() {
        SharedPreferences prefs = context.getSharedPreferences("billing_prefs", Context.MODE_PRIVATE);
        return prefs.getString("account_name", null);
    }

    // 구매 계정 이메일 저장
    private void saveAccountName(String accountName) {
        SharedPreferences prefs = context.getSharedPreferences("billing_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("account_name", accountName).apply();
    }

    /**
     * 구독 구매 시작
     */
    public void startSubscription(Activity activity, String subscriptionId) {
        if (!billingClient.isReady()) {
            Log.e(TAG, "BillingClient가 준비되지 않았습니다. 먼저 연결하세요.");
            connectToPlayBillingService();
            return;
        }

        // 해당 ID의 상품 정보 찾기
        ProductDetails productDetails = null;
        for (ProductDetails details : productDetailsList) {
            if (details.getProductId().equals(subscriptionId)) {
                productDetails = details;
                break;
            }
        }

        if (productDetails == null) {
            Log.e(TAG, "구매할 상품 정보를 찾을 수 없습니다: " + subscriptionId);
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("상품 정보를 찾을 수 없습니다. 다시 시도해주세요.");
            }
            return;
        }

        // 구독 옵션 선택
        List<ProductDetails.SubscriptionOfferDetails> offerDetailsList =
                productDetails.getSubscriptionOfferDetails();

        if (offerDetailsList == null || offerDetailsList.isEmpty()) {
            Log.e(TAG, "구독 옵션이 없습니다: " + subscriptionId);
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("구독 옵션이 없습니다. 다시 시도해주세요.");
            }
            return;
        }

        // 첫 번째 구독 옵션 사용
        String offerToken = offerDetailsList.get(0).getOfferToken();

        // 구매 플로우 파라미터 생성
        BillingFlowParams.ProductDetailsParams productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build();

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(List.of(productDetailsParams))
                .build();

        // 구매 플로우 시작
        BillingResult billingResult = billingClient.launchBillingFlow(activity, billingFlowParams);

        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "구매 플로우 시작 실패: " + billingResult.getResponseCode());
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("결제를 시작할 수 없습니다. 오류 코드: " + billingResult.getResponseCode());
            }
        }
    }

    /**
     * 구매 업데이트 리스너 구현
     */
    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {

            // 구매 완료 - 각 구매의 소유자 저장
            for (Purchase purchase : purchases) {
                savePurchaseOwner(purchase);  // <-- 여기서 호출
            }

            // 구매 완료
            processPurchases(purchases);
            if (billingStatusListener != null) {
                billingStatusListener.onPurchaseComplete();
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            // 사용자가 취소
            Log.d(TAG, "사용자가 구매를 취소했습니다.");
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("결제가 취소되었습니다.");
            }
        } else {
            // 기타 오류
            Log.e(TAG, "구매 업데이트 오류: " + billingResult.getResponseCode());
            if (billingStatusListener != null) {
                billingStatusListener.onBillingError("결제 중 오류가 발생했습니다. 오류 코드: " + billingResult.getResponseCode());
            }
        }
    }

    /**
     * 연결 해제
     */
    public void endConnection() {
        if (billingClient != null) {
            billingClient.endConnection();
        }
    }

    /**
     * 결제 상태 리스너 인터페이스
     */
    public interface BillingStatusListener {
        void onProductDetailsReceived(List<ProductDetails> productDetailsList);
        void onPurchaseComplete();
        void onSubscriptionStatusUpdated(boolean isSubscribed, String subscriptionType);
        void onBillingError(String errorMessage);
    }

    /**
     * 앱 시작 시 구독 상태 동기화
     */
    public void syncSubscriptions() {
        if (!billingClient.isReady()) {
            connectToPlayBillingService();
            return;
        }
        queryPurchases();
    }

    // 구매 완료 시 호출되어 구매자와 Firebase 사용자 연결
    private void savePurchaseOwner(Purchase purchase) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || purchase == null) return;

        SharedPreferences prefs = context.getSharedPreferences("billing_purchase_info", Context.MODE_PRIVATE);
        prefs.edit().putString(purchase.getOrderId(), user.getEmail()).apply();

        Log.d(TAG, "구매 소유자 저장: " + purchase.getOrderId() + " -> " + user.getEmail());
    }
}