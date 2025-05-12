package com.example.cryptoanalysisai.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.cryptoanalysisai.R;
import com.example.cryptoanalysisai.models.AnalysisResult;

import java.util.List;
import java.util.Locale;

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
        String title;
        int titleColor;
        String emoji;

        switch (strategyType) {
            case STRATEGY_SHORT_TERM:
                title = "단기 매매 전략 (24시간)";
                titleColor = Color.parseColor("#4CAF50"); // 녹색
                emoji = "⚡"; // 번개 이모지
                break;
            case STRATEGY_MID_TERM:
                title = "중기 매매 전략 (1주일)";
                titleColor = Color.parseColor("#2196F3"); // 파란색
                emoji = "📈"; // 차트 이모지
                break;
            case STRATEGY_LONG_TERM:
                title = "장기 매매 전략 (1개월)";
                titleColor = Color.parseColor("#9C27B0"); // 보라색
                emoji = "🔮"; // 수정구슬 이모지
                break;
            default:
                title = "매매 전략";
                titleColor = Color.BLACK;
                emoji = "📊";
                break;
        }

        tvStrategyTitle.setText(emoji + " " + title);
        tvStrategyTitle.setTextColor(titleColor);

        // 전략 데이터가 설정되었으면 표시
        if (strategy != null) {
            // 매수 단계 표시
            displayBuySteps(layoutBuySteps, strategy.getBuySteps());

            // 목표가 표시 - 현재가와 비교하여 색상 표시
            if (strategy.getTargetPrices() != null && !strategy.getTargetPrices().isEmpty()) {
                StringBuilder targetPrices = new StringBuilder();
                for (int i = 0; i < strategy.getTargetPrices().size(); i++) {
                    double targetPrice = strategy.getTargetPrices().get(i);
                    if (i > 0) {
                        targetPrices.append("<br>");
                    }

                    // 목표 번호와 가격
                    String targetLabel = String.format(Locale.getDefault(), "목표 %d: %s%,.2f",
                            i + 1, currencySymbol, targetPrice);

                    // 첫 번째 목표는 녹색, 두 번째는 주황색, 세 번째 이상은 빨간색
                    String colorCode;
                    if (i == 0) {
                        colorCode = "#4CAF50"; // 녹색 - 첫 번째 목표
                    } else if (i == 1) {
                        colorCode = "#FF9800"; // 주황색 - 두 번째 목표
                    } else {
                        colorCode = "#F44336"; // 빨간색 - 세 번째 이상 목표
                    }

                    targetPrices.append("<font color='")
                            .append(colorCode)
                            .append("'><b>")
                            .append(targetLabel)
                            .append("</b></font>");
                }
                tvTargetPrice.setText(Html.fromHtml(targetPrices.toString(), Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvTargetPrice.setText("설정된 목표가 없음");
            }

            // 손절매 라인 표시 - 현재가와 비교
            if (strategy.getStopLoss() > 0) {
                String stopLossText = String.format(Locale.getDefault(), "%s%,.2f", currencySymbol, strategy.getStopLoss());
                tvStopLoss.setText(Html.fromHtml("<font color='#F44336'><b>" + stopLossText + "</b></font>", Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvStopLoss.setText("설정된 손절매 라인 없음");
            }

            // 리스크 대비 보상 비율 표시 - 색상으로 표시
            if (strategy.getRiskRewardRatio() > 0) {
                String colorCode;
                if (strategy.getRiskRewardRatio() >= 3.0) {
                    colorCode = "#4CAF50"; // 녹색 - 좋은 비율
                } else if (strategy.getRiskRewardRatio() >= 2.0) {
                    colorCode = "#FF9800"; // 주황색 - 보통 비율
                } else {
                    colorCode = "#F44336"; // 빨간색 - 낮은 비율
                }

                String rrText = String.format(Locale.getDefault(), "%.1f:1", strategy.getRiskRewardRatio());
                tvRiskReward.setText(Html.fromHtml("<font color='" + colorCode + "'><b>" + rrText + "</b></font>", Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvRiskReward.setText("정보 없음");
            }

            // 전략 설명 표시 - 키워드 강조
            if (strategy.getExplanation() != null && !strategy.getExplanation().isEmpty()) {
                String explanation = highlightStrategyText(strategy.getExplanation());
                tvStrategyDetail.setText(Html.fromHtml(explanation, Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvStrategyDetail.setText("전략 설명 없음");
            }
        } else {
            // 전략 데이터 없음
            tvTargetPrice.setText("데이터 없음");
            tvStopLoss.setText("데이터 없음");
            tvRiskReward.setText("데이터 없음");
            tvStrategyDetail.setText("데이터 없음");
        }
    }

    /**
     * 전략 텍스트에서 키워드 강조
     */
    private String highlightStrategyText(String text) {
        if (text == null || text.isEmpty()) return "";

        // 매수/매도 관련 키워드
        text = text.replaceAll("(?i)\\b(매수|진입|분할매수)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(매도|이익실현|수익실현)\\b", "<font color='#FF9800'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(손절매|손절)\\b", "<font color='#F44336'><b>$1</b></font>");

        // 시간 관련 키워드
        text = text.replaceAll("(?i)\\b(단기|24시간)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(중기|1주일)\\b", "<font color='#2196F3'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(장기|1개월)\\b", "<font color='#9C27B0'><b>$1</b></font>");

        // 가격/추세 관련 키워드
        text = text.replaceAll("(?i)\\b(상승|오름|증가|반등)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(하락|내림|감소|조정)\\b", "<font color='#F44336'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(횡보|박스권|보합)\\b", "<font color='#FFC107'><b>$1</b></font>");

        // 심리적/기술적 키워드
        text = text.replaceAll("(?i)\\b(지지선|지지대|바닥)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(저항선|저항대|고점)\\b", "<font color='#F44336'><b>$1</b></font>");

        return text;
    }

    /**
     * 매수 단계 표시 - 향상된 시각적 디자인
     */
    private void displayBuySteps(LinearLayout container, List<AnalysisResult.Strategy.TradingStep> buySteps) {
        container.removeAllViews();

        if (buySteps == null || buySteps.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText("분석 시점에는 매수할 적절한 시점이 아닙니다.\n조정 후 진입 기회를 대기하세요.");
            tvEmpty.setTextColor(Color.parseColor("#FF9800")); // 주황색
            container.addView(tvEmpty);
            return;
        }

        for (int i = 0; i < buySteps.size(); i++) {
            AnalysisResult.Strategy.TradingStep step = buySteps.get(i);
            View itemView = getLayoutInflater().inflate(R.layout.item_buy_step, container, false);

            TextView tvBuyStepTitle = itemView.findViewById(R.id.tvBuyStepTitle);
            TextView tvBuyStepPercentage = itemView.findViewById(R.id.tvBuyStepPercentage);
            TextView tvBuyStepDescription = itemView.findViewById(R.id.tvBuyStepDescription);

            // 색상 선정 (첫 진입은 녹색, 두 번째는 파란색, 세 번째 이상은 보라색)
            int titleColor;
            String emoji;
            if (i == 0) {
                titleColor = Color.parseColor("#4CAF50"); // 녹색
                emoji = "1️⃣ ";
            } else if (i == 1) {
                titleColor = Color.parseColor("#2196F3"); // 파란색
                emoji = "2️⃣ ";
            } else {
                titleColor = Color.parseColor("#9C27B0"); // 보라색
                emoji = "3️⃣ ";
            }

            String title = emoji + String.format("진입점: %s%,.2f", currencySymbol, step.getPrice());
            tvBuyStepTitle.setText(title);
            tvBuyStepTitle.setTextColor(titleColor);

            tvBuyStepPercentage.setText(String.format("%d%%", step.getPercentage()));

            // 설명 텍스트 강조
            if (step.getDescription() != null && !step.getDescription().isEmpty()) {
                String description = highlightStrategyText(step.getDescription());
                tvBuyStepDescription.setText(Html.fromHtml(description, Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvBuyStepDescription.setText("");
            }

            // 매수 단계 카드 배경색 설정 (매우 연한 색상)
            CardView cardView = new CardView(getContext());
            cardView.setRadius(16f); // 둥근 모서리
            cardView.setCardElevation(4f); // 약간의 그림자

            // 배경색 설정 (매우 투명한 색상)
            int backgroundColor;
            if (i == 0) {
                backgroundColor = Color.parseColor("#104CAF50"); // 녹색 10% 투명도
            } else if (i == 1) {
                backgroundColor = Color.parseColor("#102196F3"); // 파란색 10% 투명도
            } else {
                backgroundColor = Color.parseColor("#109C27B0"); // 보라색 10% 투명도
            }
            cardView.setCardBackgroundColor(backgroundColor);

            // 카드뷰에 내용 추가
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 8, 0, 8);  // 상하 마진 추가
            cardView.setLayoutParams(params);

            // 아이템 뷰를 카드뷰에 추가
            ((ViewGroup) itemView.getParent()).removeView(itemView); // 기존 부모에서 제거
            cardView.addView(itemView);

            container.addView(cardView);
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