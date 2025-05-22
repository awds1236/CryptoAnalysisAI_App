package com.coinsense.cryptoanalysisai.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.models.AnalysisResult;
import com.coinsense.cryptoanalysisai.models.CoinInfo;
import com.coinsense.cryptoanalysisai.services.ExchangeRateManager;
import com.coinsense.cryptoanalysisai.services.SubscriptionManager;
import com.coinsense.cryptoanalysisai.ui.activities.SubscriptionActivity;
import com.coinsense.cryptoanalysisai.ui.dialogs.AdViewDialog;
import com.coinsense.cryptoanalysisai.services.AdManager;
import com.coinsense.cryptoanalysisai.utils.Constants;

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
        if (coinInfo == null) {
            // Context 체크를 추가하여 Fragment가 연결된 경우에만 getString() 호출
            if (isAdded()) {
                Log.e("StrategyFragment", getString(R.string.no_coin_info_log));
            } else {
                // Fragment가 연결되지 않은 경우 하드코딩 문자열 사용
                Log.e("StrategyFragment", "setCoinInfo: coinInfo is null");
            }
            return;
        }

        // 현재 coinInfo와 새 coinInfo의 심볼이 같은지 확인
        boolean isSameCoin = this.coinInfo != null &&
                this.coinInfo.getSymbol() != null &&
                coinInfo.getSymbol() != null &&
                this.coinInfo.getSymbol().equals(coinInfo.getSymbol());

        // 같은 코인이 아닌 경우에만 설정 (중복 업데이트 방지)
        if (!isSameCoin) {
            this.coinInfo = coinInfo;

            // Context 체크 추가
            if (isAdded()) {
                Log.d("StrategyFragment", coinInfo != null ?
                        getString(R.string.coin_info_set_log_format, coinInfo.getSymbol()) :
                        getString(R.string.no_coin_info_log));
            } else {
                // Fragment가 연결되지 않은 경우 하드코딩 문자열 사용
                Log.d("StrategyFragment", coinInfo != null ?
                        "setCoinInfo: coinInfo set, symbol: " + coinInfo.getSymbol() :
                        "setCoinInfo: coinInfo is null");
            }

            // coinInfo 정보 저장 (테마 변경 시 복원을 위해)
            saveCurrentCoinInfo();

            // 코인 정보가 변경되면 UI 업데이트
            if (getView() != null) {
                updateContentAccessUI();
            }
        }
    }

    // 추가 메서드
    // 현재 코인 정보 저장
    private void saveCurrentCoinInfo() {
        if (coinInfo != null && coinInfo.getSymbol() != null && getContext() != null) {
            SharedPreferences prefs = getContext().getSharedPreferences(
                    "StrategyFragment_" + strategyType, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("COIN_SYMBOL", coinInfo.getSymbol());
            editor.putString("COIN_MARKET", coinInfo.getMarket());
            editor.putString("COIN_NAME", coinInfo.getDisplayName());
            editor.apply();
        }
    }

    // 저장된 코인 정보 복원
    private void restoreCurrentCoinInfo() {
        if (getContext() != null) {
            SharedPreferences prefs = getContext().getSharedPreferences(
                    "StrategyFragment_" + strategyType, Context.MODE_PRIVATE);
            String symbol = prefs.getString("COIN_SYMBOL", null);
            String market = prefs.getString("COIN_MARKET", null);
            String name = prefs.getString("COIN_NAME", null);

            if (symbol != null && market != null) {
                CoinInfo restoredCoin = new CoinInfo();
                restoredCoin.setSymbol(symbol);
                restoredCoin.setMarket(market);
                if (name != null) {
                    restoredCoin.setKoreanName(name);
                }
                this.coinInfo = restoredCoin;
            }
        }
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
        exchangeRateManager = ExchangeRateManager.getInstance();

        adManager = AdManager.getInstance(requireContext());
        subscriptionManager = SubscriptionManager.getInstance(requireContext());

        // 저장된 코인 정보 복원
        restoreCurrentCoinInfo();
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

        ImageButton btnInfoDialog = view.findViewById(R.id.btnInfoDialog);
        btnInfoDialog.setOnClickListener(v -> showAnalysisInfoDialog());
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
                title = getString(R.string.short_term_strategy_title);
                titleColor = Color.parseColor("#4CAF50"); // 녹색
                emoji = "⚡"; // 번개 이모지
                break;
            case STRATEGY_MID_TERM:
                title = getString(R.string.mid_term_strategy_title);
                titleColor = Color.parseColor("#2196F3"); // 파란색
                emoji = "📈"; // 차트 이모지
                break;
            case STRATEGY_LONG_TERM:
                title = getString(R.string.long_term_strategy_title);
                titleColor = Color.parseColor("#9C27B0"); // 보라색
                emoji = "🔮"; // 수정구슬 이모지
                break;
            default:
                title = getString(R.string.default_strategy_title);
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
                    String targetLabel = String.format(Locale.getDefault(),
                            getString(R.string.target_price_format), i + 1, displayPrice);

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
                tvTargetPrice.setText(getString(R.string.no_target_prices));
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
                tvStopLoss.setText(getString(R.string.no_stop_loss));
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
                tvRiskReward.setText(getString(R.string.no_information));
            }

            // 전략 설명 표시
            if (strategy.getExplanation() != null && !strategy.getExplanation().isEmpty()) {
                String explanation = highlightStrategyText(strategy.getExplanation());
                tvStrategyDetail.setText(Html.fromHtml(explanation, Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvStrategyDetail.setText(getString(R.string.no_strategy_description));
            }
        } else {
            // 전략 데이터 없음
            tvTargetPrice.setText(getString(R.string.no_data));
            tvStopLoss.setText(getString(R.string.no_data));
            tvRiskReward.setText(getString(R.string.no_data));
            tvStrategyDetail.setText(getString(R.string.no_data));
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
                tvTargetPrice.setText(getString(R.string.masked_content));
                tvStopLoss.setText(getString(R.string.masked_content));
                tvRiskReward.setText(getString(R.string.masked_content_short));
                tvStrategyDetail.setText(getString(R.string.masked_content));

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

    private void showAnalysisInfoDialog() {
        if (getContext() == null) return;

        // 다이얼로그 레이아웃 인플레이트
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_analysis_info, null);

        // 다이얼로그 생성
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // 확인 버튼 클릭 리스너
        Button btnOk = dialogView.findViewById(R.id.btnOk);
        btnOk.setOnClickListener(v -> dialog.dismiss());

        // 다이얼로그 표시
        dialog.show();

        // 다이얼로그 크기 조정 (선택사항)
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }


    // 광고 대화상자 표시
    private void showAdDialog() {
        if (getActivity() == null) return;

        if (coinInfo == null) {
            Log.e("StrategyFragment", "coinInfo가 null입니다");
            Toast.makeText(getContext(), "코인 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String symbol = coinInfo.getSymbol();
        String displayName = coinInfo.getDisplayName();

        if (symbol == null) {
            Log.e("StrategyFragment", "coinInfo.symbol이 null입니다");
            Toast.makeText(getContext(), "코인 심볼 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        AdViewDialog dialog = AdViewDialog.newInstance(symbol, displayName != null ? displayName : symbol);

        dialog.setCompletionListener(coinSymbol -> {
            // 광고 시청 완료 - UI 업데이트
            updateContentAccessUI();

            // AnalysisFragment에도 UI 업데이트 알림
            Fragment parentFragment = getParentFragment();
            if (parentFragment instanceof AnalysisFragment) {
                ((AnalysisFragment) parentFragment).refreshAllUIs();
            }
        });

        dialog.show(getParentFragmentManager(), "ad_dialog");
    }

    // 콘텐츠 접근 권한 UI 업데이트
    public void updateContentAccessUI() {
        // View가 아직 생성되지 않았으면 아무것도 하지 않고 리턴
        if (getView() == null) {
            // Context 체크 추가
            if (isAdded()) {
                Log.d("StrategyFragment", getString(R.string.view_not_created_log));
            } else {
                Log.d("StrategyFragment", "updateContentAccessUI: View is not created yet");
            }
            return;
        }

        // UI 요소들이 null인지 확인
        if (blurOverlay == null || pixelatedOverlay == null || additionalBlurLayer == null ||
                contentArea == null || btnSubscribe == null || btnWatchAd == null || tvAdStatus == null) {
            // Context 체크 추가
            if (isAdded()) {
                Log.d("StrategyFragment", getString(R.string.ui_elements_null_log));
            } else {
                Log.d("StrategyFragment", "updateContentAccessUI: Some UI elements are null");
            }
            return;
        }

        // coinInfo 확인 추가
        if (coinInfo == null) {
            // 문자열 리소스 사용

            // Context 체크 추가
            if (isAdded()) {
                Log.w("StrategyFragment", getString(R.string.update_content_access_ui_warning));
            } else {
                Log.w("StrategyFragment", "updateContentAccessUI: coinInfo is null");
            }

            Log.w("StrategyFragment", getString(R.string.update_content_access_ui_warning));
            // coinInfo가 null인 경우 기본 UI 상태 설정 (구독자가 아닌 상태로 간주)
            blurOverlay.setVisibility(View.VISIBLE);
            pixelatedOverlay.setVisibility(View.VISIBLE);
            additionalBlurLayer.setVisibility(View.VISIBLE);
            contentArea.setAlpha(0.5f);
            btnSubscribe.setVisibility(View.VISIBLE);
            btnWatchAd.setVisibility(View.VISIBLE);
            tvAdStatus.setVisibility(View.GONE);
            return;
        }

        boolean isSubscribed = subscriptionManager.isSubscribed();
        boolean hasAdPermission = false;
        boolean isPremiumCoin = false;

        // coinInfo 확인 추가
        if (coinInfo != null && coinInfo.getSymbol() != null) {
            hasAdPermission = adManager.hasActiveAdPermission(coinInfo.getSymbol());
            isPremiumCoin = coinInfo.isPremium(); // 여기서 프리미엄 플래그 사용
        }

        if (!isSubscribed && !hasAdPermission) {
            // 마스킹 처리 - 언어별 리소스 사용
            tvTargetPrice.setText(getString(R.string.masked_content));
            tvStopLoss.setText(getString(R.string.masked_content));
            tvRiskReward.setText(getString(R.string.masked_content_short));
            tvStrategyDetail.setText(getString(R.string.masked_strategy_content));

            // 첫 번째 매수 단계만 블러 처리
            if (strategy != null && strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty()) {
                displayFirstBuyStepWithBlur(layoutBuySteps, strategy.getBuySteps().get(0));
            }
        }

        if (isSubscribed || hasAdPermission) {
            // 구독자이거나 광고 시청한 경우 콘텐츠 표시
            blurOverlay.setVisibility(View.GONE);
            pixelatedOverlay.setVisibility(View.GONE);
            additionalBlurLayer.setVisibility(View.GONE);
            contentArea.setAlpha(1.0f);
            btnSubscribe.setVisibility(View.GONE);
            btnWatchAd.setVisibility(View.GONE);

            // 구독자가 아니고 광고 시청한 경우 남은 시간 표시
            if (!isSubscribed && hasAdPermission) {
                int remainingMinutes = adManager.getRemainingMinutes(coinInfo.getSymbol());
                tvAdStatus.setVisibility(View.VISIBLE);
                tvAdStatus.setText(getString(R.string.ad_remaining_minutes_format, remainingMinutes));
            } else {
                tvAdStatus.setVisibility(View.GONE);
            }

            // 중요: 전략 데이터가 있으면 실제 내용 표시
            if (strategy != null) {
                // 매수 단계 등 실제 데이터 표시
                if (strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty() && layoutBuySteps != null) {
                    displayBuySteps(layoutBuySteps, strategy.getBuySteps());
                }

                // 목표가 표시
                updateTargetPrices();

                // 손절매 라인 표시
                updateStopLoss();

                // 리스크 보상 비율 표시
                updateRiskReward();

                // 전략 설명 표시
                updateStrategyDetail();
            }
        } else {
            // 구독자도 아니고 광고도 안 본 경우 콘텐츠 가림
            blurOverlay.setVisibility(View.VISIBLE);
            pixelatedOverlay.setVisibility(View.VISIBLE);
            additionalBlurLayer.setVisibility(View.VISIBLE);
            contentArea.setAlpha(0.5f);
            btnSubscribe.setVisibility(View.VISIBLE);
            btnWatchAd.setVisibility(isPremiumCoin ? View.GONE : View.VISIBLE);
            tvAdStatus.setVisibility(View.GONE);

            // 버튼 위치 조정 - btnWatchAd의 margin을 설정
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) btnWatchAd.getLayoutParams();
            if (params != null) {
                params.topMargin = (int) (80 * getResources().getDisplayMetrics().density); // 80dp
                btnWatchAd.setLayoutParams(params);
            }

            // 콘텐츠 마스킹 처리 - 리소스 사용으로 변경
            tvTargetPrice.setText(getString(R.string.masked_content));
            tvStopLoss.setText(getString(R.string.masked_content));
            tvRiskReward.setText(getString(R.string.masked_content_short));
            tvStrategyDetail.setText(getString(R.string.masked_content));

            // 콘텐츠 마스킹 코드...
        }
    }

    // 추가 메서드: 데이터 업데이트를 위한 개별 함수들
    private void updateTargetPrices() {
        if (tvTargetPrice == null || strategy == null) return;

        if (strategy.getTargetPrices() != null && !strategy.getTargetPrices().isEmpty()) {
            StringBuilder targetPrices = new StringBuilder();
            for (int i = 0; i < strategy.getTargetPrices().size(); i++) {
                // 기존 목표가 업데이트 코드...
                double targetPrice = strategy.getTargetPrices().get(i);
                // 포맷팅 코드...
                String targetLabel = String.format(getString(R.string.target_price_format),
                        i + 1, String.format("%s%.2f", currencySymbol, targetPrice));
                targetPrices.append(targetLabel);
                if (i < strategy.getTargetPrices().size() - 1) {
                    targetPrices.append("\n");
                }
            }
            tvTargetPrice.setText(targetPrices.toString());
        } else {
            tvTargetPrice.setText(getString(R.string.no_target_prices));
        }
    }

    private void updateStopLoss() {
        if (tvStopLoss == null || strategy == null) return;

        if (strategy.getStopLoss() > 0) {
            tvStopLoss.setText(String.format("%s%.2f", currencySymbol, strategy.getStopLoss()));
        } else {
            tvStopLoss.setText(getString(R.string.no_stop_loss));
        }
    }

    private void updateRiskReward() {
        if (tvRiskReward == null || strategy == null) return;

        if (strategy.getRiskRewardRatio() > 0) {
            tvRiskReward.setText(String.format("%.1f:1", strategy.getRiskRewardRatio()));
        } else {
            tvRiskReward.setText(getString(R.string.no_information));
        }
    }

    private void updateStrategyDetail() {
        if (tvStrategyDetail == null || strategy == null) return;

        if (strategy.getExplanation() != null && !strategy.getExplanation().isEmpty()) {
            String explanation = highlightStrategyText(strategy.getExplanation());
            tvStrategyDetail.setText(Html.fromHtml(explanation, Html.FROM_HTML_MODE_LEGACY));
        } else {
            tvStrategyDetail.setText(getString(R.string.no_strategy_description));
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

        // 진입점 표시 - 언어 리소스 사용
        String emoji = "1️⃣ ";
        String entryPoint = getString(R.string.entry_point); // 언어별 리소스 사용
        String maskedPrice = "****"; // 완전히 마스킹 처리
        String title = emoji + entryPoint + ": " + maskedPrice;

        tvBuyStepTitle.setText(title);
        tvBuyStepTitle.setTextColor(Color.parseColor("#4CAF50"));

        tvBuyStepPercentage.setVisibility(View.GONE);

        // 마스킹된 콘텐츠 - 언어별 리소스 사용
        tvBuyStepDescription.setText(getString(R.string.masked_strategy_content));

        // 아이템 투명도 조정
        itemView.setAlpha(0.3f);

        container.addView(itemView);

        // "더 보기" 텍스트 추가 - 언어별 리소스 사용
        TextView tvMore = new TextView(getContext());
        tvMore.setText(getString(R.string.see_more_strategies));
        tvMore.setTextSize(12); // 글자 크기 줄임
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

        String currentLanguage = getResources().getConfiguration().locale.getLanguage();
        boolean isKorean = "ko".equals(currentLanguage);


        if (buySteps == null || buySteps.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText(getString(R.string.no_appropriate_buy_time));
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

            // 한국어일 때만 달러 가격에 원화 추가
            if (isKorean && "$".equals(currencySymbol) && exchangeRateManager.getUsdToKrwRate() > 0) {
                double krwPrice = exchangeRateManager.convertUsdToKrw(price);
                formattedPrice = String.format("%s (₩%,.0f)", formattedUsdPrice, krwPrice);
            } else {
                formattedPrice = formattedUsdPrice;
            }

            String title = emoji + String.format(getString(R.string.entry_point_format), formattedPrice);
            tvBuyStepTitle.setText(title);
            tvBuyStepTitle.setTextColor(titleColor);

            tvBuyStepPercentage.setVisibility(View.GONE);

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