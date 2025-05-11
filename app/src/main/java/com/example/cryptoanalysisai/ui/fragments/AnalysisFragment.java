package com.example.cryptoanalysisai.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.cryptoanalysisai.api.BinanceApiService;
import com.example.cryptoanalysisai.api.RetrofitClient;
import com.example.cryptoanalysisai.databinding.FragmentAnalysisBinding;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.BinanceTicker;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.services.AwsRdsService;
import com.example.cryptoanalysisai.utils.Constants;

import java.util.List;

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
    private AwsRdsService awsRdsService;

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

        awsRdsService = AwsRdsService.getInstance();
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

        // 분석 버튼 클릭 리스너 설정
        binding.btnStartAnalysis.setOnClickListener(v -> {
            if (coinInfo != null && coinInfo.getMarket() != null) {
                loadAnalysisFromRds();
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
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
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

            // AWS RDS에서 분석 결과 로드
            loadAnalysisFromRds();

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

                    // 가격이 변경된 경우에만 업데이트
                    if (newPrice != coinInfo.getCurrentPrice()) {
                        coinInfo.setCurrentPrice(newPrice);

                        // 24시간 변화율도 갱신
                        apiService.get24hTicker(coinInfo.getMarket()).enqueue(new Callback<BinanceTicker>() {
                            @Override
                            public void onResponse(@NonNull Call<BinanceTicker> call, @NonNull Response<BinanceTicker> response) {
                                if (!isAdded() || binding == null) return;

                                if (response.isSuccessful() && response.body() != null) {
                                    BinanceTicker ticker24h = response.body();
                                    coinInfo.setPriceChange(ticker24h.getPriceChangePercent() / 100.0);

                                    // UI 갱신
                                    updatePriceUI();
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
     * 가격 정보 UI 업데이트
     */
    private void updatePriceUI() {
        if (binding == null || coinInfo == null) return;

        // 버튼의 텍스트 업데이트 - 현재 가격 표시
        binding.btnStartAnalysis.setText("분석 결과 불러오기 - 현재가: " + coinInfo.getFormattedPrice() +
                " (" + coinInfo.getFormattedPriceChange() + ")");
    }

    /**
     * AWS RDS에서 분석 결과 로드
     */
    private void loadAnalysisFromRds() {
        binding.progressAnalysis.setVisibility(View.VISIBLE);

        // 분석 버튼 비활성화
        binding.btnStartAnalysis.setEnabled(false);
        binding.btnStartAnalysis.setText("분석 데이터 로딩 중...");

        awsRdsService.getLatestAnalysis(coinInfo.getSymbol(), exchangeType.getCode(),
                new AwsRdsService.OnAnalysisRetrievedListener() {
                    @Override
                    public void onAnalysisRetrieved(AnalysisResult result) {
                        if (getActivity() == null || binding == null) return;

                        analysisResult = result;

                        // UI 업데이트
                        getActivity().runOnUiThread(() -> {
                            updateAnalysisUI();
                            binding.progressAnalysis.setVisibility(View.GONE);
                            binding.btnStartAnalysis.setEnabled(true);
                            updatePriceUI();
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
     * 분석 결과로 UI 업데이트
     */
    private void updateAnalysisUI() {
        if (binding == null || analysisResult == null) return;

        // 분석 요약
        binding.tvAnalysisSummary.setText(analysisResult.getSummary());

        // 매수/매도 추천
        AnalysisResult.Recommendation recommendation = analysisResult.getRecommendation();
        if (recommendation != null) {
            // 추천 타입에 따라 색상 변경
            Constants.RecommendationType recommendType = Constants.RecommendationType.fromString(recommendation.getRecommendation());
            binding.tvRecommendation.setText(recommendType.getDisplayName() + " 추천");
            binding.tvRecommendation.setTextColor(recommendType.getColor());

            // 확률 막대 업데이트
            int buyProgress = (int) Math.round(recommendation.getBuyProbability());
            binding.progressProbability.setProgress(buyProgress);

            // 확률 텍스트 업데이트
            binding.tvProbabilityText.setText(String.format("매수: %.1f%% / 매도: %.1f%%",
                    recommendation.getBuyProbability(), recommendation.getSellProbability()));

            // 신뢰도 업데이트
            binding.ratingBar.setRating((float) recommendation.getConfidence() / 2); // 0-10 -> 0-5 변환

            // 근거 업데이트
            binding.tvReason.setText(recommendation.getReason());
        }

        // 매매 전략
        AnalysisResult.Strategy strategy = analysisResult.getStrategy();
        if (strategy != null) {
            // 수익실현 목표가
            if (strategy.getTargetPrices() != null && !strategy.getTargetPrices().isEmpty()) {
                StringBuilder targetPrices = new StringBuilder();
                String currencySymbol = analysisResult.getCurrencySymbol();

                for (int i = 0; i < strategy.getTargetPrices().size(); i++) {
                    if (i > 0) targetPrices.append(", ");
                    targetPrices.append(currencySymbol).append(String.format("%,.0f", strategy.getTargetPrices().get(i)));
                }

                binding.tvTargetPrice.setText(targetPrices.toString());
            }

            // 손절매 라인
            binding.tvStopLoss.setText(analysisResult.getCurrencySymbol() +
                    String.format("%,.0f", strategy.getStopLoss()));

            // 리스크 대비 보상 비율
            binding.tvRiskReward.setText(String.format("%.1f", strategy.getRiskRewardRatio()) + ":1");

            // 전략 설명
            binding.tvStrategyDetail.setText(strategy.getExplanation());
        }

        // 시간별 전망
        AnalysisResult.Outlook outlook = analysisResult.getOutlook();
        if (outlook != null) {
            // 단기 전망
            binding.tvShortTerm.setText(outlook.getShortTerm());

            // 중기 전망
            binding.tvMidTerm.setText(outlook.getMidTerm());

            // 장기 전망
            binding.tvLongTerm.setText(outlook.getLongTerm());
        }

        // 기술적 분석
        AnalysisResult.TechnicalAnalysis technicalAnalysis = analysisResult.getTechnicalAnalysis();
        if (technicalAnalysis != null) {
            // 지지선
            if (technicalAnalysis.getSupportLevels() != null && !technicalAnalysis.getSupportLevels().isEmpty()) {
                StringBuilder supportLevels = new StringBuilder();
                String currencySymbol = analysisResult.getCurrencySymbol();

                for (int i = 0; i < technicalAnalysis.getSupportLevels().size(); i++) {
                    if (i > 0) supportLevels.append(", ");
                    supportLevels.append(currencySymbol)
                            .append(String.format("%,.0f", technicalAnalysis.getSupportLevels().get(i)));
                }

                binding.tvSupport.setText(supportLevels.toString());
            }

            // 저항선
            if (technicalAnalysis.getResistanceLevels() != null && !technicalAnalysis.getResistanceLevels().isEmpty()) {
                StringBuilder resistanceLevels = new StringBuilder();
                String currencySymbol = analysisResult.getCurrencySymbol();

                for (int i = 0; i < technicalAnalysis.getResistanceLevels().size(); i++) {
                    if (i > 0) resistanceLevels.append(", ");
                    resistanceLevels.append(currencySymbol)
                            .append(String.format("%,.0f", technicalAnalysis.getResistanceLevels().get(i)));
                }

                binding.tvResistance.setText(resistanceLevels.toString());
            }

            // 추세 강도
            binding.tvTrendStrength.setText(technicalAnalysis.getTrendStrength());

            // 주요 패턴
            binding.tvPattern.setText(technicalAnalysis.getPattern());
        }

        // 위험 요소
        if (analysisResult.getRiskFactors() != null && !analysisResult.getRiskFactors().isEmpty()) {
            StringBuilder riskFactors = new StringBuilder();

            for (int i = 0; i < analysisResult.getRiskFactors().size(); i++) {
                riskFactors.append("• ").append(analysisResult.getRiskFactors().get(i));
                if (i < analysisResult.getRiskFactors().size() - 1) {
                    riskFactors.append("\n\n");
                }
            }

            binding.tvRiskFactors.setText(riskFactors.toString());
        }
    }
}