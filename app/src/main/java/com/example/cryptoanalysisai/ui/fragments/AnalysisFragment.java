package com.example.cryptoanalysisai.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.cryptoanalysisai.api.RetrofitClient;
import com.example.cryptoanalysisai.api.UpbitApiService;
import com.example.cryptoanalysisai.databinding.FragmentAnalysisBinding;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.CandleData;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.models.TickerData;
import com.example.cryptoanalysisai.models.firebase.FirestoreAnalysisResult;
import com.example.cryptoanalysisai.services.AnalysisService;
import com.example.cryptoanalysisai.services.FirebaseManager;
import com.example.cryptoanalysisai.services.TechnicalIndicatorService;
import com.example.cryptoanalysisai.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AnalysisFragment extends Fragment {

    private static final String TAG = "AnalysisFragment";
    private static final String ARG_COIN_INFO = "arg_coin_info";
    private static final String ARG_EXCHANGE_TYPE = "arg_exchange_type";
    private static final long COOLDOWN_DURATION = 30000; // 30초 (개발 테스트용, 배포시 60000ms로 변경)

    private FragmentAnalysisBinding binding;
    private CoinInfo coinInfo;
    private ExchangeType exchangeType = ExchangeType.UPBIT;
    private AnalysisResult analysisResult;
    private AnalysisService analysisService;
    private TechnicalIndicatorService indicatorService;
    private FirebaseManager firebaseManager;
    private List<CandleData> candleDataList = new ArrayList<>();
    private Map<String, Object> technicalIndicators;
    private TickerData currentTickerData;

    // 분석 상태 및 쿨다운 관련 변수
    private boolean isAnalysisInProgress = false;
    private boolean isCooldownActive = false;
    private CountDownTimer cooldownTimer;

    // 데이터 로딩 관련 변수
    private AtomicInteger pendingRequests = new AtomicInteger(0);

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
        }

        analysisService = new AnalysisService();
        indicatorService = new TechnicalIndicatorService();
        firebaseManager = FirebaseManager.getInstance();
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
            if (!isAnalysisInProgress && !isCooldownActive) {
                // 새로운 분석 시작
                startAnalysis();
            } else if (isCooldownActive) {
                Toast.makeText(getContext(), "분석은 30초에 한 번만 가능합니다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "분석이 이미 진행 중입니다", Toast.LENGTH_SHORT).show();
            }
        });

        // UI 초기화
        if (coinInfo != null && coinInfo.getMarket() != null) {
            updateCoin(coinInfo, exchangeType);
        } else {
            binding.tvCoinTitle.setText("코인을 선택해주세요");
            binding.progressAnalysis.setVisibility(View.GONE);
            hideAllCards();
        }
    }

    @Override
    public void onDestroyView() {
        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }
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

            // 버튼 상태 초기화
            isAnalysisInProgress = false;
            isCooldownActive = false;
            if (cooldownTimer != null) {
                cooldownTimer.cancel();
            }
            binding.btnStartAnalysis.setEnabled(true);
            binding.btnStartAnalysis.setText("분석 시작");

            // 로딩 표시
            binding.progressAnalysis.setVisibility(View.VISIBLE);

            // 카드 숨기기
            hideAllCards();

            // Firebase에서 최신 분석 결과 로드
            loadLatestAnalysisFromFirebase();

            // 캔들 데이터도 함께 로드 (새 분석을 위한 준비)
            loadCandleData(coinInfo.getMarket(), Constants.CandleInterval.DAY_1);

            // 현재가 정보 로드
            loadCurrentPrice(coinInfo.getMarket());
        }
    }

    /**
     * 현재가 정보 로드
     */
    private void loadCurrentPrice(String market) {
        if (exchangeType == ExchangeType.UPBIT) {
            UpbitApiService apiService = RetrofitClient.getUpbitApiService();

            pendingRequests.incrementAndGet();
            apiService.getTicker(market).enqueue(new Callback<List<TickerData>>() {
                @Override
                public void onResponse(@NonNull Call<List<TickerData>> call, @NonNull Response<List<TickerData>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        currentTickerData = response.body().get(0);
                        if (coinInfo != null) {
                            coinInfo.setCurrentPrice(currentTickerData.getTradePrice());
                            coinInfo.setPriceChange(currentTickerData.getChangeRate());
                        }
                    }

                    if (pendingRequests.decrementAndGet() == 0) {
                        binding.progressAnalysis.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<TickerData>> call, @NonNull Throwable t) {
                    Log.e(TAG, "현재가 로드 실패: " + t.getMessage());
                    if (pendingRequests.decrementAndGet() == 0) {
                        binding.progressAnalysis.setVisibility(View.GONE);
                    }
                }
            });
        }
        // 바이낸스 구현은 생략
    }

    /**
     * Firebase에서 최신 분석 결과 로드
     */
    private void loadLatestAnalysisFromFirebase() {
        if (coinInfo == null || coinInfo.getSymbol() == null) return;

        pendingRequests.incrementAndGet();

        firebaseManager.getLatestAnalysis(
                coinInfo.getSymbol(),
                exchangeType.getCode(),
                new FirebaseManager.OnAnalysisRetrievedListener() {
                    @Override
                    public void onAnalysisRetrieved(FirestoreAnalysisResult firestoreResult) {
                        if (getActivity() == null || binding == null) {
                            pendingRequests.decrementAndGet();
                            return;
                        }

                        getActivity().runOnUiThread(() -> {
                            // Firestore 결과를 AnalysisResult로 변환
                            analysisResult = firestoreResult.toAnalysisResult();

                            // UI 업데이트
                            updateAnalysisUI();

                            if (pendingRequests.decrementAndGet() == 0) {
                                binding.progressAnalysis.setVisibility(View.GONE);
                            }

                            // 최신 분석 시간 표시
                            String timeAgo = getTimeAgo(firestoreResult.getTimestamp());
                            binding.btnStartAnalysis.setText("새로 분석하기 (마지막 분석: " + timeAgo + ")");
                        });
                    }

                    @Override
                    public void onNoAnalysisFound() {
                        if (getActivity() == null || binding == null) {
                            pendingRequests.decrementAndGet();
                            return;
                        }

                        getActivity().runOnUiThread(() -> {
                            if (pendingRequests.decrementAndGet() == 0) {
                                binding.progressAnalysis.setVisibility(View.GONE);
                            }
                            binding.btnStartAnalysis.setText("분석하기");
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null || binding == null) {
                            pendingRequests.decrementAndGet();
                            return;
                        }

                        getActivity().runOnUiThread(() -> {
                            if (pendingRequests.decrementAndGet() == 0) {
                                binding.progressAnalysis.setVisibility(View.GONE);
                            }
                        });
                    }
                });
    }

    /**
     * 시간 차이를 사람이 읽기 쉬운 형태로 변환
     */
    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        // 분 단위
        long minutes = diff / (60 * 1000);
        if (minutes < 60) {
            return minutes + "분 전";
        }

        // 시간 단위
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "시간 전";
        }

        // 일 단위
        long days = hours / 24;
        return days + "일 전";
    }

    /**
     * 분석 결과 설정
     */
    public void setAnalysisResult(AnalysisResult result) {
        this.analysisResult = result;
        updateAnalysisUI();
    }

    /**
     * 분석 시작
     */
    private void startAnalysis() {
        if (coinInfo == null || coinInfo.getMarket() == null) {
            Toast.makeText(getContext(), "코인을 먼저 선택해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        // 로딩 표시
        binding.progressAnalysis.setVisibility(View.VISIBLE);
        isAnalysisInProgress = true;
        binding.btnStartAnalysis.setText("분석 중...");
        binding.btnStartAnalysis.setEnabled(false);

        // 캔들 데이터와 현재가가 아직 로드되지 않았다면 다시 로드
        if (candleDataList.isEmpty() || currentTickerData == null) {
            loadCandleData(coinInfo.getMarket(), Constants.CandleInterval.DAY_1);
            loadCurrentPrice(coinInfo.getMarket());
        } else {
            // 이미 데이터가 로드되어 있다면 바로 분석 요청
            requestAnalysis();
        }
    }

    /**
     * 캔들 데이터 로드
     */
    private void loadCandleData(String market, Constants.CandleInterval interval) {
        pendingRequests.incrementAndGet();

        if (exchangeType == ExchangeType.UPBIT) {
            UpbitApiService apiService = RetrofitClient.getUpbitApiService();

            apiService.getDayCandles(market, 30).enqueue(new Callback<List<CandleData>>() {
                @Override
                public void onResponse(@NonNull Call<List<CandleData>> call, @NonNull Response<List<CandleData>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        candleDataList = response.body();

                        // 기술적 지표 계산
                        technicalIndicators = indicatorService.calculateAllIndicators(candleDataList);

                        // 분석 중이라면 분석 요청
                        if (isAnalysisInProgress) {
                            requestAnalysis();
                        }
                    } else {
                        handleAnalysisError("캔들 데이터를 가져오는데 실패했습니다: " + response.code());
                    }

                    if (pendingRequests.decrementAndGet() == 0 && !isAnalysisInProgress) {
                        binding.progressAnalysis.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<CandleData>> call, @NonNull Throwable t) {
                    handleAnalysisError("네트워크 오류: " + t.getMessage());

                    if (pendingRequests.decrementAndGet() == 0 && !isAnalysisInProgress) {
                        binding.progressAnalysis.setVisibility(View.GONE);
                    }
                }
            });
        }
        // 바이낸스 구현은 생략
    }

    /**
     * 분석 오류 처리
     */
    private void handleAnalysisError(String errorMessage) {
        if (getActivity() == null || binding == null) return;

        getActivity().runOnUiThread(() -> {
            Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
            binding.progressAnalysis.setVisibility(View.GONE);
            isAnalysisInProgress = false;
            binding.btnStartAnalysis.setText("분석 시작");
            binding.btnStartAnalysis.setEnabled(true);
        });
    }

    /**
     * Claude API로 분석 요청
     */
    private void requestAnalysis() {
        if (coinInfo == null || candleDataList.isEmpty() || technicalIndicators == null || currentTickerData == null) {
            handleAnalysisError("분석에 필요한 데이터가 없습니다");
            return;
        }

        // Claude API로 분석 요청
        analysisService.generateAnalysis(coinInfo, candleDataList, currentTickerData, exchangeType, technicalIndicators,
                new AnalysisService.AnalysisCallback() {
                    @Override
                    public void onAnalysisSuccess(AnalysisResult result, String rawResponse) {
                        if (getActivity() == null || isDetached()) return;

                        analysisResult = result;

                        // Firebase에 분석 결과 저장
                        firebaseManager.saveAnalysisResult(result, coinInfo, exchangeType,
                                new FirebaseManager.OnAnalysisSavedListener() {
                                    @Override
                                    public void onSuccess(String documentId) {
                                        if (getActivity() == null) return;

                                        getActivity().runOnUiThread(() -> {
                                            updateAnalysisUI();
                                            binding.progressAnalysis.setVisibility(View.GONE);
                                            isAnalysisInProgress = false;

                                            // 분석 완료 후 쿨다운 시작
                                            startCooldown();

                                            Toast.makeText(getContext(), "분석 및 저장 완료!", Toast.LENGTH_SHORT).show();
                                        });
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        if (getActivity() == null) return;

                                        getActivity().runOnUiThread(() -> {
                                            // Firebase 저장 실패해도 UI는 업데이트
                                            updateAnalysisUI();
                                            binding.progressAnalysis.setVisibility(View.GONE);
                                            isAnalysisInProgress = false;

                                            // 분석 완료 후 쿨다운 시작
                                            startCooldown();

                                            Toast.makeText(getContext(),
                                                    "분석 완료 (저장 실패: " + errorMessage + ")",
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                });
                    }

                    @Override
                    public void onAnalysisFailure(String error) {
                        if (getActivity() == null) return;

                        getActivity().runOnUiThread(() -> {
                            handleAnalysisError("분석 실패: " + error);
                        });
                    }
                });
    }

    /**
     * 쿨다운 타이머 시작
     */
    private void startCooldown() {
        isCooldownActive = true;

        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }

        cooldownTimer = new CountDownTimer(COOLDOWN_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (binding != null) {
                    int secondsLeft = (int) (millisUntilFinished / 1000);
                    binding.btnStartAnalysis.setText("분석 대기 중... (" + secondsLeft + "초)");
                }
            }

            @Override
            public void onFinish() {
                if (binding != null) {
                    isCooldownActive = false;
                    binding.btnStartAnalysis.setText("새로 분석하기");
                    binding.btnStartAnalysis.setEnabled(true);
                }
            }
        }.start();
    }

    /**
     * 모든 카드 숨기기
     */
    private void hideAllCards() {
        if (binding != null) {
            binding.cardSummary.setVisibility(View.GONE);
            binding.cardRecommendation.setVisibility(View.GONE);
            binding.cardStrategy.setVisibility(View.GONE);
            binding.cardOutlook.setVisibility(View.GONE);
            binding.cardTechnical.setVisibility(View.GONE);
            binding.cardRisk.setVisibility(View.GONE);
        }
    }

    /**
     * 분석 결과로 UI 업데이트
     */
    private void updateAnalysisUI() {
        if (binding == null || analysisResult == null) return;

        // 모든 카드 표시
        binding.cardSummary.setVisibility(View.VISIBLE);
        binding.cardRecommendation.setVisibility(View.VISIBLE);
        binding.cardStrategy.setVisibility(View.VISIBLE);
        binding.cardOutlook.setVisibility(View.VISIBLE);
        binding.cardTechnical.setVisibility(View.VISIBLE);
        binding.cardRisk.setVisibility(View.VISIBLE);

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
            String trendStrength = technicalAnalysis.getTrendStrength();
            binding.tvTrendStrength.setText(trendStrength);

            // 추세 강도에 따라 색상 변경
            if ("강".equals(trendStrength)) {
                binding.tvTrendStrength.setTextColor(Color.rgb(76, 175, 80)); // 강함 - 초록색
            } else if ("약".equals(trendStrength)) {
                binding.tvTrendStrength.setTextColor(Color.rgb(244, 67, 54)); // 약함 - 빨간색
            } else {
                binding.tvTrendStrength.setTextColor(Color.rgb(255, 152, 0)); // 중간 - 주황색
            }

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