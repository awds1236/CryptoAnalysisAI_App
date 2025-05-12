package com.example.cryptoanalysisai.ui.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cryptoanalysisai.R;
import com.example.cryptoanalysisai.databinding.ActivitySubscriptionBinding;
import com.example.cryptoanalysisai.services.SubscriptionManager;
import com.example.cryptoanalysisai.utils.Constants;

public class SubscriptionActivity extends AppCompatActivity {

    private ActivitySubscriptionBinding binding;
    private SubscriptionManager subscriptionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySubscriptionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        subscriptionManager = SubscriptionManager.getInstance(this);

        // 현재 구독 상태 표시
        updateSubscriptionStatus();

        // 월 구독 버튼
        binding.btnMonthlySubscription.setOnClickListener(v -> {
            // 실제로는 Google Play Billing을 통해 결제 처리
            // 여기서는 테스트용으로 바로 구독 활성화
            subscriptionManager.activateMonthlySubscription();
            Toast.makeText(this, "월간 구독이 활성화되었습니다.", Toast.LENGTH_SHORT).show();
            updateSubscriptionStatus();
        });

        // 연간 구독 버튼
        binding.btnYearlySubscription.setOnClickListener(v -> {
            // 실제로는 Google Play Billing을 통해 결제 처리
            subscriptionManager.activateYearlySubscription();
            Toast.makeText(this, "연간 구독이 활성화되었습니다.", Toast.LENGTH_SHORT).show();
            updateSubscriptionStatus();
        });

        // 구독 취소 버튼
        binding.btnCancelSubscription.setOnClickListener(v -> {
            subscriptionManager.cancelSubscription();
            Toast.makeText(this, "구독이 취소되었습니다.", Toast.LENGTH_SHORT).show();
            updateSubscriptionStatus();
        });

        // 닫기 버튼
        binding.btnClose.setOnClickListener(v -> finish());
    }

    private void updateSubscriptionStatus() {
        if (subscriptionManager.isSubscribed()) {
            int remainingDays = subscriptionManager.getRemainingDays();
            String expiryDate = subscriptionManager.getExpiryDateString();
            String subscriptionType = subscriptionManager.getSubscriptionType();

            String typeName = Constants.SUBSCRIPTION_MONTHLY.equals(subscriptionType)
                    ? "월간" : "연간";

            binding.tvSubscriptionStatus.setText(
                    String.format("프리미엄 구독 중 (%s)\n만료일: %s (%d일 남음)",
                            typeName, expiryDate, remainingDays));

            // 구독 취소 버튼 활성화, 구독 버튼 비활성화
            binding.btnCancelSubscription.setVisibility(View.VISIBLE);
            binding.btnMonthlySubscription.setText("월간 구독 중");
            binding.btnMonthlySubscription.setEnabled(false);

            if (Constants.SUBSCRIPTION_YEARLY.equals(subscriptionType)) {
                binding.btnYearlySubscription.setText("연간 구독 중");
                binding.btnYearlySubscription.setEnabled(false);
            } else {
                binding.btnYearlySubscription.setText("연간 구독으로 업그레이드");
                binding.btnYearlySubscription.setEnabled(true);
            }
        } else {
            binding.tvSubscriptionStatus.setText("무료 사용자");

            // 구독 버튼 활성화, 구독 취소 버튼 비활성화
            binding.btnCancelSubscription.setVisibility(View.GONE);
            binding.btnMonthlySubscription.setText("월간 구독하기");
            binding.btnMonthlySubscription.setEnabled(true);
            binding.btnYearlySubscription.setText("연간 구독하기");
            binding.btnYearlySubscription.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}