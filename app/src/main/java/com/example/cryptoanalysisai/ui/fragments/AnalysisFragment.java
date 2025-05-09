package com.example.cryptoanalysisai.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.cryptoanalysisai.api.BinanceApiService;
import com.example.cryptoanalysisai.api.RetrofitClient;
import com.example.cryptoanalysisai.api.UpbitApiService;
import com.example.cryptoanalysisai.databinding.FragmentAnalysisBinding;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.CandleData;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.models.TickerData;
import com.example.cryptoanalysisai.services.AnalysisService;
import com.example.cryptoanalysisai.services.TechnicalIndicatorService;
import com.example.cryptoanalysisai.utils.Constants;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * 인스턴스 생성 메서드
     */
    public static AnalysisFragment newInstance(CoinInfo coinInfo, ExchangeType exchangeType) {
        AnalysisFragment fragment = new AnalysisFragment();
        Bundle args = new Bundle();

        if (coinInfo != null) {
            // TODO: CoinInfo 직렬화 구현 필요 (Parcelable)
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
            // TODO: CoinInfo 역직렬화 구현 필요 (현재는 그냥 마켓코드만 저장)
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

        // 라인 차트 초기화
        setupLineChart();

        // 분석 버튼 클릭 리스너 설정
        binding.btnStartAnalysis.setOnClickListener(v -> {
            if (!isAnalysisInProgress && !isCooldownActive) {
                // 분석 시작 전에 최신 가격 정보 로드
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
     * 코인 정보 업데이트
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

            // 캔들 데이터 로드
            loadCandleData(coinInfo.getMarket(), Constants.CandleInterval.DAY_1);
        }
    }

    /**
     * 분석 결과 설정
     */
    public void setAnalysisResult(AnalysisResult result) {
        this.analysisResult = result;
        updateAnalysisUI();
    }

    /**
     * 라인 차트 초기화 - x축 레이블 제거
     */
    private void setupLineChart() {
        // 차트 설정
        binding.lineChart.setBackgroundColor(Color.WHITE);
        binding.lineChart.getDescription().setEnabled(false);
        binding.lineChart.setTouchEnabled(true);
        binding.lineChart.setDragEnabled(true);
        binding.lineChart.setScaleEnabled(true);
        binding.lineChart.setPinchZoom(true);

        // X축 설정 - 라벨 제거
        XAxis xAxis = binding.lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(false); // X축 레이블 숨기기
        xAxis.setDrawAxisLine(true);

        // 왼쪽 Y축 설정
        YAxis leftAxis = binding.lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setLabelCount(8);

        // 오른쪽 Y축 설정
        YAxis rightAxis = binding.lineChart.getAxisRight();
        rightAxis.setEnabled(false);

        // 범례 설정
        Legend legend = binding.lineChart.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
    }

    /**
     * 캔들 데이터 로드
     */
    private void loadCandleData(String market, Constants.CandleInterval interval) {
        binding.progressAnalysis.setVisibility(View.VISIBLE);

        if (exchangeType == ExchangeType.UPBIT) {
            loadUpbitCandles(market, interval);
        } else if (exchangeType == ExchangeType.BINANCE) {
            loadBinanceCandles(market, interval);
        }
    }

    /**
     * 현재가 정보 로드 - 분석 시작 전에 호출
     */
    private void loadCurrentPrice() {
        binding.progressAnalysis.setVisibility(View.VISIBLE);

        if (coinInfo == null || coinInfo.getMarket() == null) {
            Toast.makeText(getContext(), "코인 정보가 없습니다", Toast.LENGTH_SHORT).show();
            binding.progressAnalysis.setVisibility(View.GONE);
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
                        Toast.makeText(getContext(), "가격 정보를 가져오는데 실패했습니다", Toast.LENGTH_SHORT).show();
                        binding.progressAnalysis.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<TickerData>> call, @NonNull Throwable t) {
                    Toast.makeText(getContext(), "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.progressAnalysis.setVisibility(View.GONE);
                }
            });
        } else if (exchangeType == ExchangeType.BINANCE) {
            BinanceApiService apiService = RetrofitClient.getBinanceApiService();

            apiService.getTicker(coinInfo.getMarket()).enqueue(new Callback<com.example.cryptoanalysisai.models.BinanceTicker>() {
                @Override
                public void onResponse(@NonNull Call<com.example.cryptoanalysisai.models.BinanceTicker> call,
                                       @NonNull Response<com.example.cryptoanalysisai.models.BinanceTicker> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        final com.example.cryptoanalysisai.models.BinanceTicker ticker = response.body();
                        coinInfo.setCurrentPrice(ticker.getPrice());

                        // 24시간 변화율 정보도 가져오기
                        apiService.get24hTicker(coinInfo.getMarket()).enqueue(new Callback<com.example.cryptoanalysisai.models.BinanceTicker>() {
                            @Override
                            public void onResponse(@NonNull Call<com.example.cryptoanalysisai.models.BinanceTicker> call,
                                                   @NonNull Response<com.example.cryptoanalysisai.models.BinanceTicker> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    com.example.cryptoanalysisai.models.BinanceTicker ticker24h = response.body();
                                    coinInfo.setPriceChange(ticker24h.getPriceChangePercent() / 100.0);

                                    // 티커 데이터를 UpbitTickerData 형태로 변환
                                    currentTickerData = ticker.toUpbitFormat();

                                    // 현재가 로드 후 분석 시작
                                    requestAnalysis();
                                } else {
                                    Toast.makeText(getContext(), "가격 정보를 가져오는데 실패했습니다", Toast.LENGTH_SHORT).show();
                                    binding.progressAnalysis.setVisibility(View.GONE);
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<com.example.cryptoanalysisai.models.BinanceTicker> call, @NonNull Throwable t) {
                                Toast.makeText(getContext(), "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                                binding.progressAnalysis.setVisibility(View.GONE);
                            }
                        });
                    } else {
                        Toast.makeText(getContext(), "가격 정보를 가져오는데 실패했습니다", Toast.LENGTH_SHORT).show();
                        binding.progressAnalysis.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<com.example.cryptoanalysisai.models.BinanceTicker> call, @NonNull Throwable t) {
                    Toast.makeText(getContext(), "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.progressAnalysis.setVisibility(View.GONE);
                }
            });
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

                    // 데이터 정렬 - 업비트 데이터는 최신 -> 과거 순으로 되어 있음
                    // 차트를 위해 순서를 유지 (최신이 오른쪽에 오도록)
                    // Collections.reverse(candleDataList);

                    // 기술적 지표 계산
                    technicalIndicators = indicatorService.calculateAllIndicators(candleDataList);

                    // 차트 데이터 업데이트
                    updateChartData(candleDataList);

                    binding.progressAnalysis.setVisibility(View.GONE);
                } else {
                    Toast.makeText(getContext(), "데이터 로딩 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    binding.progressAnalysis.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CandleData>> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                binding.progressAnalysis.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 바이낸스 캔들 데이터 로드
     */
    private void loadBinanceCandles(String market, Constants.CandleInterval interval) {
        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.getKlines(market, interval.getBinanceCode(), 30).enqueue(new Callback<List<List<Object>>>() {
            @Override
            public void onResponse(@NonNull Call<List<List<Object>>> call, @NonNull Response<List<List<Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<List<Object>> klines = response.body();
                    candleDataList = new ArrayList<>();

                    for (List<Object> kline : klines) {
                        com.example.cryptoanalysisai.models.BinanceModels.BinanceKline binanceKline =
                                new com.example.cryptoanalysisai.models.BinanceModels.BinanceKline(kline);
                        candleDataList.add(binanceKline.toUpbitFormat(market));
                    }

                    // 데이터 정렬 처리하지 않음 (이미 바이낸스도 최신 -> 과거 순으로 정렬됨)
                    // Collections.reverse(candleDataList);

                    // 기술적 지표 계산
                    technicalIndicators = indicatorService.calculateAllIndicators(candleDataList);

                    // 차트 데이터 업데이트
                    updateChartData(candleDataList);

                    binding.progressAnalysis.setVisibility(View.GONE);
                } else {
                    Toast.makeText(getContext(), "데이터 로딩 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    binding.progressAnalysis.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<List<Object>>> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                binding.progressAnalysis.setVisibility(View.GONE);
            }
        });
    }



    /**
     * 차트 데이터 업데이트
     */
    private void updateChartData(List<CandleData> candles) {
        if (candles.isEmpty() || binding == null) return;

        // 라인 차트 데이터 생성 (종가)
        List<Entry> priceEntries = new ArrayList<>();
        List<String> xValues = new ArrayList<>();

        // 최신 데이터(인덱스 0)를 차트의 오른쪽에 표시하기 위해 역순으로 추가
        // candles은 이미 최신->과거 순으로 정렬되어 있음
        for (int i = candles.size() - 1; i >= 0; i--) {
            CandleData candle = candles.get(i);
            // 인덱스 재조정: 0 -> size-1, 1 -> size-2, ..., size-1 -> 0
            int adjustedIndex = candles.size() - 1 - i;
            priceEntries.add(new Entry(adjustedIndex, (float) candle.getTradePrice()));
            xValues.add(candle.getFormattedDate());
        }

        // 가격 데이터셋 생성
        LineDataSet priceDataSet = new LineDataSet(priceEntries, "가격");
        priceDataSet.setColor(Color.BLUE);
        priceDataSet.setCircleColor(Color.BLUE);
        priceDataSet.setCircleRadius(1f);
        priceDataSet.setDrawCircleHole(false);
        priceDataSet.setLineWidth(2f);
        priceDataSet.setDrawValues(false);
        priceDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);

        // 이동평균선 데이터 추가 (SMA, EMA)
        LineData lineData;
        if (technicalIndicators != null && technicalIndicators.containsKey("smaSeries") && technicalIndicators.containsKey("emaSeries")) {
            List<Double> smaSeries = (List<Double>) technicalIndicators.get("smaSeries");
            List<Double> emaSeries = (List<Double>) technicalIndicators.get("emaSeries");

            List<Entry> smaEntries = new ArrayList<>();
            List<Entry> emaEntries = new ArrayList<>();

            // SMA, EMA 데이터도 마찬가지로 역순으로 추가
            int smaSize = smaSeries.size();
            for (int i = smaSize - 1; i >= 0; i--) {
                int adjustedIndex = smaSize - 1 - i;
                if (adjustedIndex < candles.size()) {
                    smaEntries.add(new Entry(adjustedIndex, smaSeries.get(i).floatValue()));
                }
            }

            int emaSize = emaSeries.size();
            for (int i = emaSize - 1; i >= 0; i--) {
                int adjustedIndex = emaSize - 1 - i;
                if (adjustedIndex < candles.size()) {
                    emaEntries.add(new Entry(adjustedIndex, emaSeries.get(i).floatValue()));
                }
            }

            // SMA 데이터셋
            LineDataSet smaDataSet = new LineDataSet(smaEntries, "SMA(20)");
            smaDataSet.setColor(Color.RED);
            smaDataSet.setCircleRadius(0f);
            smaDataSet.setDrawCircles(false);
            smaDataSet.setLineWidth(1.5f);
            smaDataSet.setDrawValues(false);
            smaDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);

            // EMA 데이터셋
            LineDataSet emaDataSet = new LineDataSet(emaEntries, "EMA(20)");
            emaDataSet.setColor(Color.GREEN);
            emaDataSet.setCircleRadius(0f);
            emaDataSet.setDrawCircles(false);
            emaDataSet.setLineWidth(1.5f);
            emaDataSet.setDrawValues(false);
            emaDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);

            // 라인 차트 데이터 설정
            lineData = new LineData(priceDataSet, smaDataSet, emaDataSet);
        } else {
            // 이동평균선 없이 가격만 표시
            lineData = new LineData(priceDataSet);
        }

        binding.lineChart.setData(lineData);

        // X축 레이블 설정 - 레이블 숨김
        binding.lineChart.getXAxis().setValueFormatter(null);

        // 차트 업데이트
        binding.lineChart.invalidate();
    }

    /**
     * Claude API로 분석 요청 - 버튼 클릭시에만 호출
     */
    private void requestAnalysis() {
        if (coinInfo == null || candleDataList.isEmpty() || technicalIndicators == null) {
            Toast.makeText(getContext(), "분석에 필요한 데이터가 없습니다", Toast.LENGTH_SHORT).show();
            binding.progressAnalysis.setVisibility(View.GONE);
            return;
        }

        binding.progressAnalysis.setVisibility(View.VISIBLE);
        isAnalysisInProgress = true;

        // 버튼 텍스트 변경
        binding.btnStartAnalysis.setText("분석 중...");
        binding.btnStartAnalysis.setEnabled(false);

        // 이제 분석 시 현재가(최신) 데이터 사용
        analysisService.generateAnalysis(coinInfo, candleDataList, currentTickerData, exchangeType, technicalIndicators,
                new AnalysisService.AnalysisCallback() {
                    @Override
                    public void onAnalysisSuccess(AnalysisResult result, String rawResponse) {
                        analysisResult = result;

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                updateAnalysisUI();
                                binding.progressAnalysis.setVisibility(View.GONE);
                                isAnalysisInProgress = false;

                                // 분석 완료 후 쿨다운 시작
                                startCooldown();
                            });
                        }
                    }

                    @Override
                    public void onAnalysisFailure(String error) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "분석 실패: " + error, Toast.LENGTH_SHORT).show();
                                binding.progressAnalysis.setVisibility(View.GONE);
                                isAnalysisInProgress = false;
                                binding.btnStartAnalysis.setText("분석 시작");
                                binding.btnStartAnalysis.setEnabled(true);
                            });
                        }
                    }
                });
    }

    /**
     * 쿨다운 타이머 시작 메서드
     */
    private void startCooldown() {
        isCooldownActive = true;

        if (cooldownTimer != null) {
            cooldownTimer.cancel();
        }

        cooldownTimer = new CountDownTimer(COOLDOWN_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                binding.btnStartAnalysis.setText("분석 대기 중... (" + secondsLeft + "초)");
            }

            @Override
            public void onFinish() {
                isCooldownActive = false;
                binding.btnStartAnalysis.setText("분석 시작");
                binding.btnStartAnalysis.setEnabled(true);
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
            binding.ratingBar.setRating((float) recommendation.getConfidence());

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
                    targetPrices.append(currencySymbol).append(strategy.getTargetPrices().get(i));
                }

                binding.tvTargetPrice.setText(targetPrices.toString());
            }

            // 손절매 라인
            binding.tvStopLoss.setText(analysisResult.getCurrencySymbol() + strategy.getStopLoss());

            // 리스크 대비 보상 비율
            binding.tvRiskReward.setText(strategy.getRiskRewardRatio() + ":1");

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
                    supportLevels.append(currencySymbol).append(technicalAnalysis.getSupportLevels().get(i));
                }

                binding.tvSupport.setText(supportLevels.toString());
            }

            // 저항선
            if (technicalAnalysis.getResistanceLevels() != null && !technicalAnalysis.getResistanceLevels().isEmpty()) {
                StringBuilder resistanceLevels = new StringBuilder();
                String currencySymbol = analysisResult.getCurrencySymbol();

                for (int i = 0; i < technicalAnalysis.getResistanceLevels().size(); i++) {
                    if (i > 0) resistanceLevels.append(", ");
                    resistanceLevels.append(currencySymbol).append(technicalAnalysis.getResistanceLevels().get(i));
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
                riskFactors.append("- ").append(analysisResult.getRiskFactors().get(i));
                if (i < analysisResult.getRiskFactors().size() - 1) {
                    riskFactors.append("\n");
                }
            }

            binding.tvRiskFactors.setText(riskFactors.toString());
        }
    }
}