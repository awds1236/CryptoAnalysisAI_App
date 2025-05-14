package com.coinsense.cryptoanalysisai.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.coinsense.cryptoanalysisai.api.BinanceApiService;
import com.coinsense.cryptoanalysisai.api.RetrofitClient;
import com.coinsense.cryptoanalysisai.api.UpbitApiService;
import com.coinsense.cryptoanalysisai.databinding.FragmentChartBinding;
import com.coinsense.cryptoanalysisai.models.BinanceModels;
import com.coinsense.cryptoanalysisai.models.BinanceTicker;
import com.coinsense.cryptoanalysisai.models.CoinInfo;
import com.coinsense.cryptoanalysisai.models.ExchangeType;
import com.coinsense.cryptoanalysisai.models.TickerData;
import com.coinsense.cryptoanalysisai.services.TechnicalIndicatorService;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChartFragment extends Fragment {

    private static final String TAG = "ChartFragment";
    private static final String ARG_COIN_INFO = "arg_coin_info";
    private static final String ARG_EXCHANGE_TYPE = "arg_exchange_type";

    private FragmentChartBinding binding;
    private CoinInfo coinInfo;
    private ExchangeType exchangeType = ExchangeType.UPBIT;
    private Constants.CandleInterval currentInterval = Constants.CandleInterval.DAY_1;
    private Constants.TechnicalIndicator currentIndicator = Constants.TechnicalIndicator.RSI;
    private List<com.coinsense.cryptoanalysisai.models.CandleData> candleDataList = new ArrayList<>();
    private Map<String, Object> technicalIndicators;
    private TechnicalIndicatorService indicatorService;

    public ChartFragment() {
        // 기본 생성자
    }

    /**
     * 인스턴스 생성 메서드
     */
    public static ChartFragment newInstance(CoinInfo coinInfo, ExchangeType exchangeType) {
        ChartFragment fragment = new ChartFragment();
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

        indicatorService = new TechnicalIndicatorService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 기간 탭 초기화
        setupIntervalTabs();

        // 지표 탭 초기화
        setupIndicatorTabs();

        // 데이터 로드
        if (coinInfo != null && coinInfo.getMarket() != null) {
            loadMarketData(coinInfo.getMarket());
        } else {
            binding.tvCoinTitle.setText("코인을 선택해주세요");
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
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
            // 차트 데이터 로드
            loadMarketData(coinInfo.getMarket());
        }
    }

    /**
     * 기간 탭 설정
     */
    private void setupIntervalTabs() {
        // 기간 탭 설정
        TabLayout.Tab dayTab = binding.tabsInterval.newTab().setText("1일");
        dayTab.view.setContentDescription("1일 기간 차트");
        binding.tabsInterval.addTab(dayTab);

        TabLayout.Tab hour4Tab = binding.tabsInterval.newTab().setText("4시간");
        hour4Tab.view.setContentDescription("4시간 기간 차트");
        binding.tabsInterval.addTab(hour4Tab);

        TabLayout.Tab hour1Tab = binding.tabsInterval.newTab().setText("1시간");
        hour1Tab.view.setContentDescription("1시간 기간 차트");
        binding.tabsInterval.addTab(hour1Tab);

        TabLayout.Tab min15Tab = binding.tabsInterval.newTab().setText("15분");
        min15Tab.view.setContentDescription("15분 기간 차트");
        binding.tabsInterval.addTab(min15Tab);

        binding.tabsInterval.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                switch (position) {
                    case 0:
                        currentInterval = Constants.CandleInterval.DAY_1;
                        break;
                    case 1:
                        currentInterval = Constants.CandleInterval.HOUR_4;
                        break;
                    case 2:
                        currentInterval = Constants.CandleInterval.HOUR_1;
                        break;
                    case 3:
                        currentInterval = Constants.CandleInterval.MINUTE_15;
                        break;
                }

                if (coinInfo != null) {
                    loadCandleData(coinInfo.getMarket(), currentInterval);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not used
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 동일한 탭 선택 시 데이터 새로고침
                if (coinInfo != null) {
                    loadCandleData(coinInfo.getMarket(), currentInterval);
                }
            }
        });
    }

    /**
     * 지표 탭 설정
     */
    private void setupIndicatorTabs() {
        // 지표 탭 설정
        TabLayout.Tab rsiTab = binding.tabsIndicator.newTab().setText("RSI");
        rsiTab.view.setContentDescription("RSI 기술 지표");
        binding.tabsIndicator.addTab(rsiTab);

        TabLayout.Tab macdTab = binding.tabsIndicator.newTab().setText("MACD");
        macdTab.view.setContentDescription("MACD 기술 지표");
        binding.tabsIndicator.addTab(macdTab);

        TabLayout.Tab bbTab = binding.tabsIndicator.newTab().setText("볼린저밴드");
        bbTab.view.setContentDescription("볼린저밴드 기술 지표");
        binding.tabsIndicator.addTab(bbTab);

        TabLayout.Tab maTab = binding.tabsIndicator.newTab().setText("이동평균선");
        maTab.view.setContentDescription("이동평균선 기술 지표");
        binding.tabsIndicator.addTab(maTab);

        binding.tabsIndicator.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                switch (position) {
                    case 0:
                        currentIndicator = Constants.TechnicalIndicator.RSI;
                        updateIndicator(Constants.TechnicalIndicator.RSI);
                        break;
                    case 1:
                        currentIndicator = Constants.TechnicalIndicator.MACD;
                        updateIndicator(Constants.TechnicalIndicator.MACD);
                        break;
                    case 2:
                        currentIndicator = Constants.TechnicalIndicator.BOLLINGER;
                        updateIndicator(Constants.TechnicalIndicator.BOLLINGER);
                        break;
                    case 3:
                        currentIndicator = Constants.TechnicalIndicator.SMA;
                        updateIndicator(Constants.TechnicalIndicator.SMA);
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not used
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Not used
            }
        });
    }

    /**
     * 마켓 데이터 로드 (코인 정보 + 차트 데이터)
     */
    private void loadMarketData(String market) {
        if (market == null || market.isEmpty()) return;

        binding.progressBar.setVisibility(View.VISIBLE);

        // 코인 정보 업데이트
        binding.tvCoinTitle.setText(coinInfo != null && coinInfo.getDisplayName() != null ?
                coinInfo.getDisplayName() + " (" + coinInfo.getSymbol() + ")" : market);

        // 현재가 정보 로드
        loadCurrentPrice(market);

        // 캔들 데이터 로드
        loadCandleData(market, currentInterval);
    }

    /**
     * 현재가 정보 로드
     */
    private void loadCurrentPrice(String market) {
        if (exchangeType == ExchangeType.UPBIT) {
            UpbitApiService apiService = RetrofitClient.getUpbitApiService();

            apiService.getTicker(market).enqueue(new Callback<List<TickerData>>() {
                @Override
                public void onResponse(@NonNull Call<List<TickerData>> call, @NonNull Response<List<TickerData>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        TickerData ticker = response.body().get(0);
                        updatePriceInfo(ticker.getTradePrice(), ticker.getChangeRate());
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<TickerData>> call, @NonNull Throwable t) {
                    Log.e(TAG, "현재가 로딩 실패: " + t.getMessage());
                }
            });
        } else if (exchangeType == ExchangeType.BINANCE) {
            BinanceApiService apiService = RetrofitClient.getBinanceApiService();

            // Binance API 호출 부분
            apiService.getTicker(market).enqueue(new Callback<BinanceTicker>() {
                @Override
                public void onResponse(@NonNull Call<BinanceTicker> call, @NonNull Response<BinanceTicker> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        final BinanceTicker ticker = response.body();

                        // 24시간 변화율 정보도 가져오기
                        apiService.get24hTicker(market).enqueue(new Callback<BinanceTicker>() {
                            @Override
                            public void onResponse(@NonNull Call<BinanceTicker> call, @NonNull Response<BinanceTicker> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    BinanceTicker ticker24h = response.body();
                                    updatePriceInfo(ticker.getPrice(), ticker24h.getPriceChangePercent() / 100.0);
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                                Log.e(TAG, "24시간 변화율 로딩 실패: " + t.getMessage());
                                updatePriceInfo(ticker.getPrice(), 0);
                            }
                        });
                    }
                }

                @Override
                public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                    Log.e(TAG, "현재가 로딩 실패: " + t.getMessage());
                }
            });
        }
    }

    /**
     * 가격 정보 업데이트
     */
    private void updatePriceInfo(double price, double changeRate) {
        if (coinInfo != null) {
            coinInfo.setCurrentPrice(price);
            coinInfo.setPriceChange(changeRate);

            if (binding != null) {
                binding.tvCurrentPrice.setText(coinInfo.getFormattedPrice());
                binding.tvPriceChange.setText(coinInfo.getFormattedPriceChange());
                binding.tvPriceChange.setTextColor(changeRate >= 0 ?
                        Color.rgb(76, 175, 80) : Color.rgb(244, 67, 54));
            }
        }
    }

    /**
     * 캔들 데이터 로드
     */
    private void loadCandleData(String market, Constants.CandleInterval interval) {
        binding.progressBar.setVisibility(View.VISIBLE);

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

        Call<List<com.coinsense.cryptoanalysisai.models.CandleData>> call;
        switch (interval) {
            case DAY_1:
                call = apiService.getDayCandles(market, 100);
                break;
            case HOUR_4:
                call = apiService.getMinuteCandles(market, 240, 100);
                break;
            case HOUR_1:
                call = apiService.getHourCandles(market, 100);
                break;
            case MINUTE_15:
                call = apiService.getMinuteCandles(market, 15, 100);
                break;
            default:
                call = apiService.getDayCandles(market, 100);
                break;
        }

        call.enqueue(new Callback<List<com.coinsense.cryptoanalysisai.models.CandleData>>() {
            @Override
            public void onResponse(@NonNull Call<List<com.coinsense.cryptoanalysisai.models.CandleData>> call, @NonNull Response<List<com.coinsense.cryptoanalysisai.models.CandleData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    candleDataList = response.body();
                    binding.progressBar.setVisibility(View.GONE);
                } else {
                    Toast.makeText(getContext(), "데이터 로딩 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    binding.progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<com.coinsense.cryptoanalysisai.models.CandleData>> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 바이낸스 캔들 데이터 로드
     */
    private void loadBinanceCandles(String market, Constants.CandleInterval interval) {
        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.getKlines(market, interval.getBinanceCode(), 100).enqueue(new Callback<List<List<Object>>>() {
            @Override
            public void onResponse(@NonNull Call<List<List<Object>>> call, @NonNull Response<List<List<Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<List<Object>> klines = response.body();
                    candleDataList = new ArrayList<>();

                    for (List<Object> kline : klines) {
                        BinanceModels.BinanceKline binanceKline = new BinanceModels.BinanceKline(kline);
                        candleDataList.add(binanceKline.toUpbitFormat(market));
                    }

                    // 날짜 순서대로 정렬 (최신 -> 과거)
                    Collections.reverse(candleDataList);
                    binding.progressBar.setVisibility(View.GONE);
                } else {
                    Toast.makeText(getContext(), "데이터 로딩 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    binding.progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<List<Object>>> call, @NonNull Throwable t) {
                Toast.makeText(getContext(), "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                binding.progressBar.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 지표 업데이트
     */
    private void updateIndicator(Constants.TechnicalIndicator indicator) {
        if (technicalIndicators == null || technicalIndicators.isEmpty()) return;

        switch (indicator) {
            case RSI:
                updateRsiIndicator();
                break;
            case MACD:
                updateMacdIndicator();
                break;
            case BOLLINGER:
                updateBollingerBands();
                break;
            case SMA:
                updateMovingAverages();
                break;
        }
    }

    /**
     * RSI 지표 업데이트
     */
    private void updateRsiIndicator() {
        if (technicalIndicators == null || !technicalIndicators.containsKey("rsi14")) return;

        double rsi = (double) technicalIndicators.get("rsi14");
        binding.tvIndicatorValue.setText(String.format("RSI(14): %.2f", rsi));

        // 과매수/과매도 상태 표시
        if (rsi > 70) {
            binding.tvIndicatorValue.setTextColor(Color.RED);
        } else if (rsi < 30) {
            binding.tvIndicatorValue.setTextColor(Color.GREEN);
        } else {
            binding.tvIndicatorValue.setTextColor(Color.BLACK);
        }
    }

    /**
     * MACD 지표 업데이트
     */
    private void updateMacdIndicator() {
        if (technicalIndicators == null ||
                !technicalIndicators.containsKey("macdLine") ||
                !technicalIndicators.containsKey("signalLine") ||
                !technicalIndicators.containsKey("histogram")) return;

        double macdLine = (double) technicalIndicators.get("macdLine");
        double signalLine = (double) technicalIndicators.get("signalLine");
        double histogram = (double) technicalIndicators.get("histogram");

        binding.tvIndicatorValue.setText(String.format("MACD: %.2f, Signal: %.2f, Histogram: %.2f",
                macdLine, signalLine, histogram));

        // MACD 신호 표시
        if (macdLine > signalLine) {
            binding.tvIndicatorValue.setTextColor(Color.GREEN);
        } else {
            binding.tvIndicatorValue.setTextColor(Color.RED);
        }
    }

    /**
     * 볼린저 밴드 지표 업데이트
     */
    private void updateBollingerBands() {
        if (technicalIndicators == null ||
                !technicalIndicators.containsKey("bollingerUpper") ||
                !technicalIndicators.containsKey("bollingerMiddle") ||
                !technicalIndicators.containsKey("bollingerLower")) return;

        double upper = (double) technicalIndicators.get("bollingerUpper");
        double middle = (double) technicalIndicators.get("bollingerMiddle");
        double lower = (double) technicalIndicators.get("bollingerLower");

        // 현재가
        double currentPrice = coinInfo != null ? coinInfo.getCurrentPrice() : 0;

        binding.tvIndicatorValue.setText(String.format("볼린저밴드: 상단(%.2f), 중간(%.2f), 하단(%.2f)",
                upper, middle, lower));

        // 볼린저 밴드 위치 표시
        if (currentPrice > upper) {
            binding.tvIndicatorValue.setTextColor(Color.RED);
        } else if (currentPrice < lower) {
            binding.tvIndicatorValue.setTextColor(Color.GREEN);
        } else {
            binding.tvIndicatorValue.setTextColor(Color.BLACK);
        }
    }

    /**
     * 이동평균선 지표 업데이트
     */
    private void updateMovingAverages() {
        if (technicalIndicators == null ||
                !technicalIndicators.containsKey("sma20") ||
                !technicalIndicators.containsKey("ema20")) return;

        double sma20 = (double) technicalIndicators.get("sma20");
        double ema20 = (double) technicalIndicators.get("ema20");

        // 현재가
        double currentPrice = coinInfo != null ? coinInfo.getCurrentPrice() : 0;

        binding.tvIndicatorValue.setText(String.format("SMA(20): %.2f, EMA(20): %.2f", sma20, ema20));

        // 이동평균선 대비 위치 표시
        if (currentPrice > Math.max(sma20, ema20)) {
            binding.tvIndicatorValue.setTextColor(Color.GREEN);
        } else if (currentPrice < Math.min(sma20, ema20)) {
            binding.tvIndicatorValue.setTextColor(Color.RED);
        } else {
            binding.tvIndicatorValue.setTextColor(Color.BLACK);
        }
    }
}