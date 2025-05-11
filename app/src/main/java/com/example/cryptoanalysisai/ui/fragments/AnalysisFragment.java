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
import com.example.cryptoanalysisai.services.AnalysisApiService;
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
    private AnalysisApiService analysisApiService;

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
     * AWS Lambda API에서 분석 결과 로드
     */
    private void loadAnalysisFromApi() {
        if (binding == null || coinInfo == null) return;

        binding.progressAnalysis.setVisibility(View.VISIBLE);

        // 분석 버튼 비활성화
        binding.btnStartAnalysis.setEnabled(false);
        binding.btnStartAnalysis.setText("분석 데이터 로딩 중...");

        // 요청에 대한 로그 추가
        Log.d(TAG, "분석 결과 요청: " + coinInfo.getSymbol() + ", exchange: binance");

        analysisApiService.getLatestAnalysis(coinInfo.getSymbol(),
                new AnalysisApiService.OnAnalysisRetrievedListener() {
                    @Override
                    public void onAnalysisRetrieved(AnalysisResult result) {
                        if (getActivity() == null || binding == null) return;

                        analysisResult = result;

                        // 받은 응답 로그 출력
                        Log.d(TAG, "분석 결과 수신: " + (result != null ? result.toString() : "null"));

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

                        Log.d(TAG, "분석 결과 없음");

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

                        Log.e(TAG, "분석 결과 로드 실패: " + errorMessage);

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

        Log.d(TAG, "분석 결과 업데이트: " + analysisResult.toString());

        try {
            // 분석 요약
            binding.tvAnalysisSummary.setText(analysisResult.getSummary());

            // 매수/매도 추천
            // 추천 타입에 따라 색상 변경
            Constants.RecommendationType recommendType = Constants.RecommendationType.fromString(analysisResult.getRecommendation());
            binding.tvRecommendation.setText(recommendType.getDisplayName() + " 추천");
            binding.tvRecommendation.setTextColor(recommendType.getColor());

            // 확률 막대 업데이트
            int buyProgress = (int) Math.round(analysisResult.getBuyProbability());
            binding.progressProbability.setProgress(buyProgress);

            // 확률 텍스트 업데이트
            binding.tvProbabilityText.setText(String.format("매수: %.1f%% / 매도: %.1f%%",
                    analysisResult.getBuyProbability(), analysisResult.getSellProbability()));

            // 신뢰도 업데이트
            binding.ratingBar.setRating((float) analysisResult.getConfidence() / 2); // 0-10 -> 0-5 변환

            // 근거 업데이트 - 적절한, 읽기 좋은 텍스트를 표시하도록 수정
            // 이 부분은 API 응답에서 직접적으로 제공하지 않으므로 다른 필드의 정보를 조합해서 표시
            String reason = "현재 " + analysisResult.getTrendStrength() + "한 추세에 있으며, 현재가("
                    + analysisResult.getCurrencySymbol() + String.format("%,.0f", analysisResult.getCurrentPrice())
                    + ")는 지지선/저항선 대비 " + (analysisResult.getBuyProbability() > analysisResult.getSellProbability()
                    ? "매수 유리한 위치" : "매도 유리한 위치") + "에 있습니다.";
            binding.tvReason.setText(reason);

            // 매매 전략
            // 수익실현 목표가
            List<Double> targetPrices = analysisResult.getTargetPrices();
            if (targetPrices != null && !targetPrices.isEmpty()) {
                StringBuilder targetPricesText = new StringBuilder();
                String currencySymbol = analysisResult.getCurrencySymbol();

                for (int i = 0; i < targetPrices.size(); i++) {
                    if (i > 0) targetPricesText.append(", ");
                    targetPricesText.append(currencySymbol).append(String.format("%,.0f", targetPrices.get(i)));
                }

                binding.tvTargetPrice.setText(targetPricesText.toString());
            }

            // 손절매 라인
            binding.tvStopLoss.setText(analysisResult.getCurrencySymbol() +
                    String.format("%,.0f", analysisResult.getStopLoss()));

            // 리스크 대비 보상 비율
            binding.tvRiskReward.setText(String.format("%.1f", analysisResult.getRiskRewardRatio()) + ":1");

            // 전략 설명
            binding.tvStrategyDetail.setText(analysisResult.getStrategyExplanation());

            // 시간별 전망
            // 단기 전망
            binding.tvShortTerm.setText(analysisResult.getShortTermOutlook());

            // 중기 전망
            binding.tvMidTerm.setText(analysisResult.getMidTermOutlook());

            // 장기 전망
            binding.tvLongTerm.setText(analysisResult.getLongTermOutlook());

            // 기술적 분석
            // 지지선
            List<Double> supportLevels = analysisResult.getSupportLevels();
            if (supportLevels != null && !supportLevels.isEmpty()) {
                StringBuilder supportLevelsText = new StringBuilder();
                String currencySymbol = analysisResult.getCurrencySymbol();

                for (int i = 0; i < supportLevels.size(); i++) {
                    if (i > 0) supportLevelsText.append(", ");
                    supportLevelsText.append(currencySymbol)
                            .append(String.format("%,.0f", supportLevels.get(i)));
                }

                binding.tvSupport.setText(supportLevelsText.toString());
            }

            // 저항선
            List<Double> resistanceLevels = analysisResult.getResistanceLevels();
            if (resistanceLevels != null && !resistanceLevels.isEmpty()) {
                StringBuilder resistanceLevelsText = new StringBuilder();
                String currencySymbol = analysisResult.getCurrencySymbol();

                for (int i = 0; i < resistanceLevels.size(); i++) {
                    if (i > 0) resistanceLevelsText.append(", ");
                    resistanceLevelsText.append(currencySymbol)
                            .append(String.format("%,.0f", resistanceLevels.get(i)));
                }

                binding.tvResistance.setText(resistanceLevelsText.toString());
            }

            // 추세 강도
            binding.tvTrendStrength.setText(analysisResult.getTrendStrength());

            // 주요 패턴
            binding.tvPattern.setText(analysisResult.getPattern());

            // 위험 요소
            List<String> riskFactors = analysisResult.getRiskFactors();
            if (riskFactors != null && !riskFactors.isEmpty()) {
                StringBuilder riskFactorsText = new StringBuilder();

                for (int i = 0; i < riskFactors.size(); i++) {
                    riskFactorsText.append("• ").append(riskFactors.get(i));
                    if (i < riskFactors.size() - 1) {
                        riskFactorsText.append("\n\n");
                    }
                }

                binding.tvRiskFactors.setText(riskFactorsText.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "UI 업데이트 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(getContext(), "분석 데이터 처리 중 오류 발생", Toast.LENGTH_SHORT).show();
        }
    }
}