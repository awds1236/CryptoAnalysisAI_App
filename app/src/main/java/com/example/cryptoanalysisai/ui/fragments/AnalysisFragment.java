package com.example.cryptoanalysisai.ui.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.cryptoanalysisai.MainActivity;
import com.example.cryptoanalysisai.R;
import com.example.cryptoanalysisai.api.BinanceApiService;
import com.example.cryptoanalysisai.api.RetrofitClient;
import com.example.cryptoanalysisai.databinding.FragmentAnalysisBinding;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.BinanceTicker;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.services.AnalysisApiService;
import com.example.cryptoanalysisai.services.ExchangeRateManager;
import com.example.cryptoanalysisai.services.SubscriptionManager;
import com.example.cryptoanalysisai.ui.activities.SubscriptionActivity;
import com.example.cryptoanalysisai.utils.Constants;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.activity.OnBackPressedCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AnalysisFragment extends Fragment {

    private static final String TAG = "AnalysisFragment";
    private static final String ARG_COIN_INFO = "arg_coin_info";
    private static final String ARG_EXCHANGE_TYPE = "arg_exchange_type";

    private FragmentAnalysisBinding binding;
    private CoinInfo coinInfo;
    private ExchangeType exchangeType = ExchangeType.BINANCE;
    private AnalysisResult analysisResult;
    private AnalysisApiService analysisApiService;

    // 전략 프래그먼트
    private StrategyFragment shortTermFragment;
    private StrategyFragment midTermFragment;
    private StrategyFragment longTermFragment;
    private StrategiesAdapter strategiesAdapter;
    private SubscriptionManager subscriptionManager;

    // 새로 추가한 UI 요소 참조
    private TextView tvCrossSignal;
    private TextView tvBuySellRatio;

    // 최근 가격 변동 추적을 위한 변수
    private double lastPrice = 0;

    public AnalysisFragment() {
        // 기본 생성자
    }

    public static AnalysisFragment newInstance(CoinInfo coinInfo, ExchangeType exchangeType) {
        AnalysisFragment fragment = new AnalysisFragment();
        Bundle args = new Bundle();

        if (coinInfo != null) {
            args.putString(ARG_COIN_INFO, coinInfo.getMarket());
        }

        args.putString(ARG_EXCHANGE_TYPE, exchangeType.getCode());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            String market = getArguments().getString(ARG_COIN_INFO);
            if (market != null) {
                coinInfo = new CoinInfo();
                coinInfo.setMarket(market);
            }

            String exchangeCode = getArguments().getString(ARG_EXCHANGE_TYPE);
            if (exchangeCode != null) {
                exchangeType = ExchangeType.fromCode(exchangeCode);
            }

            // 항상 바이낸스로 고정
            exchangeType = ExchangeType.BINANCE;
        }

        analysisApiService = AnalysisApiService.getInstance();
        subscriptionManager = SubscriptionManager.getInstance(requireContext()); // 이 줄 추가

        // 전략 프래그먼트 초기화
        shortTermFragment = StrategyFragment.newInstance(StrategyFragment.STRATEGY_SHORT_TERM, "$");
        midTermFragment = StrategyFragment.newInstance(StrategyFragment.STRATEGY_MID_TERM, "$");
        longTermFragment = StrategyFragment.newInstance(StrategyFragment.STRATEGY_LONG_TERM, "$");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAnalysisBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 전략 탭 설정
        setupStrategyTabs();

        // 분석 버튼 클릭 리스너 설정
        binding.btnStartAnalysis.setOnClickListener(v -> {
            if (coinInfo != null && coinInfo.getMarket() != null) {
                loadAnalysisFromApi();
            } else {
                Toast.makeText(getContext(), "코인을 먼저 선택해주세요", Toast.LENGTH_SHORT).show();
            }
        });

        // UI 초기화
        if (coinInfo != null && coinInfo.getMarket() != null) {
            updateCoin(coinInfo, exchangeType);
        } else {
            binding.tvCoinTitle.setText("코인을 선택해주세요");
            binding.progressAnalysis.setVisibility(View.GONE);
        }

        // 새로 추가한 TextView 초기화
        tvCrossSignal = view.findViewById(R.id.tvCrossSignal);
        tvBuySellRatio = view.findViewById(R.id.tvBuySellRatio);

        // onViewCreated 메서드에서 다음 코드를 찾아서
        binding.btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                // 코인 목록 탭으로 이동
                ((MainActivity)getActivity()).navigateToCoinsTab();
            }
        });

        // 위 코드를 다음으로 바꾸세요
        // 화면에서 뒤로가기 버튼 숨기기
                binding.btnBack.setVisibility(View.GONE);


        // 기술적 분석 구독 버튼 설정
        binding.btnTechnicalSubscribe.setOnClickListener(v -> {
            // 구독 화면으로 이동
            Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * 전략 탭 설정
     */
    private void setupStrategyTabs() {
        strategiesAdapter = new StrategiesAdapter(this);
        binding.viewPagerStrategy.setAdapter(strategiesAdapter);

        new TabLayoutMediator(binding.tabsStrategy, binding.viewPagerStrategy, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("단기 (24시간)");
                    break;
                case 1:
                    tab.setText("중기 (1주일)");
                    break;
                case 2:
                    tab.setText("장기 (1개월)");
                    break;
            }
        }).attach();
    }

    /**
     * 코인 정보 업데이트 (코인 선택 시 호출)
     */
    public void updateCoin(CoinInfo coinInfo, ExchangeType exchangeType) {
        this.coinInfo = coinInfo;
        this.exchangeType = exchangeType;

        if (binding != null && coinInfo != null) {
            // 타이틀 설정
            binding.tvCoinTitle.setText(coinInfo.getDisplayName() != null ?
                    coinInfo.getDisplayName() + " (" + coinInfo.getSymbol() + ")" : coinInfo.getMarket());

            // 거래소 정보 설정
            binding.tvExchangeInfo.setText("거래소: " + exchangeType.getDisplayName() +
                    " / 통화단위: " + (exchangeType == ExchangeType.UPBIT ? "원" : "달러(USD)"));

            // AWS Lambda API에서 분석 결과 로드
            loadAnalysisFromApi();

            // 현재 가격 정보 업데이트
            updatePrice();
        }
    }

    /**
     * 현재 가격 정보 업데이트 (3초마다 호출)
     */
    public void updatePrice() {
        if (coinInfo == null || coinInfo.getMarket() == null || !isAdded()) return;

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.getTicker(coinInfo.getMarket()).enqueue(new Callback<BinanceTicker>() {
            @Override
            public void onResponse(@NonNull Call<BinanceTicker> call, @NonNull Response<BinanceTicker> response) {
                if (!isAdded() || binding == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    BinanceTicker ticker = response.body();
                    double newPrice = ticker.getPrice();

                    // 이전 가격과 현재 가격 비교하여 변화 표시
                    final boolean priceChanged = lastPrice > 0 && lastPrice != newPrice;
                    final boolean priceIncreased = newPrice > lastPrice;

                    lastPrice = newPrice;

                    // 가격이 변경된 경우에만 업데이트
                    if (newPrice != coinInfo.getCurrentPrice() || priceChanged) {
                        coinInfo.setCurrentPrice(newPrice);

                        // 24시간 변화율도 갱신
                        apiService.get24hTicker(coinInfo.getMarket()).enqueue(new Callback<BinanceTicker>() {
                            @Override
                            public void onResponse(@NonNull Call<BinanceTicker> call, @NonNull Response<BinanceTicker> response) {
                                if (!isAdded() || binding == null) return;

                                if (response.isSuccessful() && response.body() != null) {
                                    BinanceTicker ticker24h = response.body();
                                    coinInfo.setPriceChange(ticker24h.getPriceChangePercent() / 100.0);

                                    // UI 갱신 - 가격 변화에 따른 색상 표시 추가
                                    updatePriceUI(priceChanged, priceIncreased);
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                                Log.e(TAG, "24시간 가격 변화 정보 로드 실패: " + t.getMessage());
                            }
                        });
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                Log.e(TAG, "현재가 정보 로드 실패: " + t.getMessage());
            }
        });
    }

    /**
     * 가격 정보 UI 업데이트 - 가격 변화 애니메이션 추가
     */
    private void updatePriceUI(boolean priceChanged, boolean priceIncreased) {
        if (binding == null || coinInfo == null) return;

        // 가격 변화 표시를 위한 색상 및 애니메이션 설정
        int textColor = Color.WHITE;
        String pricePrefix = "";

        if (priceChanged) {
            if (priceIncreased) {
                textColor = Color.GREEN;
                pricePrefix = "▲ ";
            } else {
                textColor = Color.RED;
                pricePrefix = "▼ ";
            }
        }

        // 버튼의 텍스트 업데이트 - 현재 가격 표시
        String priceText = "분석 결과 불러오기 - 현재가: " + pricePrefix +
                coinInfo.getFormattedPrice() + " (" + coinInfo.getFormattedPriceChange() + ")";

        binding.btnStartAnalysis.setText(priceText);

        // 분석 결과가 있을 경우 지지선/저항선과 현재가 비교 표시
        if (analysisResult != null && analysisResult.getTechnicalAnalysis() != null) {
            updatePriceComparisonWithLevels();
        }
    }

    /**
     * 현재가와 지지선/저항선 비교 및 표시
     */
    private void updatePriceComparisonWithLevels() {
        if (binding == null || coinInfo == null || analysisResult == null) return;

        AnalysisResult.TechnicalAnalysis technicalAnalysis = analysisResult.getTechnicalAnalysis();
        if (technicalAnalysis == null) return;

        double currentPrice = coinInfo.getCurrentPrice();
        List<Double> supportLevels = technicalAnalysis.getSupportLevels();
        List<Double> resistanceLevels = technicalAnalysis.getResistanceLevels();

        // 가장 가까운 지지선과 저항선 찾기
        double closestSupport = findClosestLevel(supportLevels, currentPrice, true);
        double closestResistance = findClosestLevel(resistanceLevels, currentPrice, false);

        if (closestSupport > 0 && closestResistance > 0) {
            // 지지선과 저항선 사이의 위치 계산 (0: 지지선, 1: 저항선)
            double range = closestResistance - closestSupport;
            double position = (currentPrice - closestSupport) / range;
            position = Math.max(0, Math.min(1, position)); // 0~1 사이로 제한

            // 현재가가 지지선/저항선 범위 내 어디에 위치하는지 표시
            StringBuilder positionText = new StringBuilder();
            positionText.append("<b>현재가 위치: </b>");

            if (position < 0.3) {
                positionText.append("<font color='#4CAF50'>지지선 가까움</font> (");
            } else if (position > 0.7) {
                positionText.append("<font color='#F44336'>저항선 가까움</font> (");
            } else {
                positionText.append("<font color='#FFC107'>중간 구간</font> (");
            }

            // 저항선까지 % 계산
            double percentToResistance = ((closestResistance - currentPrice) / currentPrice) * 100;
            // 지지선까지 % 계산
            double percentToSupport = ((currentPrice - closestSupport) / currentPrice) * 100;

            positionText.append(String.format(Locale.US, "저항 %.2f%% ↑, 지지 %.2f%% ↓)",
                    percentToResistance, percentToSupport));

            Spanned formattedText = Html.fromHtml(positionText.toString(), Html.FROM_HTML_MODE_LEGACY);
            binding.tvPricePosition.setText(formattedText);
            binding.tvPricePosition.setVisibility(View.VISIBLE);
        } else {
            binding.tvPricePosition.setVisibility(View.GONE);
        }
    }

    /**
     * 가장 가까운 지지선/저항선 찾기
     * @param levels 지지선 또는 저항선 목록
     * @param currentPrice 현재 가격
     * @param isSupport true면 지지선(현재가보다 낮은), false면 저항선(현재가보다 높은) 찾기
     * @return 가장 가까운 레벨 가격
     */
    private double findClosestLevel(List<Double> levels, double currentPrice, boolean isSupport) {
        if (levels == null || levels.isEmpty()) return 0;

        double closest = 0;
        double minDiff = Double.MAX_VALUE;

        for (Double level : levels) {
            // 지지선은 현재가보다 낮아야 함
            if (isSupport && level >= currentPrice) continue;
            // 저항선은 현재가보다 높아야 함
            if (!isSupport && level <= currentPrice) continue;

            double diff = Math.abs(currentPrice - level);
            if (diff < minDiff) {
                minDiff = diff;
                closest = level;
            }
        }

        return closest;
    }

    /**
     * AWS Lambda API에서 분석 결과 로드
     */
    private void loadAnalysisFromApi() {
        if (binding == null || coinInfo == null) return;

        binding.progressAnalysis.setVisibility(View.VISIBLE);

        // 분석 버튼 비활성화
        binding.btnStartAnalysis.setEnabled(false);
        binding.btnStartAnalysis.setText("분석 데이터 로딩 중...");

        analysisApiService.getLatestAnalysis(coinInfo.getSymbol(),
                new AnalysisApiService.OnAnalysisRetrievedListener() {
                    @Override
                    public void onAnalysisRetrieved(AnalysisResult result) {
                        if (getActivity() == null || binding == null) return;

                        analysisResult = result;

                        // UI 업데이트
                        getActivity().runOnUiThread(() -> {
                            updateAnalysisUI();
                            binding.progressAnalysis.setVisibility(View.GONE);
                            binding.btnStartAnalysis.setEnabled(true);
                            updatePriceUI(false, false);
                        });
                    }

                    @Override
                    public void onNoAnalysisFound() {
                        if (getActivity() == null || binding == null) return;

                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "저장된 분석 결과가 없습니다", Toast.LENGTH_SHORT).show();
                            binding.progressAnalysis.setVisibility(View.GONE);
                            binding.btnStartAnalysis.setEnabled(true);
                            binding.btnStartAnalysis.setText("분석 결과 없음 - 다시 시도");
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null || binding == null) return;

                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "분석 결과 로드 실패: " + errorMessage, Toast.LENGTH_SHORT).show();
                            binding.progressAnalysis.setVisibility(View.GONE);
                            binding.btnStartAnalysis.setEnabled(true);
                            binding.btnStartAnalysis.setText("다시 시도");
                        });
                    }
                });
    }

    /**
     * 분석 결과 설정
     */
    public void setAnalysisResult(AnalysisResult result) {
        this.analysisResult = result;
        updateAnalysisUI();
    }

    /**
     * 분석 결과로 UI 업데이트 - 강화된 버전
     */
    private void updateAnalysisUI() {
        if (binding == null || analysisResult == null) return;

        // 구독 상태 확인
        boolean isSubscribed = subscriptionManager.isSubscribed();

        // 분석 시간 표시
        if (analysisResult.getTimestamp() > 0) {
            Date analysisDate = new Date(analysisResult.getTimestamp());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());

            // 분석 시간이 얼마나 지났는지 표시 (예: "10분 전")
            String timeAgo = DateUtils.getRelativeTimeSpanString(
                    analysisResult.getTimestamp(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            ).toString();

            binding.tvAnalysisTime.setText(timeAgo);
            binding.tvAnalysisTime.setVisibility(View.VISIBLE);
        } else {
            binding.tvAnalysisTime.setVisibility(View.GONE);
        }

        // 분석 요약 - HTML 형식으로 키워드 강조
        String summary = analysisResult.getSummary();
        if (summary != null && !summary.isEmpty()) {
            // 키워드에 따라 색상 강조
            summary = highlightKeywords(summary);
            binding.tvAnalysisSummary.setText(Html.fromHtml(summary, Html.FROM_HTML_MODE_LEGACY));
        } else {
            binding.tvAnalysisSummary.setText("분석 요약 정보가 없습니다.");
        }

        // 매수/매도 추천
        AnalysisResult.Recommendation recommendation = analysisResult.getRecommendation();
        if (recommendation != null) {
            // 추천 타입에 따라 색상 변경
            Constants.RecommendationType recommendType = Constants.RecommendationType.fromString(recommendation.getRecommendation());

            // 강조된 추천 메시지 구성
            String recommendText = "<b>" + recommendType.getDisplayName() + " 추천</b>";
            if (recommendation.getConfidence() >= 8) {
                recommendText += " (높은 신뢰도)";
            }

            binding.tvRecommendation.setText(Html.fromHtml(recommendText, Html.FROM_HTML_MODE_LEGACY));
            binding.tvRecommendation.setTextColor(recommendType.getColor());

            // 확률 막대 업데이트
            int buyProgress = (int) Math.round(recommendation.getBuyProbability());
            binding.progressProbability.setProgress(buyProgress);

            // 확률 텍스트 업데이트 - 시각적 강조
            StringBuilder probText = new StringBuilder();
            probText.append("<b>매수: </b><font color='#4CAF50'>")
                    .append(String.format("%.1f%%", recommendation.getBuyProbability()))
                    .append("</font> / <b>매도: </b><font color='#F44336'>")
                    .append(String.format("%.1f%%", recommendation.getSellProbability()))
                    .append("</font>");

            binding.tvProbabilityText.setText(Html.fromHtml(probText.toString(), Html.FROM_HTML_MODE_LEGACY));

            // 신뢰도 업데이트
            binding.ratingBar.setRating((float) recommendation.getConfidence() / 2); // 0-10 -> 0-5 변환
            binding.tvConfidenceValue.setText(String.format("%.1f/10", recommendation.getConfidence()));

            // 신뢰도 수준에 따른 색상 표시
            if (recommendation.getConfidence() >= 8) {
                binding.tvConfidenceValue.setTextColor(Color.parseColor("#4CAF50")); // 높음 - 녹색
            } else if (recommendation.getConfidence() >= 5) {
                binding.tvConfidenceValue.setTextColor(Color.parseColor("#FFC107")); // 중간 - 노란색
            } else {
                binding.tvConfidenceValue.setTextColor(Color.parseColor("#F44336")); // 낮음 - 빨간색
            }

            // 근거 업데이트 - 키워드 강조
            String reason = recommendation.getReason();
            if (reason != null && !reason.isEmpty()) {
                reason = highlightKeywords(reason);
                binding.tvReason.setText(Html.fromHtml(reason, Html.FROM_HTML_MODE_LEGACY));
            } else {
                binding.tvReason.setText("");
            }
        }

        // 각 전략 프래그먼트 데이터 설정
        updateStrategyFragments();

        // 시간별 전망 - 키워드 강조
        AnalysisResult.Outlook outlook = analysisResult.getOutlook();
        if (outlook != null) {
            // 단기 전망 - 키워드 강조
            String shortTerm = outlook.getShortTerm();
            if (shortTerm != null && !shortTerm.isEmpty()) {
                shortTerm = highlightKeywords(shortTerm);
                binding.tvShortTerm.setText(Html.fromHtml(shortTerm, Html.FROM_HTML_MODE_LEGACY));
            } else {
                binding.tvShortTerm.setText("정보 없음");
            }

            // 중기 전망 - 키워드 강조
            String midTerm = outlook.getMidTerm();
            if (midTerm != null && !midTerm.isEmpty()) {
                midTerm = highlightKeywords(midTerm);
                binding.tvMidTerm.setText(Html.fromHtml(midTerm, Html.FROM_HTML_MODE_LEGACY));
            } else {
                binding.tvMidTerm.setText("정보 없음");
            }

            // 장기 전망 - 키워드 강조
            String longTerm = outlook.getLongTerm();
            if (longTerm != null && !longTerm.isEmpty()) {
                longTerm = highlightKeywords(longTerm);
                binding.tvLongTerm.setText(Html.fromHtml(longTerm, Html.FROM_HTML_MODE_LEGACY));
            } else {
                binding.tvLongTerm.setText("정보 없음");
            }
        }

        // 기술적 분석
        AnalysisResult.TechnicalAnalysis technicalAnalysis = analysisResult.getTechnicalAnalysis();

        // 구독자만 기술적 분석 내용을 볼 수 있도록 처리
        if (!isSubscribed) {
            // 기술적 분석 블러 처리
            binding.technicalBlurOverlay.setVisibility(View.VISIBLE);
            binding.technicalPixelatedOverlay.setVisibility(View.VISIBLE);
            binding.btnTechnicalSubscribe.setVisibility(View.VISIBLE); // 구독 버튼 표시

            // 콘텐츠 알파값 낮추기
            binding.cardTechnical.setAlpha(0.5f);

            // 콘텐츠 마스킹 처리
            binding.tvSupport.setText("**********");
            binding.tvResistance.setText("**********");
            binding.tvTrendStrength.setText("*****");
            binding.tvPattern.setText("**********");
            binding.tvCrossSignal.setText("*****");
            binding.tvBuySellRatio.setText("*****");

            // 이동평균선 신호 및 롱숏 비율 정보 마스킹 (있는 경우)
            if (binding.tvCrossSignal != null) {
                binding.tvCrossSignal.setText("*****");
            }
            if (binding.tvBuySellRatio != null) {
                binding.tvBuySellRatio.setText("*****");
            }
        } else {
            // 구독자인 경우 블러 처리 제거
            binding.technicalBlurOverlay.setVisibility(View.GONE);
            binding.technicalPixelatedOverlay.setVisibility(View.GONE);
            binding.btnTechnicalSubscribe.setVisibility(View.GONE);
            binding.cardTechnical.setAlpha(1.0f);

            // 실제 기술적 분석 내용 표시
            if (technicalAnalysis != null) {
                // 지지선 - 현재가와 비교
                if (technicalAnalysis.getSupportLevels() != null && !technicalAnalysis.getSupportLevels().isEmpty()) {
                    StringBuilder supportLevels = new StringBuilder();
                    String currencySymbol = analysisResult.getCurrencySymbol();
                    double currentPrice = coinInfo.getCurrentPrice();

                    for (int i = 0; i < technicalAnalysis.getSupportLevels().size(); i++) {
                        double supportLevel = technicalAnalysis.getSupportLevels().get(i);
                        if (i > 0) supportLevels.append(", ");

                        // 현재가와의 차이를 백분율로 계산
                        double percentDiff = ((currentPrice - supportLevel) / currentPrice) * 100;

                        // 현재가 대비 지지선 거리에 따라 색상 표시
                        String colorCode;
                        if (percentDiff < 3) {
                            colorCode = "#FFC107"; // 노란색 - 근접
                        } else if (percentDiff < 8) {
                            colorCode = "#4CAF50"; // 녹색 - 적당
                        } else {
                            colorCode = "#9E9E9E"; // 회색 - 먼 거리
                        }

                        supportLevels.append("<font color='")
                                .append(colorCode)
                                .append("'>")
                                .append(currencySymbol)
                                .append(String.format("%,.2f", supportLevel))
                                .append(" (↓")
                                .append(String.format("%.1f%%", percentDiff))
                                .append(")</font>");
                    }

                    binding.tvSupport.setText(Html.fromHtml(supportLevels.toString(), Html.FROM_HTML_MODE_LEGACY));
                }

                // 저항선 - 현재가와 비교
                if (technicalAnalysis.getResistanceLevels() != null && !technicalAnalysis.getResistanceLevels().isEmpty()) {
                    StringBuilder resistanceLevels = new StringBuilder();
                    String currencySymbol = analysisResult.getCurrencySymbol();
                    double currentPrice = coinInfo.getCurrentPrice();

                    for (int i = 0; i < technicalAnalysis.getResistanceLevels().size(); i++) {
                        double resistanceLevel = technicalAnalysis.getResistanceLevels().get(i);
                        if (i > 0) resistanceLevels.append(", ");

                        // 현재가와의 차이를 백분율로 계산
                        double percentDiff = ((resistanceLevel - currentPrice) / currentPrice) * 100;

                        // 현재가 대비 저항선 거리에 따라 색상 표시
                        String colorCode;
                        if (percentDiff < 3) {
                            colorCode = "#FFC107"; // 노란색 - 근접
                        } else if (percentDiff < 8) {
                            colorCode = "#F44336"; // 빨간색 - 적당
                        } else {
                            colorCode = "#9E9E9E"; // 회색 - 먼 거리
                        }

                        resistanceLevels.append("<font color='")
                                .append(colorCode)
                                .append("'>")
                                .append(currencySymbol)
                                .append(String.format("%,.2f", resistanceLevel))
                                .append(" (↑")
                                .append(String.format("%.1f%%", percentDiff))
                                .append(")</font>");
                    }

                    binding.tvResistance.setText(Html.fromHtml(resistanceLevels.toString(), Html.FROM_HTML_MODE_LEGACY));
                }

                // 추세 강도 - 시각적 표시 강화
                String trendStrength = technicalAnalysis.getTrendStrength();
                if (trendStrength != null && !trendStrength.isEmpty()) {
                    String colorCode;
                    String strengthText;

                    if ("강".equals(trendStrength)) {
                        colorCode = "#4CAF50"; // 녹색
                        strengthText = "강함 (🔥)";
                    } else if ("중".equals(trendStrength)) {
                        colorCode = "#FFC107"; // 노란색
                        strengthText = "중간 (➡️)";
                    } else {
                        colorCode = "#F44336"; // 빨간색
                        strengthText = "약함 (💧)";
                    }

                    binding.tvTrendStrength.setText(Html.fromHtml("<font color='" +
                            colorCode + "'><b>" + strengthText + "</b></font>", Html.FROM_HTML_MODE_LEGACY));
                } else {
                    binding.tvTrendStrength.setText("정보 없음");
                }

                // 주요 패턴 - 키워드 강조
                String pattern = technicalAnalysis.getPattern();
                if (pattern != null && !pattern.isEmpty()) {
                    pattern = highlightKeywords(pattern);
                    binding.tvPattern.setText(Html.fromHtml(pattern, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    binding.tvPattern.setText("정보 없음");
                }

                // 이동평균선 신호 표시 - 새로 추가 (있는 경우에만)
                if (binding.tvCrossSignal != null) {
                    String crossSignal = technicalAnalysis.getCrossSignal();
                    if (crossSignal != null && !crossSignal.isEmpty()) {
                        String displayText;
                        String colorCode;

                        switch (crossSignal) {
                            case "GOLDEN_CROSS":
                                displayText = "골든 크로스 (매수 신호) ⬆️";
                                colorCode = "#4CAF50"; // 녹색
                                break;
                            case "DEATH_CROSS":
                                displayText = "데드 크로스 (매도 신호) ⬇️";
                                colorCode = "#F44336"; // 빨간색
                                break;
                            default:
                                displayText = "없음 (중립) ↔️";
                                colorCode = "#FFC107"; // 노란색
                                break;
                        }

                        binding.tvCrossSignal.setText(Html.fromHtml("<font color='" + colorCode + "'><b>" +
                                displayText + "</b></font>", Html.FROM_HTML_MODE_LEGACY));
                    } else {
                        binding.tvCrossSignal.setText("데이터 없음");
                    }
                }

                // 롱:숏 비율 표시 - 새로 추가 (있는 경우에만)
                if (binding.tvBuySellRatio != null) {
                    double buySellRatio = technicalAnalysis.getBuySellRatio();
                    if (buySellRatio > 0) {
                        String displayText;
                        String colorCode;

                        // 비율에 따른 텍스트 및 색상 결정
                        if (buySellRatio > 0.65) {
                            displayText = "매수세 강함 (" + String.format("%.2f", buySellRatio * 100) + "%)";
                            colorCode = "#4CAF50"; // 녹색
                        } else if (buySellRatio < 0.35) {
                            displayText = "매도세 강함 (" + String.format("%.2f", (1 - buySellRatio) * 100) + "%)";
                            colorCode = "#F44336"; // 빨간색
                        } else {
                            displayText = "중립 (" + String.format("%.2f", buySellRatio * 100) + "%)";
                            colorCode = "#FFC107"; // 노란색
                        }

                        binding.tvBuySellRatio.setText(Html.fromHtml("<font color='" + colorCode + "'><b>" +
                                displayText + "</b></font>", Html.FROM_HTML_MODE_LEGACY));
                    } else {
                        binding.tvBuySellRatio.setText("데이터 없음");
                    }
                }
            }
        }

        // 위험 요소 - 시각적 강조
        if (analysisResult.getRiskFactors() != null && !analysisResult.getRiskFactors().isEmpty()) {
            StringBuilder riskFactors = new StringBuilder();

            for (int i = 0; i < analysisResult.getRiskFactors().size(); i++) {
                String risk = analysisResult.getRiskFactors().get(i);

                // 위험 요소 심각도에 따른 색상 코드 (예시)
                String colorCode = "#F44336"; // 기본 빨간색

                // 키워드 기반 중요도 판단 (예시)
                if (risk.toLowerCase().contains("급격한") ||
                        risk.toLowerCase().contains("심각한") ||
                        risk.toLowerCase().contains("충격")) {
                    colorCode = "#D32F2F"; // 더 진한 빨간색
                } else if (risk.toLowerCase().contains("가능성")) {
                    colorCode = "#FF9800"; // 주황색
                }

                riskFactors.append("<font color='")
                        .append(colorCode)
                        .append("'>⚠️ ")
                        .append(risk)
                        .append("</font>");

                if (i < analysisResult.getRiskFactors().size() - 1) {
                    riskFactors.append("<br><br>");
                }
            }

            binding.tvRiskFactors.setText(Html.fromHtml(riskFactors.toString(), Html.FROM_HTML_MODE_LEGACY));
        }

        // 현재가와 지지선/저항선 비교 업데이트
        updatePriceComparisonWithLevels();
    }
    /**
     * 텍스트에서 주요 키워드 강조 처리
     */
    private String highlightKeywords(String text) {
        if (text == null || text.isEmpty()) return "";

        // 상승/하락 관련 키워드
        text = text.replaceAll("(?i)\\b(상승|오름|증가|높아짐|돌파|성장|강세|급등)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(하락|내림|감소|낮아짐|약세|약화|급락|조정)\\b", "<font color='#F44336'><b>$1</b></font>");

        // 중립/횡보 관련 키워드
        text = text.replaceAll("(?i)\\b(횡보|보합|유지|중립|관망|안정)\\b", "<font color='#FFC107'><b>$1</b></font>");

        // 투자 전략 관련 키워드
        text = text.replaceAll("(?i)\\b(매수|진입|매집|분할매수)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(매도|매각|이익실현|손절)\\b", "<font color='#F44336'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(관망|대기|신중)\\b", "<font color='#FFC107'><b>$1</b></font>");

        // 가격 및 패턴 관련 키워드
        text = text.replaceAll("(?i)\\b(지지선|바닥|저점)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(저항선|천장|고점)\\b", "<font color='#F44336'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(패턴|추세|흐름|모멘텀|변동성)\\b", "<font color='#2196F3'><b>$1</b></font>");

        // 시간 관련 키워드
        text = text.replaceAll("(?i)\\b(단기|중기|장기)\\b", "<font color='#9C27B0'><b>$1</b></font>");

        return text;
    }

    /**
     * 전략 프래그먼트 업데이트
     */
    private void updateStrategyFragments() {
        if (analysisResult == null) return;

        String currencySymbol = analysisResult.getCurrencySymbol();

        // 단기 전략 업데이트
        if (shortTermFragment != null && analysisResult.getShortTermStrategy() != null) {
            shortTermFragment.setStrategy(analysisResult.getShortTermStrategy());
        }

        // 중기 전략 업데이트
        if (midTermFragment != null && analysisResult.getMidTermStrategy() != null) {
            midTermFragment.setStrategy(analysisResult.getMidTermStrategy());
        }

        // 장기 전략 업데이트
        if (longTermFragment != null && analysisResult.getLongTermStrategy() != null) {
            longTermFragment.setStrategy(analysisResult.getLongTermStrategy());
        }
    }

    /**
     * 전략 탭에 대한 어댑터
     */
    private class StrategiesAdapter extends FragmentStateAdapter {

        public StrategiesAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return shortTermFragment;
                case 1:
                    return midTermFragment;
                case 2:
                    return longTermFragment;
                default:
                    return shortTermFragment;
            }
        }

        @Override
        public int getItemCount() {
            return 3; // 단기, 중기, 장기
        }
    }
}