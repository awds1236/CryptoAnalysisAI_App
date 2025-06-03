package com.coinsense.cryptoanalysisai.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
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
import com.coinsense.cryptoanalysisai.api.BinanceApiService;
import com.coinsense.cryptoanalysisai.api.RetrofitClient;
import com.coinsense.cryptoanalysisai.models.AnalysisResult;
import com.coinsense.cryptoanalysisai.models.BinanceTicker;
import com.coinsense.cryptoanalysisai.models.CoinInfo;
import com.coinsense.cryptoanalysisai.services.ExchangeRateManager;
import com.coinsense.cryptoanalysisai.services.SubscriptionManager;
import com.coinsense.cryptoanalysisai.ui.activities.SubscriptionActivity;
import com.coinsense.cryptoanalysisai.ui.dialogs.AdViewDialog;
import com.coinsense.cryptoanalysisai.services.AdManager;
import com.coinsense.cryptoanalysisai.utils.Constants;

// MPAndroidChart 임포트
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

    // 차트 관련 UI 요소
    private CombinedChart strategyChart;

    private AdManager adManager;
    private TextView tvAdStatus;
    private Button btnWatchAd;
    private Handler adTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable adTimerRunnable;
    private CoinInfo coinInfo;

    private View additionalBlurLayer;
    private ExchangeRateManager exchangeRateManager;

    public void setCoinInfo(CoinInfo coinInfo) {
        if (coinInfo == null) {
            if (isAdded()) {
                Log.e("StrategyFragment", getString(R.string.no_coin_info_log));
            } else {
                Log.e("StrategyFragment", "setCoinInfo: coinInfo is null");
            }
            return;
        }

        boolean isSameCoin = this.coinInfo != null &&
                this.coinInfo.getSymbol() != null &&
                coinInfo.getSymbol() != null &&
                this.coinInfo.getSymbol().equals(coinInfo.getSymbol());

        if (!isSameCoin) {
            this.coinInfo = coinInfo;

            if (isAdded()) {
                Log.d("StrategyFragment", coinInfo != null ?
                        getString(R.string.coin_info_set_log_format, coinInfo.getSymbol()) :
                        getString(R.string.no_coin_info_log));
            } else {
                Log.d("StrategyFragment", coinInfo != null ?
                        "setCoinInfo: coinInfo set, symbol: " + coinInfo.getSymbol() :
                        "setCoinInfo: coinInfo is null");
            }

            saveCurrentCoinInfo();

            if (getView() != null) {
                updateContentAccessUI();
                // 차트 업데이트
                updateChart();
            }
        }
    }

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
        additionalBlurLayer = view.findViewById(R.id.additionalBlurLayer);

        // 차트 초기화
        strategyChart = view.findViewById(R.id.strategyChart);
        setupChart();

        ImageButton btnInfoDialog = view.findViewById(R.id.btnInfoDialog);
        btnInfoDialog.setOnClickListener(v -> showAnalysisInfoDialog());

        if (btnWatchAd == null) {
            Log.e("StrategyFragment", "btnWatchAd를 찾을 수 없습니다");
        }

        // 타이틀 설정
        String title;
        int titleColor;
        String emoji;

        switch (strategyType) {
            case STRATEGY_SHORT_TERM:
                title = getString(R.string.short_term_strategy_title);
                titleColor = Color.parseColor("#4CAF50");
                emoji = "⚡";
                break;
            case STRATEGY_MID_TERM:
                title = getString(R.string.mid_term_strategy_title);
                titleColor = Color.parseColor("#2196F3");
                emoji = "📈";
                break;
            case STRATEGY_LONG_TERM:
                title = getString(R.string.long_term_strategy_title);
                titleColor = Color.parseColor("#9C27B0");
                emoji = "🔮";
                break;
            default:
                title = getString(R.string.default_strategy_title);
                titleColor = Color.BLACK;
                emoji = "📊";
                break;
        }

        tvStrategyTitle.setText(emoji + " " + title);
        tvStrategyTitle.setTextColor(titleColor);

        boolean isSubscribed = subscriptionManager.isSubscribed();

        if (strategy != null) {
            displayBuySteps(layoutBuySteps, strategy.getBuySteps());
            updateTargetPrices();
            updateStopLoss();
            updateRiskReward();
            updateStrategyDetail();
            // 차트 업데이트
            updateChart();
        } else {
            tvTargetPrice.setText(getString(R.string.no_data));
            tvStopLoss.setText(getString(R.string.no_data));
            tvRiskReward.setText(getString(R.string.no_data));
            tvStrategyDetail.setText(getString(R.string.no_data));
        }

        if (!isSubscribed) {
            setupBlurredView();
        } else {
            setupClearView();
        }

        // 환율 정보 갱신
        if (exchangeRateManager.getUsdToKrwRate() <= 0) {
            exchangeRateManager.fetchExchangeRate(new ExchangeRateManager.OnExchangeRateListener() {
                @Override
                public void onExchangeRateUpdated(double rate) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            updateUI();
                            updateChart();
                        });
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("StrategyFragment", "환율 정보 로드 실패: " + errorMessage);
                }
            });
        }

        tvAdStatus = view.findViewById(R.id.tvAdStatus);
        btnWatchAd = view.findViewById(R.id.btnWatchAd);

        if (btnWatchAd != null) {
            btnWatchAd.setOnClickListener(v -> {
                showAdDialog();
            });
        }

        updateContentAccessUI();
        startAdTimer();
    }

    /**
     * 차트 초기 설정
     */
    private void setupChart() {
        if (strategyChart == null) return;

        // 차트 기본 설정
        strategyChart.getDescription().setEnabled(false);
        strategyChart.setTouchEnabled(true);
        strategyChart.setDragEnabled(true);
        strategyChart.setScaleEnabled(true);
        strategyChart.setDrawGridBackground(false);
        strategyChart.setPinchZoom(true);

        // 차트 그리기 순서 설정 (캔들스틱과 라인만 사용)
        strategyChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.CANDLE,
                CombinedChart.DrawOrder.LINE
        });

        // 차트 여백 설정
        strategyChart.setExtraOffsets(10f, 10f, 10f, 10f);
        strategyChart.setBackgroundColor(Color.parseColor("#1E1E1E"));

        // X축 설정 - 모든 기간에서 동일한 30일 라벨
        XAxis xAxis = strategyChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.parseColor("#40FFFFFF"));
        xAxis.setGridLineWidth(1f);
        xAxis.setTextColor(Color.parseColor("#CCCCCC"));
        xAxis.setTextSize(10f);
        xAxis.setLabelCount(7, false);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setSpaceMin(0.5f);
        xAxis.setSpaceMax(0.5f);

        // X축 라벨 포매터 - 30일 일봉으로 통일
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                int totalCandles = 30;

                if (index >= 0 && index < totalCandles) {
                    int daysAgo = totalCandles - index - 1;

                    if (daysAgo == 0) return "오늘";
                    if (daysAgo % 5 == 0) return daysAgo + "일전";
                }
                return "";
            }
        });

        // Y축 설정
        YAxis leftAxis = strategyChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#40FFFFFF"));
        leftAxis.setGridLineWidth(1f);
        leftAxis.setTextColor(Color.parseColor("#CCCCCC"));
        leftAxis.setTextSize(10f);
        leftAxis.setLabelCount(6, false);
        leftAxis.setSpaceTop(10f);
        leftAxis.setSpaceBottom(10f);

        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if ("$".equals(currencySymbol)) {
                    if (value >= 10000) {
                        return String.format("$%.0fK", value / 1000);
                    } else if (value >= 1000) {
                        return String.format("$%.1fK", value / 1000);
                    } else if (value >= 1) {
                        return String.format("$%.0f", value);
                    } else {
                        return String.format("$%.3f", value);
                    }
                } else {
                    return String.format("₩%,.0f", value);
                }
            }
        });

        YAxis rightAxis = strategyChart.getAxisRight();
        rightAxis.setEnabled(false);

        // 범례 비활성화 - 차트 아래에 별도 표시되므로 차트 내 범례 제거
        strategyChart.getLegend().setEnabled(false);
    }

    /**
     * 차트 데이터 업데이트 - 모든 기간에서 동일한 30일 일봉 차트
     */
    public void updateChart() {
        if (strategyChart == null || coinInfo == null) return;

        // 모든 기간에서 30일 일봉 데이터 가져오기
        getCandleDataAndUpdateChart();
    }

    /**
     * 차트 강제 새로고침 (탭 전환 시 호출)
     */
    public void forceUpdateChart() {
        Log.d("StrategyFragment", "forceUpdateChart 호출됨 - " + getStrategyTypeName());

        if (strategyChart == null) {
            Log.w("StrategyFragment", "strategyChart가 null입니다");
            return;
        }

        if (coinInfo == null) {
            Log.w("StrategyFragment", "coinInfo가 null입니다");
            return;
        }

        // 기존 차트 데이터 클리어
        strategyChart.clear();

        // 차트 다시 설정
        setupChart();

        // 새로운 데이터로 업데이트
        getCandleDataAndUpdateChart();

        Log.d("StrategyFragment", "차트 강제 새로고침 완료");
    }

    // ★ 차트 데이터만 갱신 (1분마다 자동 호출용)
    // UI 변경 없이 백그라운드에서 차트 데이터만 새로고침
    public void refreshChartData() {
        if (strategyChart == null || coinInfo == null) {
            Log.w("StrategyFragment", "refreshChartData: 차트 또는 코인 정보가 없음");
            return;
        }

        if (!isAdded() || getView() == null) {
            Log.w("StrategyFragment", "refreshChartData: 프래그먼트가 준비되지 않음");
            return;
        }

        Log.d("StrategyFragment", "차트 데이터 자동 갱신 시작: " + getStrategyTypeName());

        // 조용히 데이터만 갱신 (로딩 인디케이터 없이)
        getCandleDataAndUpdateChartSilently();
    }

    /**
     * 조용한 차트 데이터 갱신 (로딩 표시 없이) - 50일 데이터로 MA 계산
     */
    private void getCandleDataAndUpdateChartSilently() {
        if (coinInfo == null || coinInfo.getMarket() == null) return;

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        // MA 계산을 위해 100일 데이터 요청
        String interval = "1d";
        int limit = 100; // 100일로 확장

        Log.d("StrategyFragment", String.format("자동 차트 갱신: 기간=%s, 심볼=%s",
                getStrategyTypeName(), coinInfo.getSymbol()));

        apiService.getKlines(coinInfo.getMarket(), interval, limit).enqueue(new Callback<List<List<Object>>>() {
            @Override
            public void onResponse(@NonNull Call<List<List<Object>>> call, @NonNull Response<List<List<Object>>> response) {
                if (!isAdded() || strategyChart == null) return;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<List<Object>> klines = response.body();

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            try {
                                Log.d("StrategyFragment", String.format("자동 차트 데이터 수신: %d개 캔들", klines.size()));
                                createCombinedChartData(klines);
                                Log.d("StrategyFragment", "자동 차트 갱신 완료: " + getStrategyTypeName());
                            } catch (Exception e) {
                                Log.e("StrategyFragment", "자동 차트 갱신 중 오류: " + e.getMessage());
                            }
                        });
                    }
                } else {
                    Log.w("StrategyFragment", "자동 차트 갱신: 응답 데이터가 없음");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<List<Object>>> call, @NonNull Throwable t) {
                Log.w("StrategyFragment", "자동 차트 갱신 실패: " + t.getMessage());
            }
        });
    }

    /**
     * 캔들 데이터 가져와서 차트 업데이트 - 50일 데이터로 MA 계산, 최근 30일만 표시
     */
    private void getCandleDataAndUpdateChart() {
        if (coinInfo == null || coinInfo.getMarket() == null) return;

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        // MA 계산을 위해 100일 데이터 요청 (더 정확한 MA 계산을 위해)
        String interval = "1d"; // 일봉으로 통일
        int limit = 100; // 100일로 확장 (MA20 계산을 위해)

        Log.d("StrategyFragment", String.format("차트 데이터 요청: 기간=%s, 인터벌=%s, 개수=%d",
                getStrategyTypeName(), interval, limit));

        apiService.getKlines(coinInfo.getMarket(), interval, limit).enqueue(new Callback<List<List<Object>>>() {
            @Override
            public void onResponse(@NonNull Call<List<List<Object>>> call, @NonNull Response<List<List<Object>>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<List<Object>> klines = response.body();
                    Log.d("StrategyFragment", String.format("캔들 데이터 수신: %d개", klines.size()));
                    createCombinedChartData(klines);
                } else {
                    Log.e("StrategyFragment", "캔들 데이터 응답 실패 또는 빈 데이터");
                    createEmptyChart();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<List<Object>>> call, @NonNull Throwable t) {
                Log.e("StrategyFragment", "캔들 데이터 로드 실패: " + t.getMessage());
                createEmptyChart();
            }
        });
    }

    /**
     * 기간 타입 이름 반환
     */
    private String getStrategyTypeName() {
        switch (strategyType) {
            case STRATEGY_SHORT_TERM: return "단기";
            case STRATEGY_MID_TERM: return "중기";
            case STRATEGY_LONG_TERM: return "장기";
            default: return "알 수 없음";
        }
    }

    /**
     * 캔들스틱 + 골든크로스/데드크로스 포인트만 표시하는 차트 생성
     * 50일 데이터로 MA 계산, 최근 30일만 차트에 표시
     */
    private void createCombinedChartData(List<List<Object>> klines) {
        if (strategyChart == null || klines.isEmpty()) {
            Log.e("StrategyFragment", "차트가 null이거나 캔들 데이터가 비어있음");
            createEmptyChart();
            return;
        }

        Log.d("StrategyFragment", String.format("차트 데이터 생성 시작: %d개 캔들 (100일 데이터)", klines.size()));

        CombinedData combinedData = new CombinedData();

        // 1. 전체 100일 데이터로 close price 배열 생성 (MA 계산용)
        ArrayList<Float> allClosePrices = new ArrayList<>();
        for (int i = 0; i < klines.size(); i++) {
            List<Object> kline = klines.get(i);
            try {
                double close = Double.parseDouble(kline.get(4).toString());
                allClosePrices.add((float) close);
            } catch (Exception e) {
                Log.e("StrategyFragment", "전체 데이터 파싱 오류 (인덱스 " + i + "): " + e.getMessage());
                continue;
            }
        }

        // 2. 최근 30일 캔들스틱 데이터만 생성
        int displayDays = 30; // 표시할 일수
        int startIndex = Math.max(0, klines.size() - displayDays); // 최근 30일 시작 인덱스

        ArrayList<CandleEntry> candleEntries = new ArrayList<>();
        float minPrice = Float.MAX_VALUE;
        float maxPrice = Float.MIN_VALUE;

        for (int i = startIndex; i < klines.size(); i++) {
            List<Object> kline = klines.get(i);
            try {
                double open = Double.parseDouble(kline.get(1).toString());
                double high = Double.parseDouble(kline.get(2).toString());
                double low = Double.parseDouble(kline.get(3).toString());
                double close = Double.parseDouble(kline.get(4).toString());

                float openF = (float) open;
                float highF = (float) high;
                float lowF = (float) low;
                float closeF = (float) close;

                // 차트용 인덱스는 0부터 시작하도록 조정
                int chartIndex = i - startIndex;
                candleEntries.add(new CandleEntry(chartIndex, highF, lowF, openF, closeF));

                minPrice = Math.min(minPrice, lowF);
                maxPrice = Math.max(maxPrice, highF);

            } catch (Exception e) {
                Log.e("StrategyFragment", "캔들 데이터 파싱 오류 (인덱스 " + i + "): " + e.getMessage());
                continue;
            }
        }

        if (candleEntries.isEmpty()) {
            Log.e("StrategyFragment", "파싱된 캔들 데이터가 없음");
            createEmptyChart();
            return;
        }

        Log.d("StrategyFragment", String.format("최근 30일 캔들 엔트리 생성 완료: %d개, 가격 범위: %.2f ~ %.2f",
                candleEntries.size(), minPrice, maxPrice));

        // 캔들스틱 데이터셋 설정
        CandleDataSet candleDataSet = new CandleDataSet(candleEntries, "Price");
        candleDataSet.setShadowColor(Color.parseColor("#CCCCCC"));
        candleDataSet.setShadowWidth(1f);
        candleDataSet.setDecreasingColor(Color.parseColor("#FF4B6C")); // 하락 - 빨간색
        candleDataSet.setDecreasingPaintStyle(Paint.Style.FILL);
        candleDataSet.setIncreasingColor(Color.parseColor("#00C087")); // 상승 - 녹색
        candleDataSet.setIncreasingPaintStyle(Paint.Style.FILL);
        candleDataSet.setNeutralColor(Color.parseColor("#FFC107")); // 동일가 - 노란색
        candleDataSet.setDrawValues(false);
        candleDataSet.setBarSpace(0.1f);
        candleDataSet.setHighlightEnabled(true);
        candleDataSet.setHighLightColor(Color.WHITE);

        CandleData candleData = new CandleData(candleDataSet);
        combinedData.setData(candleData);

        // 3. 전체 50일 데이터로 이동평균선 계산 (표시하지 않음, 크로스 계산용만)
        ArrayList<ILineDataSet> lineDataSets = new ArrayList<>();

        // MA5 계산 (전체 데이터로)
        ArrayList<Entry> allMa5Entries = new ArrayList<>();
        for (int i = 4; i < allClosePrices.size(); i++) { // 5일부터 계산 가능
            float sum = 0;
            for (int j = i - 4; j <= i; j++) {
                sum += allClosePrices.get(j);
            }
            float ma5 = sum / 5;
            allMa5Entries.add(new Entry(i, ma5));
        }

        // MA20 계산 (전체 데이터로)
        ArrayList<Entry> allMa20Entries = new ArrayList<>();
        for (int i = 19; i < allClosePrices.size(); i++) { // 20일부터 계산 가능
            float sum = 0;
            for (int j = i - 19; j <= i; j++) {
                sum += allClosePrices.get(j);
            }
            float ma20 = sum / 20;
            allMa20Entries.add(new Entry(i, ma20));
        }

        Log.d("StrategyFragment", String.format("MA 계산 완료 - MA5: %d개, MA20: %d개",
                allMa5Entries.size(), allMa20Entries.size()));

        // 🔍 디버깅을 위해 MA 라인을 임시로 표시 (크로스 확인용)
        // ⚠️ 최종 버전에서는 제거할 수 있음

        // 최근 30일 범위의 MA5 라인 생성 (얇은 파란색)
        ArrayList<Entry> displayMa5Entries = new ArrayList<>();
        for (int i = startIndex; i < klines.size(); i++) {
            int ma5Index = i - 4; // MA5는 인덱스 4부터 시작
            if (ma5Index >= 0 && ma5Index < allMa5Entries.size()) {
                int chartIndex = i - startIndex;
                float ma5Value = allMa5Entries.get(ma5Index).getY();
                displayMa5Entries.add(new Entry(chartIndex, ma5Value));

                minPrice = Math.min(minPrice, ma5Value);
                maxPrice = Math.max(maxPrice, ma5Value);
            }
        }

        // 최근 30일 범위의 MA20 라인 생성 (얇은 주황색)
        ArrayList<Entry> displayMa20Entries = new ArrayList<>();
        for (int i = startIndex; i < klines.size(); i++) {
            int ma20Index = i - 19; // MA20은 인덱스 19부터 시작
            if (ma20Index >= 0 && ma20Index < allMa20Entries.size()) {
                int chartIndex = i - startIndex;
                float ma20Value = allMa20Entries.get(ma20Index).getY();
                displayMa20Entries.add(new Entry(chartIndex, ma20Value));

                minPrice = Math.min(minPrice, ma20Value);
                maxPrice = Math.max(maxPrice, ma20Value);
            }
        }

        // MA5 라인 추가 (얇은 파란색 라인) - 디버깅용
        if (!displayMa5Entries.isEmpty()) {
            LineDataSet ma5DataSet = new LineDataSet(displayMa5Entries, "MA5");
            ma5DataSet.setColor(Color.parseColor("#2196F3")); // 파란색
            ma5DataSet.setLineWidth(1f);
            ma5DataSet.setDrawCircles(false);
            ma5DataSet.setDrawValues(false);
            ma5DataSet.setHighlightEnabled(false);
            lineDataSets.add(ma5DataSet);

            Log.d("StrategyFragment", String.format("🔍 MA5 라인 추가 (디버깅용): %d개 포인트", displayMa5Entries.size()));
        }

        // MA20 라인 추가 (얇은 주황색 라인) - 디버깅용
        if (!displayMa20Entries.isEmpty()) {
            LineDataSet ma20DataSet = new LineDataSet(displayMa20Entries, "MA20");
            ma20DataSet.setColor(Color.parseColor("#FF9800")); // 주황색
            ma20DataSet.setLineWidth(1f);
            ma20DataSet.setDrawCircles(false);
            ma20DataSet.setDrawValues(false);
            ma20DataSet.setHighlightEnabled(false);
            lineDataSets.add(ma20DataSet);

            Log.d("StrategyFragment", String.format("🔍 MA20 라인 추가 (디버깅용): %d개 포인트", displayMa20Entries.size()));
        }

        // 4. 골든크로스/데드크로스 시그널 포인트 계산 (전체 데이터에서)
        if (allMa5Entries.size() > 1 && allMa20Entries.size() > 1) {
            ArrayList<Entry> goldenCrossEntries = new ArrayList<>();
            ArrayList<Entry> deathCrossEntries = new ArrayList<>();

            Log.d("StrategyFragment", String.format("🔍 크로스 감지 시작 - 전체 데이터: %d일, 표시 시작: %d일째부터",
                    klines.size(), startIndex));

            // 전체 데이터에서 크로스 포인트 찾기
            int crossCount = 0;
            for (int dataIndex = 20; dataIndex < klines.size() - 1; dataIndex++) { // 20부터 시작 (MA20 안정화)
                int ma5Index = dataIndex - 4;
                int ma20Index = dataIndex - 19;

                int ma5NextIndex = ma5Index + 1;
                int ma20NextIndex = ma20Index + 1;

                if (ma5Index >= 0 && ma5NextIndex < allMa5Entries.size() &&
                        ma20Index >= 0 && ma20NextIndex < allMa20Entries.size()) {

                    float ma5Current = allMa5Entries.get(ma5Index).getY();
                    float ma5Next = allMa5Entries.get(ma5NextIndex).getY();
                    float ma20Current = allMa20Entries.get(ma20Index).getY();
                    float ma20Next = allMa20Entries.get(ma20NextIndex).getY();

                    // 모든 MA 값들을 로그로 출력 (처음 5개와 마지막 5개만)
                    if (dataIndex <= 25 || dataIndex >= klines.size() - 6) {
                        Log.d("StrategyFragment", String.format("📊 일자=%d, MA5: %.2f→%.2f, MA20: %.2f→%.2f",
                                dataIndex, ma5Current, ma5Next, ma20Current, ma20Next));
                    }

                    // 골든크로스 검사 (더 관대한 조건으로 변경)
                    if (ma5Current < ma20Current && ma5Next > ma20Next) {
                        crossCount++;
                        Log.d("StrategyFragment", String.format("🟡 골든크로스 감지! 일자=%d, MA5: %.2f→%.2f, MA20: %.2f→%.2f",
                                dataIndex + 1, ma5Current, ma5Next, ma20Current, ma20Next));

                        // 최근 30일 범위 내의 크로스만 차트에 표시
                        if (dataIndex + 1 >= startIndex) {
                            int chartIndex = (dataIndex + 1) - startIndex;

                            // 해당 날짜의 캔들 데이터 가져오기
                            try {
                                List<Object> crossKline = klines.get(dataIndex + 1);
                                double high = Double.parseDouble(crossKline.get(2).toString());
                                double low = Double.parseDouble(crossKline.get(3).toString());
                                float candleHigh = (float) high;
                                float candleLow = (float) low;

                                // 골든크로스는 캔들 아래쪽에 표시 (Low 값에서 캔들 크기의 10% 아래)
                                float candleSize = candleHigh - candleLow;
                                float offset = Math.max(candleSize * 0.3f, candleLow * 0.005f); // 캔들 크기의 30% 또는 가격의 0.5% 중 큰 값
                                float goldenCrossY = candleLow - offset;

                                goldenCrossEntries.add(new Entry(chartIndex, goldenCrossY));

                                minPrice = Math.min(minPrice, goldenCrossY);

                                Log.d("StrategyFragment", String.format("✅ 골든크로스 차트 추가: 원본일자=%d, 차트인덱스=%d, 캔들Low=%.2f, 표시위치=%.2f, 오프셋=%.2f",
                                        dataIndex + 1, chartIndex, candleLow, goldenCrossY, offset));
                            } catch (Exception e) {
                                Log.e("StrategyFragment", "골든크로스 캔들 데이터 파싱 오류: " + e.getMessage());
                            }
                        } else {
                            Log.d("StrategyFragment", String.format("⚠️ 골든크로스는 표시 범위 밖 (일자=%d, 범위=%d 이후)", dataIndex + 1, startIndex));
                        }
                    }
                    // 데드크로스 검사 (더 관대한 조건으로 변경)
                    else if (ma5Current > ma20Current && ma5Next < ma20Next) {
                        crossCount++;
                        Log.d("StrategyFragment", String.format("🔴 데드크로스 감지! 일자=%d, MA5: %.2f→%.2f, MA20: %.2f→%.2f",
                                dataIndex + 1, ma5Current, ma5Next, ma20Current, ma20Next));

                        // 최근 30일 범위 내의 크로스만 차트에 표시
                        if (dataIndex + 1 >= startIndex) {
                            int chartIndex = (dataIndex + 1) - startIndex;

                            // 해당 날짜의 캔들 데이터 가져오기
                            try {
                                List<Object> crossKline = klines.get(dataIndex + 1);
                                double high = Double.parseDouble(crossKline.get(2).toString());
                                double low = Double.parseDouble(crossKline.get(3).toString());
                                float candleHigh = (float) high;
                                float candleLow = (float) low;

                                // 데드크로스는 캔들 위쪽에 표시 (High 값에서 캔들 크기의 10% 위)
                                float candleSize = candleHigh - candleLow;
                                float offset = Math.max(candleSize * 0.3f, candleHigh * 0.005f); // 캔들 크기의 30% 또는 가격의 0.5% 중 큰 값
                                float deathCrossY = candleHigh + offset;

                                deathCrossEntries.add(new Entry(chartIndex, deathCrossY));

                                maxPrice = Math.max(maxPrice, deathCrossY);

                                Log.d("StrategyFragment", String.format("✅ 데드크로스 차트 추가: 원본일자=%d, 차트인덱스=%d, 캔들High=%.2f, 표시위치=%.2f, 오프셋=%.2f",
                                        dataIndex + 1, chartIndex, candleHigh, deathCrossY, offset));
                            } catch (Exception e) {
                                Log.e("StrategyFragment", "데드크로스 캔들 데이터 파싱 오류: " + e.getMessage());
                            }
                        } else {
                            Log.d("StrategyFragment", String.format("⚠️ 데드크로스는 표시 범위 밖 (일자=%d, 범위=%d 이후)", dataIndex + 1, startIndex));
                        }
                    }
                }
            }

            Log.d("StrategyFragment", String.format("🔍 전체 크로스 감지 완료: 총 %d개 크로스 발견", crossCount));

            // 골든크로스 포인트 추가 (큰 황금색 원, 캔들 아래쪽)
            if (!goldenCrossEntries.isEmpty()) {
                LineDataSet goldenCrossDataSet = new LineDataSet(goldenCrossEntries, "Golden Cross");
                goldenCrossDataSet.setColor(Color.TRANSPARENT);
                goldenCrossDataSet.setDrawCircles(true);
                goldenCrossDataSet.setCircleColor(Color.parseColor("#FFD700")); // 골드 색상 외곽
                goldenCrossDataSet.setCircleRadius(14f); // 더 크게
                goldenCrossDataSet.setCircleHoleRadius(9f);
                goldenCrossDataSet.setCircleHoleColor(Color.parseColor("#4CAF50")); // 녹색 중심
                goldenCrossDataSet.setDrawValues(false);
                goldenCrossDataSet.setHighlightEnabled(false);
                goldenCrossDataSet.setLineWidth(0f);
                lineDataSets.add(goldenCrossDataSet);

                Log.d("StrategyFragment", String.format("✅ 골든크로스 포인트 %d개 추가됨 (캔들 아래쪽)", goldenCrossEntries.size()));
            } else {
                Log.d("StrategyFragment", "⚠️ 골든크로스 포인트 없음 (최근 30일 범위)");
            }

            // 데드크로스 포인트 추가 (큰 검은색 원, 캔들 위쪽)
            if (!deathCrossEntries.isEmpty()) {
                LineDataSet deathCrossDataSet = new LineDataSet(deathCrossEntries, "Death Cross");
                deathCrossDataSet.setColor(Color.TRANSPARENT);
                deathCrossDataSet.setDrawCircles(true);
                deathCrossDataSet.setCircleColor(Color.parseColor("#424242")); // 어두운 회색 외곽
                deathCrossDataSet.setCircleRadius(14f); // 더 크게
                deathCrossDataSet.setCircleHoleRadius(9f);
                deathCrossDataSet.setCircleHoleColor(Color.parseColor("#F44336")); // 빨간색 중심
                deathCrossDataSet.setDrawValues(false);
                deathCrossDataSet.setHighlightEnabled(false);
                deathCrossDataSet.setLineWidth(0f);
                lineDataSets.add(deathCrossDataSet);

                Log.d("StrategyFragment", String.format("✅ 데드크로스 포인트 %d개 추가됨 (캔들 위쪽)", deathCrossEntries.size()));
            } else {
                Log.d("StrategyFragment", "⚠️ 데드크로스 포인트 없음 (최근 30일 범위)");
            }
        }

        // 5. 전략이 있는 경우 지지선/저항선 추가 (최근 30일 범위로 조정)
        if (strategy != null) {
            // 지지선들 (녹색, 점선)
            if (strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty()) {
                for (int stepIndex = 0; stepIndex < strategy.getBuySteps().size(); stepIndex++) {
                    AnalysisResult.Strategy.TradingStep step = strategy.getBuySteps().get(stepIndex);
                    ArrayList<Entry> supportEntries = new ArrayList<>();

                    float supportPrice = (float) step.getPrice();
                    for (int i = 0; i < displayDays; i++) { // 30일 범위로 조정
                        supportEntries.add(new Entry(i, supportPrice));
                    }

                    LineDataSet supportDataSet = new LineDataSet(supportEntries, "Support " + (stepIndex + 1));
                    supportDataSet.setColor(Color.parseColor("#4CAF50"));
                    supportDataSet.setLineWidth(2f);
                    supportDataSet.setDrawCircles(false);
                    supportDataSet.setDrawValues(false);
                    supportDataSet.enableDashedLine(15f, 8f, 0f);
                    supportDataSet.setDrawFilled(false);
                    supportDataSet.setHighlightEnabled(false);
                    lineDataSets.add(supportDataSet);

                    minPrice = Math.min(minPrice, supportPrice);
                    maxPrice = Math.max(maxPrice, supportPrice);

                    Log.d("StrategyFragment", String.format("지지선 %d 추가: %.2f", stepIndex + 1, supportPrice));
                }
            }

            // 저항선들 (빨간색, 점선)
            if (strategy.getTargetPrices() != null && !strategy.getTargetPrices().isEmpty()) {
                for (int targetIndex = 0; targetIndex < strategy.getTargetPrices().size(); targetIndex++) {
                    double targetPrice = strategy.getTargetPrices().get(targetIndex);
                    ArrayList<Entry> resistanceEntries = new ArrayList<>();

                    float resistancePrice = (float) targetPrice;
                    for (int i = 0; i < displayDays; i++) { // 30일 범위로 조정
                        resistanceEntries.add(new Entry(i, resistancePrice));
                    }

                    LineDataSet resistanceDataSet = new LineDataSet(resistanceEntries, "Resistance " + (targetIndex + 1));
                    resistanceDataSet.setColor(Color.parseColor("#F44336"));
                    resistanceDataSet.setLineWidth(2f);
                    resistanceDataSet.setDrawCircles(false);
                    resistanceDataSet.setDrawValues(false);
                    resistanceDataSet.enableDashedLine(15f, 8f, 0f);
                    resistanceDataSet.setDrawFilled(false);
                    resistanceDataSet.setHighlightEnabled(false);
                    lineDataSets.add(resistanceDataSet);

                    minPrice = Math.min(minPrice, resistancePrice);
                    maxPrice = Math.max(maxPrice, resistancePrice);

                    Log.d("StrategyFragment", String.format("저항선 %d 추가: %.2f", targetIndex + 1, resistancePrice));
                }
            }

            // 손절매 라인 (주황색, 점선)
            if (strategy.getStopLoss() > 0) {
                ArrayList<Entry> stopLossEntries = new ArrayList<>();
                float stopLossPrice = (float) strategy.getStopLoss();

                for (int i = 0; i < displayDays; i++) { // 30일 범위로 조정
                    stopLossEntries.add(new Entry(i, stopLossPrice));
                }

                LineDataSet stopLossDataSet = new LineDataSet(stopLossEntries, "Stop Loss");
                stopLossDataSet.setColor(Color.parseColor("#FF9800"));
                stopLossDataSet.setLineWidth(2f);
                stopLossDataSet.setDrawCircles(false);
                stopLossDataSet.setDrawValues(false);
                stopLossDataSet.enableDashedLine(20f, 10f, 0f);
                stopLossDataSet.setDrawFilled(false);
                stopLossDataSet.setHighlightEnabled(false);
                lineDataSets.add(stopLossDataSet);

                minPrice = Math.min(minPrice, stopLossPrice);
                maxPrice = Math.max(maxPrice, stopLossPrice);

                Log.d("StrategyFragment", String.format("손절매 라인 추가: %.2f", stopLossPrice));
            }
        }

        // 6. 라인 데이터 추가
        if (!lineDataSets.isEmpty()) {
            LineData lineData = new LineData(lineDataSets);
            combinedData.setData(lineData);
            Log.d("StrategyFragment", String.format("라인 데이터 추가: %d개 (MA 라인 + 크로스 포인트 + 지지/저항선)", lineDataSets.size()));
        }

        // 7. Y축 범위 설정
        float padding = (maxPrice - minPrice) * 0.08f;
        strategyChart.getAxisLeft().setAxisMinimum(minPrice - padding);
        strategyChart.getAxisLeft().setAxisMaximum(maxPrice + padding);

        // 8. X축 범위 설정 (30일로 조정)
        strategyChart.setVisibleXRangeMaximum(displayDays);
        strategyChart.setVisibleXRangeMinimum(displayDays);

        // 9. 차트 그리기 순서 설정
        strategyChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.CANDLE,
                CombinedChart.DrawOrder.LINE
        });

        // 10. 차트에 데이터 설정 및 업데이트
        strategyChart.setData(combinedData);
        strategyChart.fitScreen();
        strategyChart.invalidate();

        Log.d("StrategyFragment", "✅ 100일 데이터로 계산된 골든크로스/데드크로스 포인트 + MA 라인 (디버깅용) 차트 완료");
    }



    /**
     * 빈 차트 생성 (데이터 로드 실패시)
     */
    private void createEmptyChart() {
        if (strategyChart == null) return;

        Log.d("StrategyFragment", "빈 차트 생성");

        // 30개의 더미 캔들 데이터 생성
        ArrayList<CandleEntry> emptyEntries = new ArrayList<>();
        float basePrice = 50000f; // 기본 가격

        for (int i = 0; i < 30; i++) {
            // 약간의 변동성을 가진 더미 데이터
            float priceVariation = (float)(Math.random() * 2000 - 1000); // ±1000
            float dayPrice = basePrice + priceVariation;

            float high = dayPrice + (float)(Math.random() * 1000);
            float low = dayPrice - (float)(Math.random() * 1000);
            float open = dayPrice + (float)(Math.random() * 500 - 250);
            float close = dayPrice + (float)(Math.random() * 500 - 250);

            emptyEntries.add(new CandleEntry(i, high, low, open, close));
        }

        CandleDataSet emptyDataSet = new CandleDataSet(emptyEntries, "데이터 로드 중...");
        emptyDataSet.setColor(Color.GRAY);
        emptyDataSet.setShadowColor(Color.DKGRAY);
        emptyDataSet.setShadowWidth(1f); // 빈 차트 캔들 심지 두께
        emptyDataSet.setDecreasingColor(Color.parseColor("#66FF4B6C"));
        emptyDataSet.setIncreasingColor(Color.parseColor("#6600C087"));
        emptyDataSet.setNeutralColor(Color.parseColor("#66FFC107"));
        emptyDataSet.setDrawValues(false);
        emptyDataSet.setBarSpace(0.1f);

        CandleData emptyData = new CandleData(emptyDataSet);
        CombinedData combinedData = new CombinedData();
        combinedData.setData(emptyData);

        strategyChart.setData(combinedData);
        strategyChart.invalidate();

        Log.d("StrategyFragment", "빈 차트 생성 완료");
    }

    // 나머지 메서드들...
    private void setupBlurredView() {
        blurOverlay.setVisibility(View.VISIBLE);
        pixelatedOverlay.setVisibility(View.VISIBLE);
        additionalBlurLayer.setVisibility(View.VISIBLE);
        additionalBlurLayer.setBackgroundColor(Color.parseColor("#B3000000"));
        contentArea.setAlpha(0.5f);

        if (strategy != null) {
            tvTargetPrice.setText(getString(R.string.masked_content));
            tvStopLoss.setText(getString(R.string.masked_content));
            tvRiskReward.setText(getString(R.string.masked_content_short));
            tvStrategyDetail.setText(getString(R.string.masked_content));

            if (strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty()) {
                displayFirstBuyStepWithBlur(layoutBuySteps, strategy.getBuySteps().get(0));
            }
        }

        btnSubscribe.setVisibility(View.VISIBLE);
        btnSubscribe.setElevation(24f);
        btnSubscribe.setBackgroundResource(R.drawable.glowing_button);
        btnSubscribe.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
            startActivity(intent);
        });
    }

    private void setupClearView() {
        blurOverlay.setVisibility(View.GONE);
        pixelatedOverlay.setVisibility(View.GONE);
        additionalBlurLayer.setVisibility(View.GONE);
        contentArea.setAlpha(1.0f);
        btnSubscribe.setVisibility(View.GONE);
    }

    @Override
    public void onDestroyView() {
        stopAdTimer();
        super.onDestroyView();
    }

    private void showAnalysisInfoDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_analysis_info, null);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        Button btnOk = dialogView.findViewById(R.id.btnOk);
        btnOk.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

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
            updateContentAccessUI();

            Fragment parentFragment = getParentFragment();
            if (parentFragment instanceof AnalysisFragment) {
                ((AnalysisFragment) parentFragment).refreshAllUIs();
            }
        });

        dialog.show(getParentFragmentManager(), "ad_dialog");
    }

    public void updateContentAccessUI() {
        if (getView() == null) {
            if (isAdded()) {
                Log.d("StrategyFragment", getString(R.string.view_not_created_log));
            } else {
                Log.d("StrategyFragment", "updateContentAccessUI: View is not created yet");
            }
            return;
        }

        if (blurOverlay == null || pixelatedOverlay == null || additionalBlurLayer == null ||
                contentArea == null || btnSubscribe == null || btnWatchAd == null || tvAdStatus == null) {
            if (isAdded()) {
                Log.d("StrategyFragment", getString(R.string.ui_elements_null_log));
            } else {
                Log.d("StrategyFragment", "updateContentAccessUI: Some UI elements are null");
            }
            return;
        }

        if (coinInfo == null) {
            if (isAdded()) {
                Log.w("StrategyFragment", getString(R.string.update_content_access_ui_warning));
            } else {
                Log.w("StrategyFragment", "updateContentAccessUI: coinInfo is null");
            }

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

        if (coinInfo != null && coinInfo.getSymbol() != null) {
            hasAdPermission = adManager.hasActiveAdPermission(coinInfo.getSymbol());
            isPremiumCoin = coinInfo.isPremium();
        }

        if (!isSubscribed && !hasAdPermission) {
            tvTargetPrice.setText(getString(R.string.masked_content));
            tvStopLoss.setText(getString(R.string.masked_content));
            tvRiskReward.setText(getString(R.string.masked_content_short));
            tvStrategyDetail.setText(getString(R.string.masked_strategy_content));

            if (strategy != null && strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty()) {
                displayFirstBuyStepWithBlur(layoutBuySteps, strategy.getBuySteps().get(0));
            }
        }

        if (isSubscribed || hasAdPermission) {
            blurOverlay.setVisibility(View.GONE);
            pixelatedOverlay.setVisibility(View.GONE);
            additionalBlurLayer.setVisibility(View.GONE);
            contentArea.setAlpha(1.0f);
            btnSubscribe.setVisibility(View.GONE);
            btnWatchAd.setVisibility(View.GONE);

            if (!isSubscribed && hasAdPermission) {
                int remainingMinutes = adManager.getRemainingMinutes(coinInfo.getSymbol());
                tvAdStatus.setVisibility(View.VISIBLE);
                tvAdStatus.setText(getString(R.string.ad_remaining_minutes_format, remainingMinutes));
            } else {
                tvAdStatus.setVisibility(View.GONE);
            }

            if (strategy != null) {
                if (strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty() && layoutBuySteps != null) {
                    displayBuySteps(layoutBuySteps, strategy.getBuySteps());
                }
                updateTargetPrices();
                updateStopLoss();
                updateRiskReward();
                updateStrategyDetail();
                // 차트도 업데이트
                updateChart();
            }
        } else {
            blurOverlay.setVisibility(View.VISIBLE);
            pixelatedOverlay.setVisibility(View.VISIBLE);
            additionalBlurLayer.setVisibility(View.VISIBLE);
            contentArea.setAlpha(0.5f);
            btnSubscribe.setVisibility(View.VISIBLE);
            btnWatchAd.setVisibility(isPremiumCoin ? View.GONE : View.VISIBLE);
            tvAdStatus.setVisibility(View.GONE);

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) btnWatchAd.getLayoutParams();
            if (params != null) {
                params.topMargin = (int) (80 * getResources().getDisplayMetrics().density);
                btnWatchAd.setLayoutParams(params);
            }

            tvTargetPrice.setText(getString(R.string.masked_content));
            tvStopLoss.setText(getString(R.string.masked_content));
            tvRiskReward.setText(getString(R.string.masked_content_short));
            tvStrategyDetail.setText(getString(R.string.masked_content));
        }
    }

    private void updateTargetPrices() {
        if (tvTargetPrice == null || strategy == null) return;

        if (strategy.getTargetPrices() != null && !strategy.getTargetPrices().isEmpty()) {
            StringBuilder targetPrices = new StringBuilder();
            for (int i = 0; i < strategy.getTargetPrices().size(); i++) {
                double targetPrice = strategy.getTargetPrices().get(i);
                if (i > 0) {
                    targetPrices.append("<br>");
                }

                String basePrice = String.format(Locale.getDefault(), "%s%,.2f",
                        currencySymbol, targetPrice);

                String displayPrice;
                if ("$".equals(currencySymbol) && exchangeRateManager.getUsdToKrwRate() > 0) {
                    double krwPrice = exchangeRateManager.convertUsdToKrw(targetPrice);
                    displayPrice = String.format("%s (₩%,.0f)", basePrice, krwPrice);
                } else {
                    displayPrice = basePrice;
                }

                String targetLabel = String.format(Locale.getDefault(),
                        getString(R.string.target_price_format), i + 1, displayPrice);

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
    }

    private void updateStopLoss() {
        if (tvStopLoss == null || strategy == null) return;

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
    }

    private void updateRiskReward() {
        if (tvRiskReward == null || strategy == null) return;

        if (strategy.getRiskRewardRatio() > 0) {
            String colorCode;
            if (strategy.getRiskRewardRatio() >= 3.0) {
                colorCode = "#4CAF50";
            } else if (strategy.getRiskRewardRatio() >= 2.0) {
                colorCode = "#FF9800";
            } else {
                colorCode = "#F44336";
            }

            String rrText = String.format(Locale.getDefault(), "%.1f:1", strategy.getRiskRewardRatio());
            tvRiskReward.setText(Html.fromHtml("<font color='" + colorCode + "'><b>" + rrText + "</b></font>", Html.FROM_HTML_MODE_LEGACY));
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

    private void startAdTimer() {
        adTimerRunnable = new Runnable() {
            @Override
            public void run() {
                updateContentAccessUI();
                adTimerHandler.postDelayed(this, 60000);
            }
        };

        adTimerHandler.post(adTimerRunnable);
    }

    private void stopAdTimer() {
        if (adTimerHandler != null && adTimerRunnable != null) {
            adTimerHandler.removeCallbacks(adTimerRunnable);
        }
    }

    private void displayFirstBuyStepWithBlur(LinearLayout container, AnalysisResult.Strategy.TradingStep step) {
        container.removeAllViews();

        if (step == null) {
            return;
        }

        View itemView = getLayoutInflater().inflate(R.layout.item_buy_step, null, false);

        TextView tvBuyStepTitle = itemView.findViewById(R.id.tvBuyStepTitle);
        TextView tvBuyStepPercentage = itemView.findViewById(R.id.tvBuyStepPercentage);
        TextView tvBuyStepDescription = itemView.findViewById(R.id.tvBuyStepDescription);

        String emoji = "1️⃣ ";
        String entryPoint = getString(R.string.entry_point);
        String maskedPrice = "****";
        String title = emoji + entryPoint + ": " + maskedPrice;

        tvBuyStepTitle.setText(title);
        tvBuyStepTitle.setTextColor(Color.parseColor("#4CAF50"));

        tvBuyStepPercentage.setVisibility(View.GONE);
        tvBuyStepDescription.setText(getString(R.string.masked_strategy_content));

        itemView.setAlpha(0.3f);
        container.addView(itemView);

        TextView tvMore = new TextView(getContext());
        tvMore.setText(getString(R.string.see_more_strategies));
        tvMore.setTextSize(12);
        tvMore.setTypeface(null, Typeface.ITALIC);
        tvMore.setTextColor(Color.GRAY);
        tvMore.setPadding(0, 16, 0, 16);
        container.addView(tvMore);
    }

    private String highlightStrategyText(String text) {
        if (text == null || text.isEmpty()) return "";

        text = text.replaceAll("(?i)\\b(매수|진입|분할매수)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(매도|이익실현|수익실현)\\b", "<font color='#FF9800'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(손절매|손절)\\b", "<font color='#F44336'><b>$1</b></font>");

        text = text.replaceAll("(?i)\\b(단기|24시간)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(중기|1주일)\\b", "<font color='#2196F3'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(장기|1개월)\\b", "<font color='#9C27B0'><b>$1</b></font>");

        text = text.replaceAll("(?i)\\b(상승|오름|증가|반등)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(하락|내림|감소|조정)\\b", "<font color='#F44336'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(횡보|박스권|보합)\\b", "<font color='#FFC107'><b>$1</b></font>");

        text = text.replaceAll("(?i)\\b(지지선|지지대|바닥)\\b", "<font color='#4CAF50'><b>$1</b></font>");
        text = text.replaceAll("(?i)\\b(저항선|저항대|고점)\\b", "<font color='#F44336'><b>$1</b></font>");

        return text;
    }

    private void displayBuySteps(LinearLayout container, List<AnalysisResult.Strategy.TradingStep> buySteps) {
        container.removeAllViews();

        String currentLanguage = getResources().getConfiguration().locale.getLanguage();
        boolean isKorean = "ko".equals(currentLanguage);

        if (buySteps == null || buySteps.isEmpty()) {
            TextView tvEmpty = new TextView(getContext());
            tvEmpty.setText(getString(R.string.no_appropriate_buy_time));
            tvEmpty.setTextColor(Color.parseColor("#FF9800"));
            container.addView(tvEmpty);
            return;
        }

        for (int i = 0; i < buySteps.size(); i++) {
            AnalysisResult.Strategy.TradingStep step = buySteps.get(i);
            View itemView = getLayoutInflater().inflate(R.layout.item_buy_step, null, false);

            TextView tvBuyStepTitle = itemView.findViewById(R.id.tvBuyStepTitle);
            TextView tvBuyStepPercentage = itemView.findViewById(R.id.tvBuyStepPercentage);
            TextView tvBuyStepDescription = itemView.findViewById(R.id.tvBuyStepDescription);

            int titleColor;
            String emoji;
            if (i == 0) {
                titleColor = Color.parseColor("#4CAF50");
                emoji = "1️⃣ ";
            } else if (i == 1) {
                titleColor = Color.parseColor("#2196F3");
                emoji = "2️⃣ ";
            } else {
                titleColor = Color.parseColor("#9C27B0");
                emoji = "3️⃣ ";
            }

            double price = step.getPrice();
            String formattedUsdPrice = String.format("%s%.2f", currencySymbol, price);
            String formattedPrice;

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

            if (step.getDescription() != null && !step.getDescription().isEmpty()) {
                String description = highlightStrategyText(step.getDescription());
                tvBuyStepDescription.setText(Html.fromHtml(description, Html.FROM_HTML_MODE_LEGACY));
            } else {
                tvBuyStepDescription.setText("");
            }

            CardView cardView = new CardView(getContext());
            cardView.setRadius(16f);
            cardView.setCardElevation(0f);
            cardView.setUseCompatPadding(false);

            int backgroundColor;
            if (i == 0) {
                backgroundColor = Color.parseColor("#104CAF50");
            } else if (i == 1) {
                backgroundColor = Color.parseColor("#102196F3");
            } else {
                backgroundColor = Color.parseColor("#109C27B0");
            }
            cardView.setCardBackgroundColor(backgroundColor);
            cardView.setForeground(null);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 8, 0, 8);
            cardView.setLayoutParams(params);

            cardView.addView(itemView);
            container.addView(cardView);
        }
    }

    private void updateUI() {
        if (strategy != null) {
            displayBuySteps(layoutBuySteps, strategy.getBuySteps());
            updateTargetPrices();
            updateStopLoss();
            updateRiskReward();
            updateStrategyDetail();
            updateChart();
        }
    }

    public void setStrategy(AnalysisResult.Strategy strategy) {
        this.strategy = strategy;

        if (getView() != null) {
            onViewCreated(getView(), null);
        }
    }
}