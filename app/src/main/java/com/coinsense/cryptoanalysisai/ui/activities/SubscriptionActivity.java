package com.coinsense.cryptoanalysisai.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.ProductDetails;
import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.databinding.ActivitySubscriptionBinding;
import com.coinsense.cryptoanalysisai.services.BillingManager;
import com.coinsense.cryptoanalysisai.services.SubscriptionManager;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.coinsense.cryptoanalysisai.utils.LocaleHelper;

import java.util.List;

public class SubscriptionActivity extends BaseActivity implements BillingManager.BillingStatusListener {

    private static final String TAG = "SubscriptionActivity";
    private ActivitySubscriptionBinding binding;
    private SubscriptionManager subscriptionManager;
    private BillingManager billingManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubscriptionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        subscriptionManager = SubscriptionManager.getInstance(this);
        billingManager = BillingManager.getInstance(this);
        billingManager.setBillingStatusListener(this);

        // 현재 구독 상태 표시
        updateSubscriptionStatus();

        // 월 구독 버튼
        binding.btnMonthlySubscription.setOnClickListener(v -> {
            if (subscriptionManager.isSubscribed() &&
                    Constants.SUBSCRIPTION_MONTHLY.equals(subscriptionManager.getSubscriptionType())) {
                // 이미 월간 구독 중 - 문자열 리소스로 변경
                Toast.makeText(this, getString(R.string.already_monthly_subscriber), Toast.LENGTH_SHORT).show();
            } else {
                // 월간 구독 시작
                binding.progressBar.setVisibility(View.VISIBLE);
                billingManager.startSubscription(this, BillingManager.MONTHLY_SUBSCRIPTION_ID);
            }
        });

        // 연간 구독 버튼
        binding.btnYearlySubscription.setOnClickListener(v -> {
            if (subscriptionManager.isSubscribed() &&
                    Constants.SUBSCRIPTION_YEARLY.equals(subscriptionManager.getSubscriptionType())) {
                // 이미 연간 구독 중 - 문자열 리소스로 변경
                Toast.makeText(this, getString(R.string.already_yearly_subscriber), Toast.LENGTH_SHORT).show();
            } else if (subscriptionManager.isSubscribed() &&
                    Constants.SUBSCRIPTION_MONTHLY.equals(subscriptionManager.getSubscriptionType())) {
                // 월간 구독에서 연간 구독으로 업그레이드
                binding.progressBar.setVisibility(View.VISIBLE);
                billingManager.startSubscription(this, BillingManager.YEARLY_SUBSCRIPTION_ID);
            } else {
                // 신규 연간 구독
                binding.progressBar.setVisibility(View.VISIBLE);
                billingManager.startSubscription(this, BillingManager.YEARLY_SUBSCRIPTION_ID);
            }
        });

        // 구독 취소 버튼
        binding.btnCancelSubscription.setOnClickListener(v -> {
            // Google Play 구독 관리 페이지로 이동하는 대화상자 표시
            showSubscriptionManagementDialog();
        });

        // 닫기 버튼
        binding.btnClose.setOnClickListener(v -> finish());

        // 로딩 상태 표시
        binding.progressBar.setVisibility(View.VISIBLE);

        // Google Play에서 상품 정보 조회
        billingManager.connectToPlayBillingService();
    }

    private void updateSubscriptionStatus() {
        // 바인딩 객체가 null인지 확인 (액티비티가 소멸된 상태)
        if (binding == null) {
            Log.d(TAG, "updateSubscriptionStatus: 액티비티가 이미 소멸됨");
            return;
        }

        if (subscriptionManager.isSubscribed()) {
            int remainingDays = subscriptionManager.getRemainingDays();
            String expiryDate = subscriptionManager.getExpiryDateString();
            String subscriptionType = subscriptionManager.getSubscriptionType();
            boolean isAutoRenewing = subscriptionManager.isAutoRenewing();
            String nextBillingDate = subscriptionManager.getNextBillingDateString();

            // 문자열 리소스 사용
            String typeName = Constants.SUBSCRIPTION_MONTHLY.equals(subscriptionType)
                    ? getString(R.string.monthly) : getString(R.string.yearly);

            // 구독 상태 텍스트 업데이트 - 문자열 리소스 사용
            binding.tvSubscriptionStatus.setText(
                    getString(R.string.premium_subscriber_status_format,
                            typeName, expiryDate, remainingDays));

            // 결제 정보 섹션 추가 - 문자열 리소스 사용
            binding.tvBillingInfo.setVisibility(View.VISIBLE);
            binding.tvBillingInfo.setText(
                    getString(R.string.billing_info_format,
                            subscriptionManager.getStartDateString(),
                            nextBillingDate,
                            isAutoRenewing ?
                                    getString(R.string.auto_renew_enabled) :
                                    getString(R.string.auto_renew_disabled)));

            // 구독 취소 버튼 활성화, 구독 버튼 비활성화
            binding.btnCancelSubscription.setVisibility(View.VISIBLE);
            binding.btnMonthlySubscription.setText(
                    getString(R.string.subscribed_to_format, subscriptionManager.getMonthlyPrice()));
            binding.btnMonthlySubscription.setEnabled(false);

            if (Constants.SUBSCRIPTION_YEARLY.equals(subscriptionType)) {
                binding.btnYearlySubscription.setText(
                        getString(R.string.subscribed_to_format, subscriptionManager.getYearlyPrice()));
                binding.btnYearlySubscription.setEnabled(false);
            } else {
                binding.btnYearlySubscription.setText(
                        getString(R.string.upgrade_to_format, subscriptionManager.getYearlyPrice()));
                binding.btnYearlySubscription.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14); // 폰트 크기를 14sp로 설정
                binding.btnYearlySubscription.setEnabled(true);
            }
        } else {
            binding.tvSubscriptionStatus.setText(getString(R.string.free_user));
            binding.tvBillingInfo.setVisibility(View.GONE);

            // 구독 버튼 활성화, 구독 취소 버튼 비활성화
            binding.btnCancelSubscription.setVisibility(View.GONE);
            binding.btnMonthlySubscription.setText(getString(R.string.subscribe_to_format,
                    getString(R.string.subscription_monthly_price)));
            binding.btnYearlySubscription.setText(getString(R.string.subscribe_to_format,
                    getString(R.string.subscription_yearly_price)));
        }

        binding.progressBar.setVisibility(View.GONE);
    }

    /**
     * Google Play 구독 관리 페이지로 이동하는 대화상자 표시
     */
    private void showSubscriptionManagementDialog() {
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.subscription_management))
                .setMessage(getString(R.string.cancel_subscription_message,
                        subscriptionManager.getNextBillingDateString()))
                .setPositiveButton(getString(R.string.go_to_subscription_management), (dialogInterface, which) -> {
                    // Google Play 구독 관리 페이지로 이동
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://play.google.com/store/account/subscriptions")));
                })
                .setNegativeButton(getString(R.string.cancel), (dialogInterface, which) -> dialogInterface.dismiss())
                .create();

        // 다이얼로그 보이기 전에 버튼 색상 설정
        dialog.setOnShowListener(dialogInterface -> {
            // 다크 모드인지 확인
            int nightModeFlags = getResources().getConfiguration().uiMode &
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK;

            boolean isNightMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;

            if (isNightMode) {
                // 다크 모드면 버튼 텍스트 색상을 흰색으로 설정
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(getResources().getColor(android.R.color.white));
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                        .setTextColor(getResources().getColor(android.R.color.white));
            }
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 바인딩 null 설정 전에 리스너 제거
        if (billingManager != null) {
            billingManager.setBillingStatusListener(null);
        }

        binding = null;
    }

    @Override
    public void onProductDetailsReceived(List<ProductDetails> productDetailsList) {
        Log.d(TAG, "상품 정보 수신됨: " + productDetailsList.size() + "개");

        if (productDetailsList.isEmpty()) {
            // 상품 정보가 없는 경우 기본값 사용
            binding.progressBar.setVisibility(View.GONE);
            return;
        }

        // 상품 정보에서 가격 추출
        for (ProductDetails details : productDetailsList) {
            // 구독 상품 정보에서 가격 및 기간 추출
            List<ProductDetails.SubscriptionOfferDetails> offerDetails = details.getSubscriptionOfferDetails();
            if (offerDetails != null && !offerDetails.isEmpty()) {
                // 첫 번째 구독 옵션 사용
                ProductDetails.SubscriptionOfferDetails offer = offerDetails.get(0);

                // 첫 번째 가격 단계 사용
                if (!offer.getPricingPhases().getPricingPhaseList().isEmpty()) {
                    String formattedPrice = offer.getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
                    String productId = details.getProductId();

                    if (BillingManager.MONTHLY_SUBSCRIPTION_ID.equals(productId)) {
                        // 월간 구독 가격
                        binding.btnMonthlySubscription.setText(formattedPrice + " 구독하기");
                    } else if (BillingManager.YEARLY_SUBSCRIPTION_ID.equals(productId)) {
                        // 연간 구독 가격
                        binding.btnYearlySubscription.setText(formattedPrice + " 구독하기");
                    }
                }
            }
        }

        // 로딩 상태 숨기기
        binding.progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onPurchaseComplete() {
        Log.d(TAG, "구매 완료");
        binding.progressBar.setVisibility(View.GONE);
        Toast.makeText(this, "구독이 성공적으로 완료되었습니다.", Toast.LENGTH_SHORT).show();
        updateSubscriptionStatus();
    }

    @Override
    public void onSubscriptionStatusUpdated(boolean isSubscribed, String subscriptionType) {
        Log.d(TAG, "구독 상태 업데이트: " + isSubscribed + ", 유형: " + subscriptionType);
        updateSubscriptionStatus();
    }

    @Override
    public void onBillingError(String errorMessage) {
        Log.e(TAG, "결제 오류: " + errorMessage);
        binding.progressBar.setVisibility(View.GONE);
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

}