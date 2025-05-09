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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AnalysisFragment extends Fragment {

    private static final String TAG = "AnalysisFragment";
    private static final String ARG_COIN_INFO = "arg_coin_info";
    private static final String ARG_EXCHANGE_TYPE = "arg_exchange_type";
    private static final long COOLDOWN_DURATION = 60000; // 1분 (60,000ms)

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
                // 새로운 분석 시작 전에 최신 가격 정보 로드
                loadCurrentPrice();
            } else if (isCooldownActive) {
                Toast.makeText(getContext(), "분석은 1분에 한 번만 가능합니다", Toast.LENGTH_SHORT).show();
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

            // Firebase에서 최신 분석 결과 로드
            loadLatestAnalysisFromFirebase();

            // 캔들 데이터도 함께 로드 (새 분석을 위한 준비)
            loadCandleData(coinInfo.getMarket(), Constants.CandleInterval.DAY_1);
        }
    }

    /**
     * Firebase에서 최신 분석 결과 로드
     */
    private void loadLatestAnalysisFromFirebase() {
        if (coinInfo == null || coinInfo.getSymbol() == null) return;

        binding.progressAnalysis.setVisibility(View.VISIBLE);

        firebaseManager.getLatestAnalysis(
                coinInfo.getSymbol(),
                exchangeType.getCode(),
                new FirebaseManager.OnAnalysisRetrievedListener() {
                    @Override
                    public void onAnalysisRetrieved(FirestoreAnalysisResult firestoreResult) {
                        if (getActivity() == null || binding == null) return;

                        getActivity().runOnUiThread(() -> {
                            // Firestore 결과를 AnalysisResult로 변환
                            analysisResult = firestoreResult.toAnalysisResult();

                            // UI 업데이트
                            updateAnalysisUI();
                            binding.progressAnalysis.setVisibility(View.GONE);

                            // 최신 분석 시간 표시
                            String timeAgo = getTimeAgo(firestoreResult.getTimestamp());
                            binding.btnStartAnalysis.setText("새로 분석하기 (마지막 분석: " + timeAgo + ")");
                        });
                    }

                    @Override
                    public void onNoAnalysisFound() {
                        if (getActivity() == null || binding == null) return;

                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "저장된 분석 결과가 없습니다.", Toast.LENGTH_SHORT).show();
                            binding.progressAnalysis.setVisibility(View.GONE);
                            binding.btnStartAnalysis.setText("분석하기");
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null || binding == null) return;

                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "분석 결과 로드 실패: " + errorMessage, Toast.LENGTH_SHORT).show();
                            binding.progressAnalysis.setVisibility(View.GONE);
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
     * 현재가 정보 로드 (분석 버튼 클릭 시)
     */
    private void loadCurrentPrice() {
        binding.progressAnalysis.setVisibility(View.VISIBLE);
        isAnalysisInProgress = true;
        binding.btnStartAnalysis.setText("분석 중...");
        binding.btnStartAnalysis.setEnabled(false);

        if (coinInfo == null || coinInfo.getMarket() == null) {
            Toast.makeText(getContext(), "코인 정보가 없습니다", Toast.LENGTH_SHORT).show();
            binding.progressAnalysis.setVisibility(View.GONE);
            isAnalysisInProgress = false;
            binding.btnStartAnalysis.setText("분석 시작");
            binding.btnStartAnalysis.setEnabled(true);
            return;
        }

        if (exchangeType == ExchangeType.UPBIT) {
            UpbitApiService apiService = RetrofitClient.getUpbitApiService();

            apiService.getTicker(coinInfo.getMarket()).enqueue(new Callback<List<TickerData>>() {
                @Override
                public void onResponse(@NonNull Call<List<TickerData>> call, @NonNull Response<List<TickerData>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        currentTickerData = response.body().get(0);
                        coinInfo.setCurrentPrice(currentTickerData.getTradePrice());
                        coinInfo.setPriceChange(currentTickerData.getChangeRate());

                        // 현재가 로드 후 분석 시작
                        requestAnalysis();
                    } else {
                        handleAnalysisError("가격 정보를 가져오는데 실패했습니다");
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<TickerData>> call, @NonNull Throwable t) {
                    handleAnalysisError("네트워크 오류: " + t.getMessage());
                }
            });
        } else if (exchangeType == ExchangeType.BINANCE) {
            // 바이낸스 코드도 비슷하게 구현 (간략화)
            handleAnalysisError("현재 바이낸스는 지원하지 않습니다");
        }
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
     * 캔들 데이터 로드
     */
    private void loadCandleData(String market, Constants.CandleInterval interval) {
        if (exchangeType == ExchangeType.UPBIT) {
            loadUpbitCandles(market, interval);
        } else if (exchangeType == ExchangeType.BINANCE) {
            loadBinanceCandles(market, interval);
        }
    }

    /**
     * 업비트 캔들 데이터 로드
     */
    private void loadUpbitCandles(String market, Constants.CandleInterval interval) {
        UpbitApiService apiService = RetrofitClient.getUpbitApiService();

        Call<List<CandleData>> call;
        switch (interval) {
            case DAY_1:
                call = apiService.getDayCandles(market, 30);
                break;
            case HOUR_4:
                call = apiService.getMinuteCandles(market, 240, 30);
                break;
            case HOUR_1:
                call = apiService.getHourCandles(market, 30);
                break;
            case MINUTE_15:
                call = apiService.getMinuteCandles(market, 15, 30);
                break;
            default:
                call = apiService.getDayCandles(market, 30);
                break;
        }

        call.enqueue(new Callback<List<CandleData>>() {
            @Override
            public void onResponse(@NonNull Call<List<CandleData>> call, @NonNull Response<List<CandleData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    candleDataList = response.body();

                    // 기술적 지표 계산
                    technicalIndicators = indicatorService.calculateAllIndicators(candleDataList);
                } else {
                    Log.e(TAG, "캔들 데이터 로드 실패: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CandleData>> call, @NonNull Throwable t) {
                Log.e(TAG, "캔들 데이터 로드 네트워크 오류: " + t.getMessage());
            }
        });
    }

    /**
     * 바이낸스 캔들 데이터 로드
     */
    private void loadBinanceCandles(String market, Constants.CandleInterval interval) {
        // 바이낸스 캔들 데이터 로드 코드 (간략화)
        Log.d(TAG, "바이낸스 캔들 데이터 로드");
    }

    /**
     * Claude API로 분석 요청
     */
    private void requestAnalysis() {
        if (coinInfo == null || candleDataList.isEmpty() || technicalIndicators == null) {
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