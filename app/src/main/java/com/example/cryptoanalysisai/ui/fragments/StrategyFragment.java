package com.example.cryptoanalysisai.ui.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.cryptoanalysisai.R;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.services.ExchangeRateManager;
import com.example.cryptoanalysisai.services.SubscriptionManager;
import com.example.cryptoanalysisai.ui.activities.SubscriptionActivity;
import com.example.cryptoanalysisai.ui.dialogs.AdViewDialog;
import com.example.cryptoanalysisai.services.AdManager;

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
    private SubscriptionManager subscriptionManager;

    // UI 요소 참조
    private TextView tvStrategyTitle;
    private LinearLayout layoutBuySteps;
    private TextView tvTargetPrice;
    private TextView tvStopLoss;
    private TextView tvRiskReward;
    private TextView tvStrategyDetail;
    private View blurOverlay;
    private View pixelatedOverlay;
    private View btnSubscribe;
    private View contentArea;


    private AdManager adManager;
    private TextView tvAdStatus;
    private Button btnWatchAd;
    private Handler adTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable adTimerRunnable;
    private CoinInfo coinInfo;

    private View additionalBlurLayer;

    public void setCoinInfo(CoinInfo coinInfo) {
        this.coinInfo = coinInfo;
    }

    private ExchangeRateManager exchangeRateManager;

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

        subscriptionManager = SubscriptionManager.getInstance(requireContext());
        exchangeRateManager = ExchangeRateManager.getInstance(); // 추가

        adManager = AdManager.getInstance(requireContext());
        subscriptionManager = SubscriptionManager.getInstance(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_strategy, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // UI 요소 초기화
        tvStrategyTitle = view.findViewById(R.id.tvStrategyTitle);
        layoutBuySteps = view.findViewById(R.id.layoutBuySteps);
        tvTargetPrice = view.findViewById(R.id.tvTargetPrice);
        tvStopLoss = view.findViewById(R.id.tvStopLoss);
        tvRiskReward = view.findViewById(R.id.tvRiskReward);
        tvStrategyDetail = view.findViewById(R.id.tvStrategyDetail);
        blurOverlay = view.findViewById(R.id.blurOverlay);
        pixelatedOverlay = view.findViewById(R.id.pixelatedOverlay);
        btnSubscribe = view.findViewById(R.id.btnSubscribe);
        contentArea = view.findViewById(R.id.contentArea);
        btnWatchAd = view.findViewById(R.id.btnWatchAd);
        if (btnWatchAd == null) {
            Log.e("StrategyFragment", "btnWatchAd를 찾을 수 없습니다");
        }
        additionalBlurLayer = view.findViewById(R.id.additionalBlurLayer);

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

        // 구독 상태 확인
        boolean isSubscribed = subscriptionManager.isSubscribed();

        // 전략 데이터가 있으면 모든 내용 표시 (구독 여부 상관없이)
        if (strategy != null) {
            // 매수 단계 표시 - 모든 데이터 표시
            displayBuySteps(layoutBuySteps, strategy.getBuySteps());

            // 목표가 표시 수정
            if (strategy.getTargetPrices() != null && !strategy.getTargetPrices().isEmpty()) {
                StringBuilder targetPrices = new StringBuilder();
                for (int i = 0; i < strategy.getTargetPrices().size(); i++) {
                    double targetPrice = strategy.getTargetPrices().get(i);
                    if (i > 0) {
                        targetPrices.append("<br>");
                    }

                    // 달러 기본 가격 포맷
                    String basePrice = String.format(Locale.getDefault(), "%s%,.2f",
                            currencySymbol, targetPrice);

                    // 원화 환산 추가
                    String displayPrice;
                    if ("$".equals(currencySymbol) && exchangeRateManager.getUsdToKrwRate() > 0) {
                        double krwPrice = exchangeRateManager.convertUsdToKrw(targetPrice);
                        displayPrice = String.format("%s (₩%,.0f)", basePrice, krwPrice);
                    } else {
                        displayPrice = basePrice;
                    }

                    // 목표 번호와 가격
                    String targetLabel = String.format(Locale.getDefault(), "목표 %d: %s",
                            i + 1, displayPrice);

                    // 색상 코드 등 기존 표시 로직 유지
                    String colorCode;
                    if (i == 0) {
                        colorCode = "#4CAF50";
                    } else if (i == 1) {
                        colorCode = "#FF9800";
                    } else {
                        colorCode = "#F44336";
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

            // 손절매 라인 표시
            if (strategy.getStopLoss() > 0) {
                double stopLoss = strategy.getStopLoss();
                String baseStopLoss = String.format(Locale.getDefault(), "%s%,.2f",
                        currencySymbol, stopLoss);

                String displayStopLoss;
                if ("$".equals(currencySymbol) && exchangeRateManager.getUsdToKrwRate() > 0) {
                    double krwStopLoss = exchangeRateManager.convertUsdToKrw(stopLoss);
                    displayStopLoss = String.format("%s (₩%,.0f)", baseStopLoss, krwStopLoss);
                } else {
                    displayStopLoss = baseStopLoss;
                }

                tvStopLoss.setText(Html.fromHtml("<font color='#F44336'><b>" + displayStopLoss +
                        "</b></font>", Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvStopLoss.setText("설정된 손절매 라인 없음");
            }

            // 리스크 대비 보상 비율 표시
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

            // 전략 설명 표시
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

        // onViewCreated() 메서드 내에서 구독 상태 체크 부분 수정
        // 구독 상태에 따라 콘텐츠 블러 처리
        if (!isSubscribed) {
            // 블러 오버레이 표시 - 제목 부분만 제외하고 나머지 전체에 적용
            blurOverlay.setVisibility(View.VISIBLE);
            pixelatedOverlay.setVisibility(View.VISIBLE);

            // 추가 블러 레이어 표시 - 더 진한 색상으로 설정
            View additionalBlurLayer = view.findViewById(R.id.additionalBlurLayer);
            additionalBlurLayer.setVisibility(View.VISIBLE);
            additionalBlurLayer.setBackgroundColor(Color.parseColor("#B3000000")); // 더 진한 검은색 반투명(70%)

            // 콘텐츠 자체를 더 흐리게 처리
            contentArea.setAlpha(0.5f);  // 콘텐츠 거의 완전히 숨김

            // 추가: 텍스트 내용을 별표나 의미 없는 문자로 대체하여 이중으로 보호
            if (strategy != null) {
                // 목표가, 손절매 등에 별표 처리 추가
                tvTargetPrice.setText("목표 1: **********\n목표 2: **********");
                tvStopLoss.setText("**********");
                tvRiskReward.setText("*.**:*");
                tvStrategyDetail.setText("**************** ******** ***** ************\n****************** ************");

                // 진입 지점 정보 숨기기 (첫 번째만 제외하고)
                if (strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty()) {
                    displayFirstBuyStepWithBlur(layoutBuySteps, strategy.getBuySteps().get(0));
                }
            }

            // 구독 버튼 표시 - 강조 표시 및 셰도우 효과 추가
            btnSubscribe.setVisibility(View.VISIBLE);
            btnSubscribe.setElevation(24f);  // 입체감 더 강화

            // 버튼 주변에 빛나는 효과 추가 (드로어블 리소스로 배경 교체)
            btnSubscribe.setBackgroundResource(R.drawable.glowing_button);

            btnSubscribe.setOnClickListener(v -> {
                // 구독 화면으로 이동
                Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
                startActivity(intent);
            });
        } else {
            // 구독된 경우 일반 콘텐츠 표시
            blurOverlay.setVisibility(View.GONE);
            pixelatedOverlay.setVisibility(View.GONE);

            View additionalBlurLayer = view.findViewById(R.id.additionalBlurLayer);
            additionalBlurLayer.setVisibility(View.GONE);

            contentArea.setAlpha(1.0f);  // 완전 불투명 (정상 표시)
            btnSubscribe.setVisibility(View.GONE);
        }

        // 환율 정보 갱신 (이미 최신 정보가 있다면 불필요한 API 호출 방지)
        if (exchangeRateManager.getUsdToKrwRate() <= 0) {
            exchangeRateManager.fetchExchangeRate(new ExchangeRateManager.OnExchangeRateListener() {
                @Override
                public void onExchangeRateUpdated(double rate) {
                    // 환율 정보가 업데이트되면 UI 새로고침
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            updateUI();
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    // 에러 처리 (필요시 사용자에게 알림)
                    Log.e("StrategyFragment", "환율 정보 로드 실패: " + errorMessage);
                }
            });
        }


        // 광고 상태 및 버튼 뷰 찾기
        tvAdStatus = view.findViewById(R.id.tvAdStatus);
        btnWatchAd = view.findViewById(R.id.btnWatchAd);

        if (btnWatchAd != null) {
            btnWatchAd.setOnClickListener(v -> {
                showAdDialog();
            });
        }

        // 콘텐츠 접근 권한 확인 및 UI 업데이트
        updateContentAccessUI();

        // 매 분마다 타이머 업데이트
        startAdTimer();
    }

    // UI 업데이트를 위한 헬퍼 메서드
    private void updateUI() {
        if (strategy != null) {
            displayBuySteps(layoutBuySteps, strategy.getBuySteps());
            // 기타 UI 업데이트 로직...
        }
    }

    @Override
    public void onDestroyView() {
        stopAdTimer();
        super.onDestroyView();
    }


    // 광고 대화상자 표시
    private void showAdDialog() {
        if (getActivity() == null || coinInfo == null) return;

        AdViewDialog dialog = AdViewDialog.newInstance(
                coinInfo.getSymbol(),
                coinInfo.getDisplayName()
        );

        dialog.setCompletionListener(coinSymbol -> {
            // 광고 시청 완료 - UI 업데이트
            updateContentAccessUI();
        });

        dialog.show(getParentFragmentManager(), "ad_dialog");
    }

    // 콘텐츠 접근 권한 UI 업데이트
    private void updateContentAccessUI() {
        if (coinInfo == null || coinInfo.getSymbol() == null) return;

        boolean isSubscribed = subscriptionManager.isSubscribed();
        boolean hasAdPermission = adManager.hasActiveAdPermission(coinInfo.getSymbol());

        if (isSubscribed || hasAdPermission) {
            // 구독자이거나 광고 시청한 경우 콘텐츠 표시
            blurOverlay.setVisibility(View.GONE);
            pixelatedOverlay.setVisibility(View.GONE);
            additionalBlurLayer.setVisibility(View.GONE);
            contentArea.setAlpha(1.0f);
            btnSubscribe.setVisibility(View.GONE);

            // 광고 보기 버튼 숨김
            btnWatchAd.setVisibility(View.GONE);

            // 구독자가 아니고 광고 시청한 경우 남은 시간 표시
            if (!isSubscribed && hasAdPermission) {
                int remainingMinutes = adManager.getRemainingMinutes(coinInfo.getSymbol());
                tvAdStatus.setVisibility(View.VISIBLE);
                tvAdStatus.setText("광고 시청 후 " + remainingMinutes + "분 남음");
            } else {
                tvAdStatus.setVisibility(View.GONE);
            }

            // 전략 데이터가 있으면 실제 내용 표시
            if (strategy != null) {
                displayBuySteps(layoutBuySteps, strategy.getBuySteps());
                // 나머지 데이터 표시 코드...
            }
        } else {
            // 구독자도 아니고 광고도 안 본 경우 콘텐츠 가림
            blurOverlay.setVisibility(View.VISIBLE);
            pixelatedOverlay.setVisibility(View.VISIBLE);
            additionalBlurLayer.setVisibility(View.VISIBLE);
            contentArea.setAlpha(0.5f);

            // 구독 버튼 및 광고 버튼 표시
            btnSubscribe.setVisibility(View.VISIBLE);
            btnWatchAd.setVisibility(View.VISIBLE);

            // 버튼 위치 조정 - btnWatchAd의 margin을 설정
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) btnWatchAd.getLayoutParams();
            if (params != null) {
                params.topMargin = (int) (80 * getResources().getDisplayMetrics().density); // 80dp
                btnWatchAd.setLayoutParams(params);
            }

            // 광고 상태 숨김
            tvAdStatus.setVisibility(View.GONE);

            // 콘텐츠 마스킹 코드...
        }
    }

    // 광고 타이머 시작
    private void startAdTimer() {
        adTimerRunnable = new Runnable() {
            @Override
            public void run() {
                updateContentAccessUI();
                adTimerHandler.postDelayed(this, 60000); // 1분마다 업데이트
            }
        };

        adTimerHandler.post(adTimerRunnable);
    }

    // 광고 타이머 중지
    private void stopAdTimer() {
        if (adTimerHandler != null && adTimerRunnable != null) {
            adTimerHandler.removeCallbacks(adTimerRunnable);
        }
    }


    /**
     * 첫 번째 매수 단계만 블러 처리된 상태로 표시 (미끼용)
     */
    private void displayFirstBuyStepWithBlur(LinearLayout container, AnalysisResult.Strategy.TradingStep step) {
        container.removeAllViews();

        if (step == null) {
            return;
        }

        View itemView = getLayoutInflater().inflate(R.layout.item_buy_step, null, false);

        TextView tvBuyStepTitle = itemView.findViewById(R.id.tvBuyStepTitle);
        TextView tvBuyStepPercentage = itemView.findViewById(R.id.tvBuyStepPercentage);
        TextView tvBuyStepDescription = itemView.findViewById(R.id.tvBuyStepDescription);

        // 첫 진입점 표시 - 일부만 보이고 나머지는 별표로
        String emoji = "1️⃣ ";
        String price = String.format("%s%,.2f", currencySymbol, step.getPrice());
        // 가격 일부만 보이게 처리
        String maskedPrice = price.substring(0, Math.min(price.length(), 2)) + "********";
        String title = emoji + "진입점: " + maskedPrice;

        tvBuyStepTitle.setText(title);
        tvBuyStepTitle.setTextColor(Color.parseColor("#4CAF50")); // 녹색

        tvBuyStepPercentage.setText("**%");

        // 설명 모두 마스킹
        tvBuyStepDescription.setText("*** *** ***** (프리미엄 구독 시 확인 가능)");

        // 아이템 반투명하게 설정
        itemView.setAlpha(0.3f);

        // 컨테이너에 추가
        container.addView(itemView);

        // "더 보기" 텍스트 추가
        TextView tvMore = new TextView(getContext());
        tvMore.setText("+ 더 많은 전략 정보는 구독 후 확인 가능");
        tvMore.setTextSize(14);
        tvMore.setTypeface(null, Typeface.ITALIC);
        tvMore.setTextColor(Color.GRAY);
        tvMore.setPadding(0, 16, 0, 16);
        container.addView(tvMore);
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
            View itemView = getLayoutInflater().inflate(R.layout.item_buy_step, null, false);

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

            // 진입점 표시 부분 수정
            double price = step.getPrice();
            String formattedUsdPrice = String.format("%s%.2f", currencySymbol, price);
            String formattedPrice;

            // 달러 가격에 원화 추가
            if ("$".equals(currencySymbol) && exchangeRateManager.getUsdToKrwRate() > 0) {
                double krwPrice = exchangeRateManager.convertUsdToKrw(price);
                formattedPrice = String.format("%s (₩%,.0f)", formattedUsdPrice, krwPrice);
            } else {
                formattedPrice = formattedUsdPrice;
            }

            String title = emoji + "진입점: " + formattedPrice;
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
            cardView.setCardElevation(0f); // 그림자 제거
            cardView.setUseCompatPadding(false); // 호환성 패딩 제거

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
            cardView.setForeground(null); // 포그라운드 제거 (API 23 이상에서만 동작)

            // 카드뷰에 내용 추가
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 8, 0, 8);  // 상하 마진 추가
            cardView.setLayoutParams(params);

            // 아이템 뷰를 카드뷰에 추가 - 이전 버그 수정
            cardView.addView(itemView);

            // 컨테이너에 카드뷰 추가
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