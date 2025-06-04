package com.coinsense.cryptoanalysisai.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.DataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.interfaces.datasets.IScatterDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.renderer.scatter.IShapeRenderer;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.google.android.material.tabs.TabLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.github.mikephil.charting.renderer.scatter.IShapeRenderer;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

public class StrategyFragment extends Fragment {

    private static final String ARG_STRATEGY_TYPE = "strategy_type";
    private static final String ARG_CURRENCY_SYMBOL = "currency_symbol";

    public static final int STRATEGY_SHORT_TERM = 0;
    public static final int STRATEGY_MID_TERM = 1;
    public static final int STRATEGY_LONG_TERM = 2;

    // ★ 차트 기간 타입 추가
    public static final int CHART_INTERVAL_1H = 0;
    public static final int CHART_INTERVAL_4H = 1;
    public static final int CHART_INTERVAL_1D = 2;

    private int strategyType;
    private String currencySymbol;
    private AnalysisResult.Strategy strategy;
    private SubscriptionManager subscriptionManager;

    // ★ 현재 선택된 차트 기간 변수 추가
    private int currentChartInterval = CHART_INTERVAL_1D; // 기본값: 1일봉

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

    // ★ 새로 추가된 UI 요소들
    private TabLayout tabsChartInterval;
    private TextView tvMovingAverageInfo;

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

    private TextView tvRecentCross; // 📍 최근 크로스 정보 표시용 TextView 추가

    // 최근 크로스 정보 저장용 변수들
    private String recentCrossType = null; // "GOLDEN" 또는 "DEATH"
    private int recentCrossDaysAgo = -1;   // 며칠 전인지

    private List<List<Object>> currentKlinesData = new ArrayList<>(); // 현재 차트의 kline 데이터 저장


    // StrategyFragment 클래스 안에 추가
    public static class RotatedTriangleRenderer implements IShapeRenderer {
        private final boolean inverted;

        public RotatedTriangleRenderer(boolean inverted) {
            this.inverted = inverted;
        }

        @Override
        public void renderShape(Canvas c, IScatterDataSet dataSet, ViewPortHandler viewPortHandler,
                                float posX, float posY, Paint renderPaint) {

            // IScatterDataSet에서 크기 가져오기
            final float shapeSize = dataSet.getScatterShapeSize();

            // 캔버스 저장
            c.save();

            if (inverted) {
                // 180도 회전 (데드크로스용)
                c.rotate(180f, posX, posY);
            }

            // 기본 삼각형 그리기
            final float shapeHalf = shapeSize / 2f;

            Path triangle = new Path();
            triangle.moveTo(posX, posY - shapeHalf); // 위쪽 꼭짓점
            triangle.lineTo(posX - shapeHalf, posY + shapeHalf); // 왼쪽 아래
            triangle.lineTo(posX + shapeHalf, posY + shapeHalf); // 오른쪽 아래
            triangle.close();

            c.drawPath(triangle, renderPaint);

            // 캔버스 복원
            c.restore();
        }
    }

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

        // ★ 새로 추가된 UI 요소들 초기화
        tabsChartInterval = view.findViewById(R.id.tabsChartInterval);
        tvMovingAverageInfo = view.findViewById(R.id.tvMovingAverageInfo);

        // 📍 최근 크로스 TextView 초기화 추가
        tvRecentCross = view.findViewById(R.id.tvRecentCross);

        // 차트 초기화
        strategyChart = view.findViewById(R.id.strategyChart);
        setupChart();

        // ★ 차트 기간 탭 설정
        setupChartIntervalTabs();

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
     * ★ 차트 기간 선택 탭 설정 - 중복 추가 방지
     */
    private void setupChartIntervalTabs() {
        if (tabsChartInterval == null) return;

        // ★ 기존 탭들을 모두 제거 (중복 방지)
        tabsChartInterval.removeAllTabs();

        // ★ 기존 리스너도 제거 (메모리 누수 방지)
        tabsChartInterval.clearOnTabSelectedListeners();

        // 탭 추가 - 이제 중복되지 않음
        tabsChartInterval.addTab(tabsChartInterval.newTab().setText("1시간"));
        tabsChartInterval.addTab(tabsChartInterval.newTab().setText("4시간"));
        tabsChartInterval.addTab(tabsChartInterval.newTab().setText("1일"));

        // 기본 선택: 1일봉
        tabsChartInterval.selectTab(tabsChartInterval.getTabAt(CHART_INTERVAL_1D));
        currentChartInterval = CHART_INTERVAL_1D;

        // 탭 선택 리스너 - 새로 설정
        tabsChartInterval.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                currentChartInterval = position;

                // 이동평균선 정보 업데이트
                updateMovingAverageInfo();

                // 차트 업데이트
                updateChart();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 사용하지 않음
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 같은 탭 재선택 시 차트 새로고침
                updateChart();
            }
        });

        // 초기 이동평균선 정보 설정
        updateMovingAverageInfo();

        Log.d("StrategyFragment", "차트 기간 탭 설정 완료 - 중복 방지 적용");
    }


    /**
     * ★ 이동평균선 정보 업데이트
     */
    private void updateMovingAverageInfo() {
        if (tvMovingAverageInfo == null) return;

        String intervalName = getIntervalDisplayName(currentChartInterval);
        MovingAverageConfig config = getMovingAverageConfig(strategyType, currentChartInterval);

        String infoText = String.format("현재 기간: %s (%s)",
                intervalName, config.getDisplayText());

        tvMovingAverageInfo.setText(infoText);
        tvMovingAverageInfo.setVisibility(View.VISIBLE);
    }

    /**
     * ★ 기간 표시 이름 반환
     */
    private String getIntervalDisplayName(int interval) {
        switch (interval) {
            case CHART_INTERVAL_1H: return "1시간봉";
            case CHART_INTERVAL_4H: return "4시간봉";
            case CHART_INTERVAL_1D: return "1일봉";
            default: return "1일봉";
        }
    }

    /**
     * ★ 이동평균선 설정 클래스
     */
    private static class MovingAverageConfig {
        public final int fastPeriod;
        public final int slowPeriod;
        public final boolean fastIsEMA;
        public final boolean slowIsEMA;
        public final String fastName;
        public final String slowName;

        public MovingAverageConfig(int fastPeriod, int slowPeriod, boolean fastIsEMA, boolean slowIsEMA, String fastName, String slowName) {
            this.fastPeriod = fastPeriod;
            this.slowPeriod = slowPeriod;
            this.fastIsEMA = fastIsEMA;
            this.slowIsEMA = slowIsEMA;
            this.fastName = fastName;
            this.slowName = slowName;
        }

        public String getDisplayText() {
            return fastName + " vs " + slowName;
        }
    }

    /**
     * ★ 전략별, 기간별 이동평균선 설정 반환
     */
    private MovingAverageConfig getMovingAverageConfig(int strategyType, int chartInterval) {
        switch (strategyType) {
            case STRATEGY_SHORT_TERM:
                switch (chartInterval) {
                    case CHART_INTERVAL_1H:
                        return new MovingAverageConfig(5, 20, true, true, "5시간 EMA", "20시간 EMA");
                    case CHART_INTERVAL_4H:
                        return new MovingAverageConfig(5, 20, true, true, "20시간 EMA", "80시간 EMA");
                    case CHART_INTERVAL_1D:
                    default:
                        return new MovingAverageConfig(5, 20, true, true, "5일 EMA", "20일 EMA");
                }
            case STRATEGY_MID_TERM:
                switch (chartInterval) {
                    case CHART_INTERVAL_1H:
                        return new MovingAverageConfig(20, 60, true, false, "20시간 EMA", "60시간 SMA");
                    case CHART_INTERVAL_4H:
                        return new MovingAverageConfig(5, 15, true, false, "20시간 EMA", "60시간 SMA");
                    case CHART_INTERVAL_1D:
                    default:
                        return new MovingAverageConfig(20, 60, true, false, "20일 EMA", "60일 SMA");
                }
            case STRATEGY_LONG_TERM:
                switch (chartInterval) {
                    case CHART_INTERVAL_1H:
                        return new MovingAverageConfig(50, 200, false, false, "50시간 SMA", "200시간 SMA");
                    case CHART_INTERVAL_4H:
                        return new MovingAverageConfig(12, 50, false, false, "48시간 SMA", "200시간 SMA");
                    case CHART_INTERVAL_1D:
                    default:
                        return new MovingAverageConfig(50, 200, false, false, "50일 SMA", "200일 SMA");
                }
            default:
                return new MovingAverageConfig(5, 20, true, true, "5일 EMA", "20일 EMA");
        }
    }

    /**
     * ★ 기간별 바이낸스 인터벌 코드 반환
     */
    private String getBinanceInterval(int chartInterval) {
        switch (chartInterval) {
            case CHART_INTERVAL_1H: return "1h";
            case CHART_INTERVAL_4H: return "4h";
            case CHART_INTERVAL_1D: return "1d";
            default: return "1d";
        }
    }

    /**
     * ★ 기간별 데이터 수집 개수 반환
     */
    private int getDataLimitForInterval(int chartInterval) {
        MovingAverageConfig config = getMovingAverageConfig(strategyType, chartInterval);
        // 긴 기간 이동평균선의 2배 + 여유분
        return Math.max(config.slowPeriod * 2 + 50, 200);
    }

    /**
     * setupChart() 메서드 수정 - MarkerView 적용
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

        // 차트 그리기 순서 설정
        strategyChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.CANDLE,
                CombinedChart.DrawOrder.LINE,
                CombinedChart.DrawOrder.SCATTER
        });

        // 차트 여백 설정
        strategyChart.setExtraOffsets(8f, 40f, 8f, 5f); // 위쪽 여백 증가 (마커 공간)
        strategyChart.setBackgroundColor(Color.parseColor("#1E1E1E"));

        // ★ X축 설정 - 라벨 제거
        XAxis xAxis = strategyChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.parseColor("#40FFFFFF"));
        xAxis.setGridLineWidth(1f);
        xAxis.setTextColor(Color.parseColor("#CCCCCC"));
        xAxis.setTextSize(9f);

        // X축 라벨 완전히 숨기기
        xAxis.setDrawLabels(false);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(100f);
        xAxis.setGranularity(5f);
        xAxis.setGranularityEnabled(true);
        xAxis.setLabelCount(0, false);
        xAxis.setAvoidFirstLastClipping(false);
        xAxis.setSpaceMin(0.5f);
        xAxis.setSpaceMax(0.5f);

        // Y축 설정 (기존과 동일)
        YAxis leftAxis = strategyChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#40FFFFFF"));
        leftAxis.setGridLineWidth(1f);
        leftAxis.setTextColor(Color.parseColor("#CCCCCC"));
        leftAxis.setTextSize(10f);
        leftAxis.setLabelCount(6, false);
        leftAxis.setSpaceTop(2f);
        leftAxis.setSpaceBottom(2f);

        // Y축 가격 포맷터 (기존과 동일)
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if ("$".equals(currencySymbol)) {
                    if (value >= 100000) {
                        return String.format("$%.0fK", value / 1000);
                    } else if (value >= 10000) {
                        return String.format("$%.1fK", value / 1000);
                    } else if (value >= 1000) {
                        return String.format("$%.2fK", value / 1000);
                    } else if (value >= 100) {
                        return String.format("$%.0f", value);
                    } else if (value >= 10) {
                        return String.format("$%.1f", value);
                    } else if (value >= 1) {
                        return String.format("$%.2f", value);
                    } else {
                        return String.format("$%.3f", value);
                    }
                } else {
                    if (value >= 100000000) {
                        return String.format("₩%.0f억", value / 100000000);
                    } else if (value >= 10000000) {
                        return String.format("₩%.1f천만", value / 10000000);
                    } else if (value >= 1000000) {
                        return String.format("₩%.1f백만", value / 1000000);
                    } else if (value >= 100000) {
                        return String.format("₩%.0f만", value / 10000);
                    } else if (value >= 10000) {
                        return String.format("₩%.1f만", value / 10000);
                    } else if (value >= 1000) {
                        return String.format("₩%.1f천", value / 1000);
                    } else if (value >= 100) {
                        return String.format("₩%.0f", value);
                    } else if (value >= 10) {
                        return String.format("₩%.1f", value);
                    } else if (value >= 1) {
                        return String.format("₩%.2f", value);
                    } else {
                        return String.format("₩%.3f", value);
                    }
                }
            }
        });

        YAxis rightAxis = strategyChart.getAxisRight();
        rightAxis.setEnabled(false);
        strategyChart.getLegend().setEnabled(false);

        // ★ 커스텀 MarkerView 설정 (Toast 대신)
        try {
            DateTimeMarkerView markerView = new DateTimeMarkerView(getContext());
            markerView.setChartView(strategyChart);
            strategyChart.setMarker(markerView);

            Log.d("StrategyFragment", "MarkerView 설정 완료");
        } catch (Exception e) {
            Log.e("StrategyFragment", "MarkerView 설정 오류: " + e.getMessage());
            // MarkerView 설정 실패 시 기본 클릭 리스너로 폴백
            setupFallbackClickListener();
        }

        // ★ 하이라이트 설정 (선택된 봉 강조)
        strategyChart.setHighlightPerTapEnabled(true);
        strategyChart.setHighlightPerDragEnabled(false);

        // 하이라이트 선 스타일
        strategyChart.setDrawMarkers(true);

        Log.d("StrategyFragment", "차트 설정 완료 - MarkerView 적용");
    }

    /**
     * ★ MarkerView 실패 시 폴백 클릭 리스너
     */
    private void setupFallbackClickListener() {
        strategyChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int chartIndex = (int) e.getX();
                showCandleDateTime(chartIndex); // 기존 Toast 방식
            }

            @Override
            public void onNothingSelected() {
                // 아무것도 선택되지 않았을 때
            }
        });
    }


    /**
     * ★ 간단한 생성자를 가진 MarkerView (권장)
     */
    private class DateTimeMarkerView extends MarkerView {
        private TextView tvDateTime;
        private View backgroundView;

        public DateTimeMarkerView(Context context) {
            super(context, R.layout.marker_datetime);

            // 마커 뷰 초기화
            tvDateTime = findViewById(R.id.tvDateTime);
            backgroundView = findViewById(R.id.markerBackground);

            // 반투명 배경 설정
            if (backgroundView != null) {
                backgroundView.setBackgroundResource(R.drawable.marker_background);
            }
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            try {
                // 클릭된 봉의 인덱스
                int chartIndex = (int) e.getX();

                // 날짜/시간 텍스트 생성
                String dateTimeText = getDateTimeForIndex(chartIndex);

                if (dateTimeText != null && !dateTimeText.isEmpty()) {
                    tvDateTime.setText(dateTimeText);
                    tvDateTime.setTextColor(Color.WHITE);
                } else {
                    tvDateTime.setText("날짜 없음");
                }

                Log.d("StrategyFragment", "MarkerView 업데이트: " + dateTimeText);

            } catch (Exception ex) {
                Log.e("StrategyFragment", "MarkerView 업데이트 오류: " + ex.getMessage());
                tvDateTime.setText("오류");
            }

            super.refreshContent(e, highlight);
        }

        @Override
        public MPPointF getOffset() {
            // 마커 위치 조정 (중앙 정렬, 봉 위쪽에 표시)
            return new MPPointF(-(getWidth() / 2), -getHeight() - 15);
        }

        @Override
        public MPPointF getOffsetForDrawingAtPoint(float posX, float posY) {
            // 기본적인 위치 조정
            MPPointF offset = getOffset();

            // 간단한 경계 체크
            if (posX + offset.x < 0) {
                offset.x = -posX + 10;
            }

            // 위쪽 경계 체크
            if (posY + offset.y < 0) {
                offset.y = 20; // 봉 아래쪽에 표시
            }

            return offset;
        }

        /**
         * 인덱스에 해당하는 날짜/시간 문자열 반환
         */
        private String getDateTimeForIndex(int chartIndex) {
            if (currentKlinesData == null || currentKlinesData.isEmpty()) {
                return null;
            }

            try {
                // 차트 인덱스를 실제 kline 데이터 인덱스로 변환
                int totalDisplayPeriods = 100;
                int startIndex = Math.max(0, currentKlinesData.size() - totalDisplayPeriods);
                int actualIndex = startIndex + chartIndex;

                if (actualIndex >= 0 && actualIndex < currentKlinesData.size()) {
                    List<Object> kline = currentKlinesData.get(actualIndex);

                    // timestamp 파싱
                    long timestamp = parseTimestamp(kline.get(0));
                    Date candleDate = new Date(timestamp);

                    // 포맷팅
                    return formatDateTime(candleDate, currentChartInterval);
                }

            } catch (Exception e) {
                Log.e("StrategyFragment", "MarkerView 날짜 계산 오류: " + e.getMessage());
            }

            return null;
        }
    }

    /**
     * ★ 안전한 timestamp 파싱 메서드
     */
    private long parseTimestamp(Object timestampObj) {
        if (timestampObj == null) {
            throw new IllegalArgumentException("Timestamp is null");
        }

        String timestampStr = timestampObj.toString().trim();

        try {
            // 먼저 직접 long 파싱 시도
            return Long.parseLong(timestampStr);
        } catch (NumberFormatException e1) {
            try {
                // 과학적 표기법일 경우 Double로 파싱 후 변환
                double timestampDouble = Double.parseDouble(timestampStr);
                return (long) timestampDouble;
            } catch (NumberFormatException e2) {
                // 둘 다 실패하면 예외 발생
                throw new NumberFormatException("Cannot parse timestamp: " + timestampStr);
            }
        }
    }

    /**
     * ★ 클릭된 봉의 날짜/시간 표시 - 과학적 표기법 파싱 수정
     */
    private void showCandleDateTime(int chartIndex) {
        if (currentKlinesData == null || currentKlinesData.isEmpty()) {
            Toast.makeText(getContext(), "차트 데이터가 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 차트 인덱스를 실제 kline 데이터 인덱스로 변환
            int totalDisplayPeriods = 100;
            int startIndex = Math.max(0, currentKlinesData.size() - totalDisplayPeriods);
            int actualIndex = startIndex + chartIndex;

            if (actualIndex >= 0 && actualIndex < currentKlinesData.size()) {
                List<Object> kline = currentKlinesData.get(actualIndex);

                // ★ timestamp 추출 - 과학적 표기법 처리
                long timestamp;
                try {
                    String timestampStr = kline.get(0).toString();

                    // 과학적 표기법인지 확인 (E 또는 e 포함)
                    if (timestampStr.contains("E") || timestampStr.contains("e")) {
                        // 과학적 표기법을 Double로 파싱한 후 long으로 변환
                        double timestampDouble = Double.parseDouble(timestampStr);
                        timestamp = (long) timestampDouble;
                    } else {
                        // 일반 숫자 문자열
                        timestamp = Long.parseLong(timestampStr);
                    }

                    Log.d("StrategyFragment", String.format("Timestamp 파싱: %s → %d", timestampStr, timestamp));

                } catch (NumberFormatException e) {
                    Log.e("StrategyFragment", "Timestamp 파싱 실패: " + kline.get(0).toString());
                    Toast.makeText(getContext(), "시간 데이터 형식 오류", Toast.LENGTH_SHORT).show();
                    return;
                }

                Date candleDate = new Date(timestamp);

                // 언어별 포맷팅
                String dateTimeText = formatDateTime(candleDate, currentChartInterval);

                // 토스트로 표시
                Toast.makeText(getContext(), dateTimeText, Toast.LENGTH_LONG).show();

                Log.d("StrategyFragment", String.format("봉 클릭: 인덱스=%d, 실제인덱스=%d, 시간=%s",
                        chartIndex, actualIndex, dateTimeText));

            } else {
                Log.w("StrategyFragment", String.format("인덱스 범위 초과: 차트=%d, 실제=%d, 전체=%d",
                        chartIndex, actualIndex, currentKlinesData.size()));
                Toast.makeText(getContext(), "유효하지 않은 데이터입니다", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e("StrategyFragment", "날짜/시간 표시 오류: " + e.getMessage());
            e.printStackTrace(); // 스택 트레이스도 출력
            Toast.makeText(getContext(), "날짜 정보를 가져올 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ★ 언어별 날짜/시간 포맷팅
     */
    private String formatDateTime(Date date, int chartInterval) {
        String currentLanguage = getResources().getConfiguration().locale.getLanguage();
        boolean isKorean = "ko".equals(currentLanguage);

        SimpleDateFormat formatter;

        if (isKorean) {
            // 한국어 - 한국 시간(KST)
            TimeZone kstTimeZone = TimeZone.getTimeZone("Asia/Seoul");

            switch (chartInterval) {
                case CHART_INTERVAL_1H:
                    formatter = new SimpleDateFormat("M월 d일 H시", Locale.KOREAN);
                    break;
                case CHART_INTERVAL_4H:
                    formatter = new SimpleDateFormat("M월 d일 H시", Locale.KOREAN);
                    break;
                case CHART_INTERVAL_1D:
                default:
                    formatter = new SimpleDateFormat("M월 d일", Locale.KOREAN);
                    break;
            }
            formatter.setTimeZone(kstTimeZone);

        } else {
            // 영어 - UTC 시간
            TimeZone utcTimeZone = TimeZone.getTimeZone("UTC");

            switch (chartInterval) {
                case CHART_INTERVAL_1H:
                    formatter = new SimpleDateFormat("MMM dd, HH:mm", Locale.ENGLISH);
                    break;
                case CHART_INTERVAL_4H:
                    formatter = new SimpleDateFormat("MMM dd, HH:mm", Locale.ENGLISH);
                    break;
                case CHART_INTERVAL_1D:
                default:
                    formatter = new SimpleDateFormat("MMM dd", Locale.ENGLISH);
                    break;
            }
            formatter.setTimeZone(utcTimeZone);
        }

        return formatter.format(date);
    }



    /**
     * ★ 기간별 시간 단위 반환
     */
    private String getTimeUnitForInterval(int interval) {
        switch (interval) {
            case CHART_INTERVAL_1H: return "시간전";
            case CHART_INTERVAL_4H: return "×4시간전";
            case CHART_INTERVAL_1D: return getString(R.string.days_ago);
            default: return getString(R.string.days_ago);
        }
    }

    /**
     * 차트 데이터 업데이트 - 모든 기간에서 동일한 30일 일봉 차트
     */
    public void updateChart() {
        if (strategyChart == null || coinInfo == null) return;

        // ★ 현재 선택된 기간에 따른 데이터 가져오기
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
     * 조용한 차트 데이터 갱신 - 전략별 데이터 기간
     */
    private void getCandleDataAndUpdateChartSilently() {
        if (coinInfo == null || coinInfo.getMarket() == null) return;

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        String interval = getBinanceInterval(currentChartInterval);
        int limit = getDataLimitForInterval(currentChartInterval);

        Log.d("StrategyFragment", String.format("자동 차트 갱신: 기간=%s, 인터벌=%s, 심볼=%s",
                getStrategyTypeName(), interval, coinInfo.getSymbol()));

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
     * ★ 캔들 데이터 가져와서 차트 업데이트 - 기간별 데이터 로딩
     */
    private void getCandleDataAndUpdateChart() {
        if (coinInfo == null || coinInfo.getMarket() == null) return;

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        String interval = getBinanceInterval(currentChartInterval);
        int limit = getDataLimitForInterval(currentChartInterval);

        Log.d("StrategyFragment", String.format("차트 데이터 요청: 기간=%s, 인터벌=%s, 개수=%d",
                getIntervalDisplayName(currentChartInterval), interval, limit));

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
     * EMA 계산 메서드
     */
    private ArrayList<Entry> calculateEMA(ArrayList<Float> prices, int period) {
        ArrayList<Entry> emaEntries = new ArrayList<>();

        if (prices.size() < period) {
            return emaEntries;
        }

        // 첫 번째 EMA는 SMA로 계산
        float firstSMA = 0;
        for (int i = 0; i < period; i++) {
            firstSMA += prices.get(i);
        }
        firstSMA = firstSMA / period;

        emaEntries.add(new Entry(period - 1, firstSMA));

        // 이후 EMA 계산
        float multiplier = 2.0f / (period + 1);
        float previousEMA = firstSMA;

        for (int i = period; i < prices.size(); i++) {
            float currentPrice = prices.get(i);
            float currentEMA = (currentPrice * multiplier) + (previousEMA * (1 - multiplier));
            emaEntries.add(new Entry(i, currentEMA));
            previousEMA = currentEMA;
        }

        return emaEntries;
    }

    /**
     * SMA 계산 메서드
     */
    private ArrayList<Entry> calculateSMA(ArrayList<Float> prices, int period) {
        ArrayList<Entry> smaEntries = new ArrayList<>();

        for (int i = period - 1; i < prices.size(); i++) {
            float sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += prices.get(j);
            }
            float sma = sum / period;
            smaEntries.add(new Entry(i, sma));
        }

        return smaEntries;
    }

    /**
     * ★ 전략별 골든크로스/데드크로스 차트 생성 - 기간별 설정 적용
     */
    private void createCombinedChartData(List<List<Object>> klines) {
        if (strategyChart == null || klines.isEmpty()) {
            Log.e("StrategyFragment", "차트가 null이거나 캔들 데이터가 비어있음");
            createEmptyChart();
            return;
        }

        currentKlinesData = new ArrayList<>(klines);

        // 📍 접근 권한 확인
        boolean isSubscribed = subscriptionManager.isSubscribed();
        boolean hasAdPermission = false;
        boolean hasChartAccess = false;

        if (coinInfo != null && coinInfo.getSymbol() != null) {
            hasAdPermission = adManager.hasActiveAdPermission(coinInfo.getSymbol());
            hasChartAccess = isSubscribed || hasAdPermission;
        }

        // 📍 최근 크로스 정보 초기화
        recentCrossType = null;
        recentCrossDaysAgo = -1;

        // ★ 현재 기간별 이동평균선 설정 가져오기
        MovingAverageConfig config = getMovingAverageConfig(strategyType, currentChartInterval);

        Log.d("StrategyFragment", String.format("차트 데이터 생성 시작: %d개 캔들 (%s 전략, %s) - 차트 접근권한: %s",
                klines.size(), getStrategyTypeName(), getIntervalDisplayName(currentChartInterval), hasChartAccess ? "있음" : "없음"));

        CombinedData combinedData = new CombinedData();

        // 1. 전체 데이터로 close price 배열 생성
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

        // 2. 100개 캔들스틱 데이터 생성 (항상 표시)
        int totalDisplayPeriods = 100;
        int extendedPeriods = 105;
        int visiblePeriods = 30;
        int startIndex = Math.max(0, klines.size() - totalDisplayPeriods);

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

        // 캔들스틱 데이터셋 설정 (항상 표시)
        CandleDataSet candleDataSet = new CandleDataSet(candleEntries, "Price");
        candleDataSet.setShadowColor(Color.parseColor("#CCCCCC"));
        candleDataSet.setShadowWidth(0.8f);
        candleDataSet.setDecreasingColor(Color.parseColor("#FF4B6C"));
        candleDataSet.setDecreasingPaintStyle(Paint.Style.FILL);
        candleDataSet.setIncreasingColor(Color.parseColor("#00C087"));
        candleDataSet.setIncreasingPaintStyle(Paint.Style.FILL);
        candleDataSet.setNeutralColor(Color.parseColor("#FFC107"));
        candleDataSet.setDrawValues(false);
        candleDataSet.setBarSpace(0.2f);
        candleDataSet.setHighlightEnabled(true);
        candleDataSet.setHighLightColor(Color.WHITE);

        CandleData candleData = new CandleData(candleDataSet);
        combinedData.setData(candleData);

        // 3. ★ 기간별 이동평균선 계산 및 골든크로스/데드크로스 분석 (접근 권한이 있을 때만)
        ArrayList<ILineDataSet> lineDataSets = new ArrayList<>();

        if (hasChartAccess) {
            ArrayList<Entry> fastMA = new ArrayList<>();
            ArrayList<Entry> slowMA = new ArrayList<>();

            // ★ 현재 기간에 맞는 이동평균선 계산
            if (config.fastIsEMA) {
                fastMA = calculateEMA(allClosePrices, config.fastPeriod);
            } else {
                fastMA = calculateSMA(allClosePrices, config.fastPeriod);
            }

            if (config.slowIsEMA) {
                slowMA = calculateEMA(allClosePrices, config.slowPeriod);
            } else {
                slowMA = calculateSMA(allClosePrices, config.slowPeriod);
            }

            Log.d("StrategyFragment", String.format("MA 계산 완료 - %s: %d개, %s: %d개 (권한 있음 - 크로스 분석 진행)",
                    config.fastName, fastMA.size(), config.slowName, slowMA.size()));

            // 4. 골든크로스/데드크로스 시그널 포인트 계산 (접근 권한이 있을 때만)
            if (fastMA.size() > 1 && slowMA.size() > 1) {
                ArrayList<Entry> goldenCrossEntries = new ArrayList<>();
                ArrayList<Entry> deathCrossEntries = new ArrayList<>();

                int stabilizationStart = config.slowPeriod; // 긴 기간 이동평균선 안정화 후부터

                // 📍 최근 크로스 찾기를 위한 변수
                int mostRecentCrossDate = -1;
                String mostRecentCrossType = null;

                Log.d("StrategyFragment", String.format("🔍 %s 크로스 감지 시작 (%s) - 안정화 시점: %d 기간째부터 (권한 있음)",
                        getStrategyTypeName(), getIntervalDisplayName(currentChartInterval), stabilizationStart));

                // 크로스 포인트 찾기
                for (int dataIndex = stabilizationStart; dataIndex < klines.size() - 1; dataIndex++) {

                    // 해당 데이터 인덱스의 MA 값 찾기
                    float fastCurrent = 0, fastNext = 0, slowCurrent = 0, slowNext = 0;
                    boolean foundCurrent = false, foundNext = false;

                    // 현재 시점 MA 값 찾기
                    for (Entry fastEntry : fastMA) {
                        if ((int)fastEntry.getX() == dataIndex) {
                            fastCurrent = fastEntry.getY();
                            break;
                        }
                    }
                    for (Entry slowEntry : slowMA) {
                        if ((int)slowEntry.getX() == dataIndex) {
                            slowCurrent = slowEntry.getY();
                            foundCurrent = true;
                            break;
                        }
                    }

                    // 다음 시점 MA 값 찾기
                    for (Entry fastEntry : fastMA) {
                        if ((int)fastEntry.getX() == dataIndex + 1) {
                            fastNext = fastEntry.getY();
                            break;
                        }
                    }
                    for (Entry slowEntry : slowMA) {
                        if ((int)slowEntry.getX() == dataIndex + 1) {
                            slowNext = slowEntry.getY();
                            foundNext = true;
                            break;
                        }
                    }

                    if (!foundCurrent || !foundNext) continue;

                    // 골든크로스 검사 (Fast MA가 Slow MA를 상향 돌파)
                    if (fastCurrent <= slowCurrent && fastNext > slowNext) {
                        Log.d("StrategyFragment", String.format("🟡 골든크로스 감지! (%s) 일자=%d, %s: %.2f→%.2f, %s: %.2f→%.2f",
                                getIntervalDisplayName(currentChartInterval), dataIndex + 1, config.fastName, fastCurrent, fastNext, config.slowName, slowCurrent, slowNext));

                        // 📍 최근 크로스 정보 업데이트
                        if (dataIndex + 1 > mostRecentCrossDate) {
                            mostRecentCrossDate = dataIndex + 1;
                            mostRecentCrossType = "GOLDEN";
                        }

                        // 최근 100개 범위 내의 크로스만 차트에 표시
                        if (dataIndex + 1 >= startIndex) {
                            int chartIndex = (dataIndex + 1) - startIndex;
                            try {
                                List<Object> crossKline = klines.get(dataIndex + 1);
                                double high = Double.parseDouble(crossKline.get(2).toString());
                                double low = Double.parseDouble(crossKline.get(3).toString());
                                float candleHigh = (float) high;
                                float candleLow = (float) low;

                                float candleSize = candleHigh - candleLow;
                                float offset = Math.max(candleSize * 0.7f, candleLow * 0.02f);
                                float goldenCrossY = candleLow - offset;

                                goldenCrossEntries.add(new Entry(chartIndex, goldenCrossY));
                                minPrice = Math.min(minPrice, goldenCrossY);
                            } catch (Exception e) {
                                Log.e("StrategyFragment", "골든크로스 캔들 데이터 파싱 오류: " + e.getMessage());
                            }
                        }
                    }
                    // 데드크로스 검사 (Fast MA가 Slow MA를 하향 돌파)
                    else if (fastCurrent >= slowCurrent && fastNext < slowNext) {
                        Log.d("StrategyFragment", String.format("🔴 데드크로스 감지! (%s) 일자=%d, %s: %.2f→%.2f, %s: %.2f→%.2f",
                                getIntervalDisplayName(currentChartInterval), dataIndex + 1, config.fastName, fastCurrent, fastNext, config.slowName, slowCurrent, slowNext));

                        // 📍 최근 크로스 정보 업데이트
                        if (dataIndex + 1 > mostRecentCrossDate) {
                            mostRecentCrossDate = dataIndex + 1;
                            mostRecentCrossType = "DEATH";
                        }

                        // 최근 100개 범위 내의 크로스만 차트에 표시
                        if (dataIndex + 1 >= startIndex) {
                            int chartIndex = (dataIndex + 1) - startIndex;
                            try {
                                List<Object> crossKline = klines.get(dataIndex + 1);
                                double high = Double.parseDouble(crossKline.get(2).toString());
                                double low = Double.parseDouble(crossKline.get(3).toString());
                                float candleHigh = (float) high;
                                float candleLow = (float) low;

                                float candleSize = candleHigh - candleLow;
                                float offset = Math.max(candleSize * 1.2f, candleHigh * 0.025f);
                                float deathCrossY = candleHigh + offset;

                                deathCrossEntries.add(new Entry(chartIndex, deathCrossY));
                                maxPrice = Math.max(maxPrice, deathCrossY);
                            } catch (Exception e) {
                                Log.e("StrategyFragment", "데드크로스 캔들 데이터 파싱 오류: " + e.getMessage());
                            }
                        }
                    }
                }

                // 📍 최근 크로스 정보 저장 (권한이 있을 때만)
                if (mostRecentCrossDate >= 0 && mostRecentCrossType != null) {
                    recentCrossType = mostRecentCrossType;
                    // ★ 기간별 단위로 계산
                    recentCrossDaysAgo = klines.size() - 1 - mostRecentCrossDate;

                    Log.d("StrategyFragment", String.format("📍 최근 크로스: %s (%d %s 전) - 권한 있음으로 표시",
                            recentCrossType, recentCrossDaysAgo, getTimeUnitForInterval(currentChartInterval)));
                }

                // ScatterData로 삼각형 마커 추가 (크로스 포인트 - 권한이 있을 때만)
                ArrayList<IScatterDataSet> scatterDataSets = new ArrayList<>();

                // 골든크로스 삼각형 마커 (위쪽 삼각형)
                if (!goldenCrossEntries.isEmpty()) {
                    ScatterDataSet goldenCrossDataSet = new ScatterDataSet(goldenCrossEntries, "Golden Cross");
                    goldenCrossDataSet.setShapeRenderer(new RotatedTriangleRenderer(false));
                    goldenCrossDataSet.setColor(Color.parseColor("#4CAF50"));
                    goldenCrossDataSet.setScatterShapeSize(30f);
                    goldenCrossDataSet.setDrawValues(false);
                    goldenCrossDataSet.setHighlightEnabled(false);
                    scatterDataSets.add(goldenCrossDataSet);

                    Log.d("StrategyFragment", String.format("✅ 골든크로스 삼각형 마커 %d개 추가됨 (권한 있음)", goldenCrossEntries.size()));
                }

                // 데드크로스 삼각형 마커 (아래쪽 삼각형)
                if (!deathCrossEntries.isEmpty()) {
                    ScatterDataSet deathCrossDataSet = new ScatterDataSet(deathCrossEntries, "Death Cross");
                    deathCrossDataSet.setShapeRenderer(new RotatedTriangleRenderer(true));
                    deathCrossDataSet.setColor(Color.parseColor("#F44336"));
                    deathCrossDataSet.setScatterShapeSize(30f);
                    deathCrossDataSet.setDrawValues(false);
                    deathCrossDataSet.setHighlightEnabled(false);
                    scatterDataSets.add(deathCrossDataSet);

                    Log.d("StrategyFragment", String.format("✅ 데드크로스 삼각형 마커 %d개 추가됨 (권한 있음)", deathCrossEntries.size()));
                }

                // ScatterData를 CombinedData에 추가 (권한이 있을 때만)
                if (!scatterDataSets.isEmpty()) {
                    ScatterData scatterData = new ScatterData(scatterDataSets);
                    combinedData.setData(scatterData);
                }
            }
        } else {
            Log.d("StrategyFragment", "차트 접근 권한 없음 - 크로스 분석 및 표시 생략");
        }

        // 5. 전략이 있는 경우 지지선/저항선 추가 (접근 권한이 있을 때만)
        if (hasChartAccess && strategy != null) {
            Log.d("StrategyFragment", "지지선/저항선 추가 시작 (권한 있음)");

            // 지지선들 (녹색, 점선)
            if (strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty()) {
                for (int stepIndex = 0; stepIndex < strategy.getBuySteps().size(); stepIndex++) {
                    AnalysisResult.Strategy.TradingStep step = strategy.getBuySteps().get(stepIndex);
                    ArrayList<Entry> supportEntries = new ArrayList<>();

                    float supportPrice = (float) step.getPrice();
                    for (int i = 0; i < extendedPeriods; i++) {
                        supportEntries.add(new Entry(i, supportPrice));
                    }

                    LineDataSet supportDataSet = new LineDataSet(supportEntries, "Support " + (stepIndex + 1));
                    supportDataSet.setColor(Color.parseColor("#4CAF50"));
                    supportDataSet.setLineWidth(1f);
                    supportDataSet.setDrawCircles(false);
                    supportDataSet.setDrawValues(false);
                    supportDataSet.enableDashedLine(15f, 8f, 0f);
                    supportDataSet.setDrawFilled(false);
                    supportDataSet.setHighlightEnabled(false);
                    lineDataSets.add(supportDataSet);

                    minPrice = Math.min(minPrice, supportPrice);
                    maxPrice = Math.max(maxPrice, supportPrice);
                }
            }

            // 저항선들 (빨간색, 점선)
            if (strategy.getTargetPrices() != null && !strategy.getTargetPrices().isEmpty()) {
                for (int targetIndex = 0; targetIndex < strategy.getTargetPrices().size(); targetIndex++) {
                    double targetPrice = strategy.getTargetPrices().get(targetIndex);
                    ArrayList<Entry> resistanceEntries = new ArrayList<>();

                    float resistancePrice = (float) targetPrice;
                    for (int i = 0; i < extendedPeriods; i++) {
                        resistanceEntries.add(new Entry(i, resistancePrice));
                    }

                    LineDataSet resistanceDataSet = new LineDataSet(resistanceEntries, "Resistance " + (targetIndex + 1));
                    resistanceDataSet.setColor(Color.parseColor("#F44336"));
                    resistanceDataSet.setLineWidth(1f);
                    resistanceDataSet.setDrawCircles(false);
                    resistanceDataSet.setDrawValues(false);
                    resistanceDataSet.enableDashedLine(15f, 8f, 0f);
                    resistanceDataSet.setDrawFilled(false);
                    resistanceDataSet.setHighlightEnabled(false);
                    lineDataSets.add(resistanceDataSet);

                    minPrice = Math.min(minPrice, resistancePrice);
                    maxPrice = Math.max(maxPrice, resistancePrice);
                }
            }

            // 손절매 라인 (주황색, 점선) - 위험 관리 구간
            if (strategy.getStopLoss() > 0) {
                ArrayList<Entry> stopLossEntries = new ArrayList<>();
                float stopLossPrice = (float) strategy.getStopLoss();

                for (int i = 0; i < extendedPeriods; i++) {
                    stopLossEntries.add(new Entry(i, stopLossPrice));
                }

                LineDataSet stopLossDataSet = new LineDataSet(stopLossEntries, "Stop Loss");
                stopLossDataSet.setColor(Color.parseColor("#FF9800"));
                stopLossDataSet.setLineWidth(1f);
                stopLossDataSet.setDrawCircles(false);
                stopLossDataSet.setDrawValues(false);
                stopLossDataSet.enableDashedLine(20f, 10f, 0f);
                stopLossDataSet.setDrawFilled(false);
                stopLossDataSet.setHighlightEnabled(false);
                lineDataSets.add(stopLossDataSet);

                minPrice = Math.min(minPrice, stopLossPrice);
                maxPrice = Math.max(maxPrice, stopLossPrice);
            }

            Log.d("StrategyFragment", String.format("지지선/저항선 %d개 추가 완료 (권한 있음)", lineDataSets.size()));
        } else if (!hasChartAccess) {
            Log.d("StrategyFragment", "차트 접근 권한 없음 - 지지선/저항선 표시 생략");
        }

        // 6. 라인 데이터 추가 (접근 권한이 있고 지지/저항선이 있을 때만)
        if (!lineDataSets.isEmpty()) {
            LineData lineData = new LineData(lineDataSets);
            combinedData.setData(lineData);
            Log.d("StrategyFragment", String.format("라인 데이터 추가: %d개 (권한 있음)", lineDataSets.size()));
        }

        // 7. Y축 범위 설정
        float priceRange = maxPrice - minPrice;
        float padding = priceRange * 0.02f;
        float minPadding = priceRange * 0.01f;
        padding = Math.max(padding, minPadding);

        strategyChart.getAxisLeft().setAxisMinimum(minPrice - padding);
        strategyChart.getAxisLeft().setAxisMaximum(maxPrice + padding);

        // 8. 차트 그리기 순서 설정
        strategyChart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.CANDLE,
                CombinedChart.DrawOrder.LINE,      // 지지/저항선 (권한 있을 때만)
                CombinedChart.DrawOrder.SCATTER    // 크로스 마커 (권한 있을 때만)
        });

        // 9. 차트에 데이터 설정
        strategyChart.setData(combinedData);

        // X축 범위 다시 강제 설정 (데이터 설정 후)
        XAxis xAxis = strategyChart.getXAxis();
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(100f);
        xAxis.setGranularity(5f);
        xAxis.setGranularityEnabled(true);
        xAxis.setSpaceMax(0.5f);

        // X축 뷰포트 설정
        strategyChart.setVisibleXRangeMaximum(visiblePeriods);
        strategyChart.setVisibleXRangeMinimum(visiblePeriods);
        strategyChart.setScaleXEnabled(true);
        strategyChart.setDragEnabled(true);

        // 초기 위치 조정 - "오늘"이 화면 중간쯤 오도록
        float initialPosition = 105 - visiblePeriods;
        strategyChart.moveViewToX(initialPosition);

        // 10. 최근 크로스 UI 업데이트
        // 권한이 있을 때만 실제 크로스 정보를 업데이트하고,
        // 권한이 없을 때는 updateContentAccessUI()에서 마스킹 처리됨
        if (hasChartAccess) {
            updateRecentCrossUI();
        }

        strategyChart.invalidate();

        Log.d("StrategyFragment", String.format("✅ %s 전략 차트 완료 (%s): 권한 상태=%s, 캔들만=%s",
                getStrategyTypeName(), getIntervalDisplayName(currentChartInterval),
                hasChartAccess ? "있음" : "없음",
                hasChartAccess ? "전체표시" : "캔들만표시"));
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
        emptyDataSet.setShadowWidth(1f);
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

    /**
     * 현재 사용자가 콘텐츠에 접근할 권한이 있는지 확인
     * @return true: 구독자이거나 광고 시청함, false: 권한 없음
     */
    private boolean hasContentAccess() {
        if (coinInfo == null || coinInfo.getSymbol() == null) {
            return false;
        }

        boolean isSubscribed = subscriptionManager != null && subscriptionManager.isSubscribed();
        boolean hasAdPermission = adManager != null && adManager.hasActiveAdPermission(coinInfo.getSymbol());

        return isSubscribed || hasAdPermission;
    }

    /**
     * 최근 크로스 정보를 안전하게 마스킹 처리
     */
    private void maskRecentCrossInfo() {
        if (tvRecentCross == null) return;

        tvRecentCross.setText(getString(R.string.masked_content_cross));
        tvRecentCross.setAlpha(0.3f);
        tvRecentCross.setTextColor(Color.parseColor("#80CCCCCC"));
        tvRecentCross.setVisibility(View.VISIBLE);

        Log.d("StrategyFragment", "최근 크로스 정보 마스킹 적용");
    }

    /**
     * 최근 크로스 정보 블러 효과 제거
     */
    private void unmaskRecentCrossInfo() {
        if (tvRecentCross == null) return;

        tvRecentCross.setAlpha(1.0f);
        tvRecentCross.setTextColor(Color.parseColor("#FFFFFF"));

        Log.d("StrategyFragment", "최근 크로스 정보 블러 제거");
    }

    private void updateRecentCrossUI() {
        if (tvRecentCross == null) {
            return;
        }

        // ★ 권한 체크 추가 - 권한이 없으면 마스킹 상태 유지
        if (coinInfo == null || !hasContentAccess()) {
            // 권한이 없는 경우 마스킹 처리
            tvRecentCross.setText(getString(R.string.masked_content_cross));
            tvRecentCross.setAlpha(0.3f);
            tvRecentCross.setTextColor(Color.parseColor("#80CCCCCC"));
            tvRecentCross.setVisibility(View.VISIBLE);
            Log.d("StrategyFragment", "최근 크로스 정보 마스킹 처리 (권한 없음)");
            return;
        }

        // 권한이 있는 경우에만 실제 크로스 정보 표시
        if (recentCrossType == null || recentCrossDaysAgo < 0) {
            // 최근 크로스가 없는 경우
            tvRecentCross.setVisibility(View.GONE);
            Log.d("StrategyFragment", "최근 크로스 없음 - UI 숨김");
            return;
        }

        // 크로스 타입에 따른 텍스트 및 색상 설정
        String crossTypeText;
        int textColor;
        String emoji;

        if ("GOLDEN".equals(recentCrossType)) {
            crossTypeText = getString(R.string.golden_cross_legend);
            textColor = Color.parseColor("#4CAF50"); // 녹색
            emoji = "▲";
        } else if ("DEATH".equals(recentCrossType)) {
            crossTypeText = getString(R.string.death_cross_legend);
            textColor = Color.parseColor("#F44336"); // 빨간색
            emoji = "▼";
        } else {
            tvRecentCross.setVisibility(View.GONE);
            return;
        }

        // ★ 기간별 단위 텍스트 처리
        String timeAgoText;
        if (recentCrossDaysAgo == 0) {
            timeAgoText = getString(R.string.today);
        } else if (recentCrossDaysAgo == 1) {
            timeAgoText = "1" + getTimeUnitForInterval(currentChartInterval);
        } else {
            timeAgoText = recentCrossDaysAgo + getTimeUnitForInterval(currentChartInterval);
        }

        // 최종 텍스트 구성
        String recentCrossText = String.format(getString(R.string.recent_cross_format),
                emoji, crossTypeText, timeAgoText);

        // ★ 권한이 있는 경우에만 실제 UI 업데이트
        tvRecentCross.setText(Html.fromHtml(String.format("<font color='%s'><b>%s</b></font>",
                        String.format("#%06X", (0xFFFFFF & textColor)), recentCrossText),
                Html.FROM_HTML_MODE_LEGACY));
        tvRecentCross.setAlpha(1.0f); // 투명도 완전 복원
        tvRecentCross.setTextColor(Color.parseColor("#FFFFFF")); // 텍스트 색상 복원
        tvRecentCross.setVisibility(View.VISIBLE);

        Log.d("StrategyFragment", String.format("최근 크로스 UI 업데이트: %s %s (%s) - %s",
                emoji, crossTypeText, timeAgoText, getIntervalDisplayName(currentChartInterval)));
    }

    // 나머지 메서드들은 기존과 동일...
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

            // ★ 최근 크로스 정보 블러 처리 강화
            if (tvRecentCross != null) {
                tvRecentCross.setText(getString(R.string.masked_content_cross));
                tvRecentCross.setAlpha(0.3f);
                tvRecentCross.setTextColor(Color.parseColor("#80CCCCCC"));
            }

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

        // ★ 최근 크로스 정보 블러 효과 제거
        if (tvRecentCross != null) {
            tvRecentCross.setAlpha(1.0f);
            tvRecentCross.setTextColor(Color.parseColor("#FFFFFF"));
        }
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

            // coinInfo가 없을 때 모든 콘텐츠 블러 처리
            blurOverlay.setVisibility(View.VISIBLE);
            pixelatedOverlay.setVisibility(View.VISIBLE);
            additionalBlurLayer.setVisibility(View.VISIBLE);
            contentArea.setAlpha(0.5f);
            btnSubscribe.setVisibility(View.VISIBLE);
            btnWatchAd.setVisibility(View.VISIBLE);
            tvAdStatus.setVisibility(View.GONE);

            // 최근 크로스 정보도 마스킹
            if (tvRecentCross != null) {
                tvRecentCross.setText(getString(R.string.masked_content_cross));
                tvRecentCross.setVisibility(View.VISIBLE);
            }
            return;
        }

        boolean isSubscribed = subscriptionManager.isSubscribed();
        boolean hasAdPermission = false;
        boolean isPremiumCoin = false;

        if (coinInfo != null && coinInfo.getSymbol() != null) {
            hasAdPermission = adManager.hasActiveAdPermission(coinInfo.getSymbol());
            isPremiumCoin = coinInfo.isPremium();
        }

        // 권한이 있는 경우 (구독자이거나 광고 시청함)
        if (isSubscribed || hasAdPermission) {
            // 블러 효과 제거
            blurOverlay.setVisibility(View.GONE);
            pixelatedOverlay.setVisibility(View.GONE);
            additionalBlurLayer.setVisibility(View.GONE);
            contentArea.setAlpha(1.0f);
            btnSubscribe.setVisibility(View.GONE);
            btnWatchAd.setVisibility(View.GONE);

            // 광고 시청자의 경우 남은 시간 표시
            if (!isSubscribed && hasAdPermission) {
                int remainingMinutes = adManager.getRemainingMinutes(coinInfo.getSymbol());
                tvAdStatus.setVisibility(View.VISIBLE);
                tvAdStatus.setText(getString(R.string.ad_remaining_minutes_format, remainingMinutes));
            } else {
                tvAdStatus.setVisibility(View.GONE);
            }

            // 실제 콘텐츠 표시
            if (strategy != null) {
                if (strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty() && layoutBuySteps != null) {
                    displayBuySteps(layoutBuySteps, strategy.getBuySteps());
                }
                updateTargetPrices();
                updateStopLoss();
                updateRiskReward();
                updateStrategyDetail();

                // ★ 최근 크로스 정보 실제 표시
                updateRecentCrossUI();

                // 차트도 업데이트
                updateChart();
            }
        }
        // 권한이 없는 경우 (구독자도 아니고 광고도 안 봄)
        else {
            // 블러 효과 적용
            blurOverlay.setVisibility(View.VISIBLE);
            pixelatedOverlay.setVisibility(View.VISIBLE);
            additionalBlurLayer.setVisibility(View.VISIBLE);
            contentArea.setAlpha(0.5f);
            btnSubscribe.setVisibility(View.VISIBLE);
            btnWatchAd.setVisibility(isPremiumCoin ? View.GONE : View.VISIBLE);
            tvAdStatus.setVisibility(View.GONE);

            // 광고 시청 버튼 위치 조정
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) btnWatchAd.getLayoutParams();
            if (params != null) {
                params.topMargin = (int) (80 * getResources().getDisplayMetrics().density);
                btnWatchAd.setLayoutParams(params);
            }

            // 모든 콘텐츠 마스킹 처리
            tvTargetPrice.setText(getString(R.string.masked_content));
            tvStopLoss.setText(getString(R.string.masked_content));
            tvRiskReward.setText(getString(R.string.masked_content_short));
            tvStrategyDetail.setText(getString(R.string.masked_strategy_content));

            // ★ 최근 크로스 정보 마스킹 처리
            if (tvRecentCross != null) {
                tvRecentCross.setText(getString(R.string.masked_content_cross));
                tvRecentCross.setVisibility(View.VISIBLE);
                // 추가 블러 효과를 위해 투명도 조정
                tvRecentCross.setAlpha(0.3f);
            }

            // 첫 번째 매수 단계만 마스킹해서 표시
            if (strategy != null && strategy.getBuySteps() != null && !strategy.getBuySteps().isEmpty()) {
                displayFirstBuyStepWithBlur(layoutBuySteps, strategy.getBuySteps().get(0));
            }
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