package com.example.cryptoanalysisai.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.cryptoanalysisai.R;
import com.example.cryptoanalysisai.models.AnalysisResult;

import java.util.List;

public class StrategyFragment extends Fragment {

    private static final String ARG_STRATEGY_TYPE = "strategy_type";
    private static final String ARG_CURRENCY_SYMBOL = "currency_symbol";

    public static final int STRATEGY_SHORT_TERM = 0;
    public static final int STRATEGY_MID_TERM = 1;
    public static final int STRATEGY_LONG_TERM = 2;

    private int strategyType;
    private String currencySymbol;
    private AnalysisResult.Strategy strategy;

    public static StrategyFragment newInstance(int strategyType, String currencySymbol) {
        StrategyFragment fragment = new StrategyFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_STRATEGY_TYPE, strategyType);
        args.putString(ARG_CURRENCY_SYMBOL, currencySymbol);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            strategyType = getArguments().getInt(ARG_STRATEGY_TYPE);
            currencySymbol = getArguments().getString(ARG_CURRENCY_SYMBOL, "$");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_strategy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvStrategyTitle = view.findViewById(R.id.tvStrategyTitle);
        LinearLayout layoutBuySteps = view.findViewById(R.id.layoutBuySteps);
        TextView tvTargetPrice = view.findViewById(R.id.tvTargetPrice);
        TextView tvStopLoss = view.findViewById(R.id.tvStopLoss);
        TextView tvRiskReward = view.findViewById(R.id.tvRiskReward);
        TextView tvStrategyDetail = view.findViewById(R.id.tvStrategyDetail);

        // 타이틀 설정
        switch (strategyType) {
            case STRATEGY_SHORT_TERM:
                tvStrategyTitle.setText("단기 매매 전략 (24시간)");
                break;
            case STRATEGY_MID_TERM:
                tvStrategyTitle.setText("중기 매매 전략 (1주일)");
                break;
            case STRATEGY_LONG_TERM:
                tvStrategyTitle.setText("장기 매매 전략 (1개월)");
                break;
        }

        // 전략 데이터가 설정되었으면 표시
        if (strategy != null) {
            // 매수 단계 표시
            displayBuySteps(layoutBuySteps, strategy.getBuySteps());

            // 목표가 표시
            if (strategy.getTargetPrices() != null && !strategy.getTargetPrices().isEmpty()) {
                StringBuilder targetPrices = new StringBuilder();
                for (int i = 0; i < strategy.getTargetPrices().size(); i++) {
                    if (i > 0) {
                        targetPrices.append(", ");
                    }
                    targetPrices.append(currencySymbol).append(String.format("%,.2f", strategy.getTargetPrices().get(i)));
                }
                tvTargetPrice.setText(targetPrices.toString());
            } else {
                tvTargetPrice.setText("설정된 목표가 없음");
            }

            // 손절매 라인 표시
            tvStopLoss.setText(String.format("%s%,.2f", currencySymbol, strategy.getStopLoss()));

            // 리스크 대비 보상 비율 표시
            tvRiskReward.setText(String.format("%.1f:1", strategy.getRiskRewardRatio()));

            // 전략 설명 표시
            tvStrategyDetail.setText(strategy.getExplanation());
        } else {
            // 전략 데이터 없음
            tvTargetPrice.setText("데이터 없음");
            tvStopLoss.setText("데이터 없음");
            tvRiskReward.setText("데이터 없음");
            tvStrategyDetail.setText("데이터 없음");
        }
    }

    /**
     * 매수 단계 표시
     */
    private void displayBuySteps(LinearLayout container, List<AnalysisResult.Strategy.TradingStep> buySteps) {
        container.removeAllViews();

        if (buySteps == null || buySteps.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("설정된 매수 단계 없음");
            container.addView(tvEmpty);
            return;
        }

        for (AnalysisResult.Strategy.TradingStep step : buySteps) {
            View itemView = getLayoutInflater().inflate(R.layout.item_buy_step, container, false);

            TextView tvBuyStepTitle = itemView.findViewById(R.id.tvBuyStepTitle);
            TextView tvBuyStepPercentage = itemView.findViewById(R.id.tvBuyStepPercentage);
            TextView tvBuyStepDescription = itemView.findViewById(R.id.tvBuyStepDescription);

            tvBuyStepTitle.setText(String.format("진입점: %s%,.2f", currencySymbol, step.getPrice()));
            tvBuyStepPercentage.setText(String.format("%d%%", step.getPercentage()));
            tvBuyStepDescription.setText(step.getDescription());

            container.addView(itemView);
        }
    }

    /**
     * 전략 데이터 설정
     */
    public void setStrategy(AnalysisResult.Strategy strategy) {
        this.strategy = strategy;

        // 프래그먼트가 이미 생성되었다면 UI 업데이트
        if (getView() != null) {
            onViewCreated(getView(), null);
        }
    }
}