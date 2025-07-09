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
     * 🔧 수정된 processPurchases 메서드 - 구독 취소 버그 수정
     */
    private void processPurchases(List<Purchase> purchases) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.e(TAG, "Firebase 사용자가 로그인되어 있지 않습니다");
            return;
        }

        Log.d(TAG, "현재 Firebase 사용자 ID: " + user.getUid());
        Log.d(TAG, "Google Play에서 확인된 구매 내역 수: " + (purchases != null ? purchases.size() : 0));

        // 구매 소유자 확인 및 필터링
        SharedPreferences prefs = context.getSharedPreferences("billing_purchase_info", Context.MODE_PRIVATE);
        List<Purchase> validPurchases = new ArrayList<>();

        if (purchases != null && !purchases.isEmpty()) {
            for (Purchase purchase : purchases) {
                String purchaseOwner = prefs.getString(purchase.getOrderId(), null);

                if (purchaseOwner == null) {
                    purchaseOwner = user.getEmail();
                    prefs.edit().putString(purchase.getOrderId(), purchaseOwner).apply();
                    Log.d(TAG, "구매 소유자 정보 저장: " + purchase.getOrderId() + " -> " + purchaseOwner);
                }

                if (purchaseOwner.equals(user.getEmail())) {
                    validPurchases.add(purchase);
                    Log.d(TAG, "유효한 구매 발견: " + purchase.getOrderId() + " 소유자: " + purchaseOwner);
                } else {
                    Log.w(TAG, "다른 계정의 구매 무시: " + purchase.getOrderId() +
                            " 현재 사용자: " + user.getEmail() +
                            " 구매 소유자: " + purchaseOwner);
                }
            }

            if (validPurchases.isEmpty()) {
                Log.d(TAG, "현재 기기에서 유효한 구매 없음 - 다른 기기 구독 확인");
                checkCrossDeviceSubscription(user);
                return;
            }

            purchases = validPurchases;
        } else {
            Log.d(TAG, "구매 내역 없음 - 다른 기기 구독 확인");
            checkCrossDeviceSubscription(user);
            return;
        }

        // 🔧 수정: 구독 상태 분석 로직 개선
        SubscriptionManager subscriptionManager = SubscriptionManager.getInstance(context);
        boolean isSubscribed = false;
        long expiryTimestamp = 0;
        String subscriptionType = Constants.SUBSCRIPTION_NONE;
        boolean isAutoRenewing = false;

        for (Purchase purchase : purchases) {
            Log.d(TAG, "구매 상태 검토: " + purchase.getProducts() + ", 상태: " + purchase.getPurchaseState());
            Log.d(TAG, "자동 갱신 상태: " + purchase.isAutoRenewing());
            Log.d(TAG, "구매 시간: " + new Date(purchase.getPurchaseTime()));

            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged()) {
                    acknowledgePurchase(purchase);
                }

                List<String> skus = purchase.getProducts();

                // 🔧 수정: 자동 갱신 상태 확인
                boolean purchaseAutoRenewing = purchase.isAutoRenewing();

                if (skus.contains(MONTHLY_SUBSCRIPTION_ID)) {
                    subscriptionType = Constants.SUBSCRIPTION_MONTHLY;
                    isAutoRenewing = purchaseAutoRenewing;

                    // 🔧 수정: 실제 구독 상태에 따른 만료 시간 계산
                    if (purchaseAutoRenewing) {
                        // 자동 갱신 활성화 - 정상적인 구독
                        expiryTimestamp = calculateActualExpiryTime(purchase, 30);
                        isSubscribed = true;
                        Log.d(TAG, "월간 구독 활성 (자동 갱신): 만료일 " + new Date(expiryTimestamp));
                    } else {
                        // 자동 갱신 비활성화 - 취소된 구독, 현재 기간까지만 유효
                        expiryTimestamp = calculateGracePeriodExpiryTime(purchase, 30);
                        isSubscribed = isWithinGracePeriod(expiryTimestamp);
                        Log.d(TAG, "월간 구독 취소됨 (유예 기간): 만료일 " + new Date(expiryTimestamp) + ", 현재 유효: " + isSubscribed);
                    }

                } else if (skus.contains(YEARLY_SUBSCRIPTION_ID)) {
                    subscriptionType = Constants.SUBSCRIPTION_YEARLY;
                    isAutoRenewing = purchaseAutoRenewing;

                    if (purchaseAutoRenewing) {
                        // 자동 갱신 활성화 - 정상적인 구독
                        expiryTimestamp = calculateActualExpiryTime(purchase, 365);
                        isSubscribed = true;
                        Log.d(TAG, "연간 구독 활성 (자동 갱신): 만료일 " + new Date(expiryTimestamp));
                    } else {
                        // 자동 갱신 비활성화 - 취소된 구독, 현재 기간까지만 유효
                        expiryTimestamp = calculateGracePeriodExpiryTime(purchase, 365);
                        isSubscribed = isWithinGracePeriod(expiryTimestamp);
                        Log.d(TAG, "연간 구독 취소됨 (유예 기간): 만료일 " + new Date(expiryTimestamp) + ", 현재 유효: " + isSubscribed);
                    }
                }
            }
        }

        // 🔧 추가: 만료된 구독 처리
        if (!isSubscribed && expiryTimestamp > 0) {
            Log.d(TAG, "구독이 만료되었습니다. 만료일: " + new Date(expiryTimestamp));
            subscriptionType = Constants.SUBSCRIPTION_NONE;
            expiryTimestamp = 0;
        }

        // Firebase 구독 상태 업데이트
        updateFirebaseSubscription(user, isSubscribed, expiryTimestamp, subscriptionType, isAutoRenewing);

        // SubscriptionManager 업데이트
        subscriptionManager.setSubscribed(isSubscribed, expiryTimestamp, subscriptionType);
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
        subscriptionData.put("lastUpdated", System.currentTimeMillis());

        subscriptionRef.updateChildren(subscriptionData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Firebase 구독 상태 업데이트 성공: " + isSubscribed +
                            ", 자동갱신: " + isAutoRenewing);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase 구독 상태 업데이트 실패: " + e.getMessage());
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
    }
}