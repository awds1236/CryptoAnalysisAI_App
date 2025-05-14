package com.coinsense.cryptoanalysisai.services;

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

import java.util.ArrayList;
import java.util.List;

public class BillingManager implements PurchasesUpdatedListener {
    private static final String TAG = "BillingManager";

    // 구독 상품 ID
    public static final String MONTHLY_SUBSCRIPTION_ID = "com.example.cryptoanalysisai.monthly";
    public static final String YEARLY_SUBSCRIPTION_ID = "com.example.cryptoanalysisai.yearly";

    private static BillingManager instance;
    private final Context context;
    private BillingClient billingClient;
    private List<ProductDetails> productDetailsList = new ArrayList<>();

    // 결제 상태 콜백 리스너
    private BillingStatusListener billingStatusListener;

    private BillingManager(Context context) {
        this.context = context.getApplicationContext();
        setupBillingClient();
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
     * 구매 내역 처리 (구독 상태 업데이트)
     */
    private void processPurchases(List<Purchase> purchases) {
        SubscriptionManager subscriptionManager = SubscriptionManager.getInstance(context);
        boolean isSubscribed = false;
        long expiryTimestamp = 0;
        String subscriptionType = Constants.SUBSCRIPTION_NONE;

        for (Purchase purchase : purchases) {
            // 구매 상태 확인
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                // 구매 인정(Acknowledge) 상태 확인
                if (!purchase.isAcknowledged()) {
                    acknowledgePurchase(purchase);
                }

                // 구독 상품 확인
                List<String> skus = purchase.getProducts();
                if (skus.contains(MONTHLY_SUBSCRIPTION_ID)) {
                    isSubscribed = true;
                    subscriptionType = Constants.SUBSCRIPTION_MONTHLY;

                    // 만료 시간 설정 (현재 시간으로부터 30일 후)
                    expiryTimestamp = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000);

                    Log.d(TAG, "월간 구독 확인됨");
                } else if (skus.contains(YEARLY_SUBSCRIPTION_ID)) {
                    isSubscribed = true;
                    subscriptionType = Constants.SUBSCRIPTION_YEARLY;

                    // 만료 시간 설정 (현재 시간으로부터 365일 후)
                    expiryTimestamp = System.currentTimeMillis() + (365L * 24 * 60 * 60 * 1000);

                    Log.d(TAG, "연간 구독 확인됨");
                }
            }
        }

        // 구독 상태 업데이트
        subscriptionManager.setSubscribed(isSubscribed, expiryTimestamp, subscriptionType);

        // 상태 콜백 호출
        if (billingStatusListener != null) {
            billingStatusListener.onSubscriptionStatusUpdated(isSubscribed, subscriptionType);
        }

        // 자동 갱신 상태 저장
        for (Purchase purchase : purchases) {
            if (purchase.isAutoRenewing()) {
                SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(Constants.PREF_SUBSCRIPTION_AUTO_RENEWING, true);
                editor.apply();
            }
        }
    }

    /**
     * 구매 인정 (Acknowledge)
     */
    private void acknowledgePurchase(Purchase purchase) {
        AcknowledgePurchaseParams acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();

        billingClient.acknowledgePurchase(
                acknowledgePurchaseParams,
                new AcknowledgePurchaseResponseListener() {
                    @Override
                    public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "구매 인정 성공");
                        } else {
                            Log.e(TAG, "구매 인정 실패: " + billingResult.getResponseCode());
                        }
                    }
                });
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
}