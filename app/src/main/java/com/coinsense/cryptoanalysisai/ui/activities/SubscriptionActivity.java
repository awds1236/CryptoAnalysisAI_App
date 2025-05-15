package com.coinsense.cryptoanalysisai.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.ProductDetails;
import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.databinding.ActivitySubscriptionBinding;
import com.coinsense.cryptoanalysisai.services.BillingManager;
import com.coinsense.cryptoanalysisai.services.SubscriptionManager;
import com.coinsense.cryptoanalysisai.utils.Constants;

import java.util.List;

public class SubscriptionActivity extends AppCompatActivity implements BillingManager.BillingStatusListener {

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
                // 이미 월간 구독 중
                Toast.makeText(this, "이미 월간 구독 이용 중입니다.", Toast.LENGTH_SHORT).show();
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
                // 이미 연간 구독 중
                Toast.makeText(this, "이미 연간 구독 이용 중입니다.", Toast.LENGTH_SHORT).show();
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
        if (subscriptionManager.isSubscribed()) {
            int remainingDays = subscriptionManager.getRemainingDays();
            String expiryDate = subscriptionManager.getExpiryDateString();
            String subscriptionType = subscriptionManager.getSubscriptionType();
            boolean isAutoRenewing = subscriptionManager.isAutoRenewing();
            String nextBillingDate = subscriptionManager.getNextBillingDateString();

            String typeName = Constants.SUBSCRIPTION_MONTHLY.equals(subscriptionType)
                    ? "월간" : "연간";

            // 구독 상태 텍스트 업데이트
            binding.tvSubscriptionStatus.setText(
                    String.format("프리미엄 구독 중 (%s)\n만료일: %s (%d일 남음)",
                            typeName, expiryDate, remainingDays));

            // 결제 정보 섹션 추가
            binding.tvBillingInfo.setVisibility(View.VISIBLE);
            binding.tvBillingInfo.setText(
                    String.format("결제 정보:\n• 구독 시작일: %s\n• 다음 결제일: %s\n• 자동 갱신: %s",
                            subscriptionManager.getStartDateString(),
                            nextBillingDate,
                            isAutoRenewing ? "활성화됨" : "비활성화됨"));

            // 구독 취소 버튼 활성화, 구독 버튼 비활성화
            binding.btnCancelSubscription.setVisibility(View.VISIBLE);
            binding.btnMonthlySubscription.setText(subscriptionManager.getMonthlyPrice() + " 구독 중");
            binding.btnMonthlySubscription.setEnabled(false);

            if (Constants.SUBSCRIPTION_YEARLY.equals(subscriptionType)) {
                binding.btnYearlySubscription.setText(subscriptionManager.getYearlyPrice() + " 구독 중");
                binding.btnYearlySubscription.setEnabled(false);
            } else {
                binding.btnYearlySubscription.setText(subscriptionManager.getYearlyPrice() + "으로 업그레이드");
                binding.btnYearlySubscription.setEnabled(true);
            }
        } else {
            binding.tvSubscriptionStatus.setText("무료 사용자");
            binding.tvBillingInfo.setVisibility(View.GONE);

            // 구독 버튼 활성화, 구독 취소 버튼 비활성화
            binding.btnCancelSubscription.setVisibility(View.GONE);
            binding.btnMonthlySubscription.setText(subscriptionManager.getMonthlyPrice() + " 구독하기");
            binding.btnMonthlySubscription.setEnabled(true);
            binding.btnYearlySubscription.setText(subscriptionManager.getYearlyPrice() + " 구독하기");
            binding.btnYearlySubscription.setEnabled(true);
        }

        binding.progressBar.setVisibility(View.GONE);
    }

    /**
     * Google Play 구독 관리 페이지로 이동하는 대화상자 표시
     */
    private void showSubscriptionManagementDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("구독 관리")
                .setMessage("구독을 취소하시면 다음 결제일인 " +
                        subscriptionManager.getNextBillingDateString() +
                        " 이후에는 프리미엄 서비스 이용이 불가능합니다.\n\n" +
                        "취소 후에도 결제일까지는 서비스를 계속 이용할 수 있습니다.")
                .setPositiveButton("구독 관리로 이동", (dialog, which) -> {
                    // Google Play 구독 관리 페이지로 이동
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://play.google.com/store/account/subscriptions")));
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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