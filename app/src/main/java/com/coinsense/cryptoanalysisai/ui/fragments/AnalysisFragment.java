package com.coinsense.cryptoanalysisai.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.coinsense.cryptoanalysisai.MainActivity;
import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.api.BinanceApiService;
import com.coinsense.cryptoanalysisai.api.RetrofitClient;
import com.coinsense.cryptoanalysisai.databinding.FragmentAnalysisBinding;
import com.coinsense.cryptoanalysisai.models.AnalysisResult;
import com.coinsense.cryptoanalysisai.models.BinanceTicker;
import com.coinsense.cryptoanalysisai.models.CoinInfo;
import com.coinsense.cryptoanalysisai.models.ExchangeType;
import com.coinsense.cryptoanalysisai.services.AdManager;
import com.coinsense.cryptoanalysisai.services.AnalysisApiService;
import com.coinsense.cryptoanalysisai.services.SubscriptionManager;
import com.coinsense.cryptoanalysisai.ui.activities.SubscriptionActivity;
import com.coinsense.cryptoanalysisai.ui.dialogs.AdViewDialog;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.SimpleDateFormat;
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

    // ★ 차트 갱신 주기 (1분 = 60,000ms)
    private static final long CHART_REFRESH_INTERVAL = 60 * 1000;

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

    private ProgressBar progressLongShortRatio;
    private TextView tvLongShortRatioText;

    // 새로 추가한 UI 요소 참조
    private TextView tvCrossSignal;
    private TextView tvBuySellRatio;

    // 최근 가격 변동 추적을 위한 변수
    private double lastPrice = 0;

    // 광고 관련 변수 추가
    private AdManager adManager;
    private Button btnTechnicalWatchAd;
    private TextView tvTechnicalAdStatus;
    private Handler adTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable adTimerRunnable;

    // ★ 차트 갱신을 위한 새로운 변수들 추가
    private Handler chartRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable chartRefreshRunnable;
    private boolean isChartAutoRefreshEnabled = false;

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

        analysisApiService = AnalysisApiService.getInstance(requireContext());  // context 전달
        subscriptionManager = SubscriptionManager.getInstance(requireContext());
        adManager = AdManager.getInstance(requireContext());

        // 전략 프래그먼트 초기화
        shortTermFragment = StrategyFragment.newInstance(StrategyFragment.STRATEGY_SHORT_TERM, "$");
        midTermFragment = StrategyFragment.newInstance(StrategyFragment.STRATEGY_MID_TERM, "$");
        longTermFragment = StrategyFragment.newInstance(StrategyFragment.STRATEGY_LONG_TERM, "$");

        subscriptionManager = SubscriptionManager.getInstance(requireContext());
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
                Toast.makeText(getContext(), getString(R.string.select_coin_first), Toast.LENGTH_SHORT).show();
            }
        });

        // UI 초기화
        if (coinInfo != null && coinInfo.getMarket() != null) {
            updateCoin(coinInfo, exchangeType);
        } else {
            binding.tvCoinTitle.setText(getString(R.string.select_coin_first));
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

        // ★ 시간별 전망 구독 버튼 설정 추가 (View Binding 사용)
        binding.btnOutlookSubscribe.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
            startActivity(intent);
        });

        progressLongShortRatio = view.findViewById(R.id.progressLongShortRatio);
        tvLongShortRatioText = view.findViewById(R.id.tvLongShortRatioText);

        // 광고 관련 뷰 찾기
        btnTechnicalWatchAd = view.findViewById(R.id.btnTechnicalWatchAd);
        tvTechnicalAdStatus = view.findViewById(R.id.tvTechnicalAdStatus);

        // 광고 버튼 클릭 이벤트
        btnTechnicalWatchAd.setOnClickListener(v -> {
            showAdDialog();
        });

        // ★ 시간별 전망 광고 버튼 클릭 이벤트 추가 (View Binding 사용)
        binding.btnOutlookWatchAd.setOnClickListener(v -> {
            showAdDialog();
        });

        // 초기 UI 업데이트
        updateTechnicalAccessUI();
        updateOutlookAccessUI(); // ★ 추가


        // 타이머 시작
        startAdTimer();

        // ★ 차트 자동 갱신 시작
        startChartAutoRefresh();
    }

    /**
     * ★ 시간별 전망 접근 UI 업데이트 (새로 추가) - View Binding 사용
     */
    private void updateOutlookAccessUI() {
        if (coinInfo == null || coinInfo.getSymbol() == null || analysisResult == null) return;

        boolean isSubscribed = subscriptionManager.isSubscribed();
        boolean hasAdPermission = adManager.hasActiveAdPermission(coinInfo.getSymbol());
        boolean isPremiumCoin = coinInfo.isPremium();

        AnalysisResult.Outlook outlook = analysisResult.getOutlook();

        if (isSubscribed || hasAdPermission) {
            // 구독자이거나 광고 시청한 경우 콘텐츠 표시
            binding.outlookBlurOverlay.setVisibility(View.GONE);
            binding.outlookPixelatedOverlay.setVisibility(View.GONE);
            binding.btnOutlookSubscribe.setVisibility(View.GONE);
            binding.btnOutlookWatchAd.setVisibility(View.GONE);
            binding.cardOutlook.setAlpha(1.0f);

            // 구독자가 아니고 광고 시청한 경우 남은 시간 표시
            if (!isSubscribed && hasAdPermission) {
                int remainingMinutes = adManager.getRemainingMinutes(coinInfo.getSymbol());
                binding.tvOutlookAdStatus.setVisibility(View.VISIBLE);
                binding.tvOutlookAdStatus.setText(getString(R.string.ad_remaining_minutes_format, remainingMinutes));
            } else {
                binding.tvOutlookAdStatus.setVisibility(View.GONE);
            }

            // 실제 시간별 전망 내용 표시
            if (outlook != null) {
                // 단기 전망 - 키워드 강조
                String shortTerm = outlook.getShortTerm();
                if (shortTerm != null && !shortTerm.isEmpty()) {
                    shortTerm = highlightKeywords(shortTerm);
                    binding.tvShortTerm.setText(Html.fromHtml(shortTerm, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    binding.tvShortTerm.setText(getString(R.string.no_info));
                }

                // 중기 전망 - 키워드 강조
                String midTerm = outlook.getMidTerm();
                if (midTerm != null && !midTerm.isEmpty()) {
                    midTerm = highlightKeywords(midTerm);
                    binding.tvMidTerm.setText(Html.fromHtml(midTerm, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    binding.tvMidTerm.setText(getString(R.string.no_info));
                }

                // 장기 전망 - 키워드 강조
                String longTerm = outlook.getLongTerm();
                if (longTerm != null && !longTerm.isEmpty()) {
                    longTerm = highlightKeywords(longTerm);
                    binding.tvLongTerm.setText(Html.fromHtml(longTerm, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    binding.tvLongTerm.setText(getString(R.string.no_info));
                }
            }
        } else {
            // 구독자도 아니고 광고도 안 본 경우 콘텐츠 가림
            binding.outlookBlurOverlay.setVisibility(View.VISIBLE);
            binding.outlookPixelatedOverlay.setVisibility(View.VISIBLE);
            binding.btnOutlookSubscribe.setVisibility(View.VISIBLE);
            binding.btnOutlookWatchAd.setVisibility(isPremiumCoin ? View.GONE : View.VISIBLE);
            binding.cardOutlook.setAlpha(0.5f);
            binding.tvOutlookAdStatus.setVisibility(View.GONE);

            // 콘텐츠 마스킹 처리
            binding.tvShortTerm.setText(getString(R.string.masked_content));
            binding.tvMidTerm.setText(getString(R.string.masked_content));
            binding.tvLongTerm.setText(getString(R.string.masked_content));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // ★ 화면이 다시 보일 때 차트 자동 갱신 시작
        startChartAutoRefresh();
        Log.d(TAG, "onResume: 차트 자동 갱신 시작");
    }

    @Override
    public void onPause() {
        super.onPause();
        // ★ 화면이 보이지 않을 때 차트 자동 갱신 중지
        stopChartAutoRefresh();
        Log.d(TAG, "onPause: 차트 자동 갱신 중지");
    }

    @Override
    public void onDestroyView() {
        stopAdTimer();
        // ★ 뷰가 파괴될 때 차트 자동 갱신 중지
        stopChartAutoRefresh();
        super.onDestroyView();
        binding = null;
    }

    // ★ 차트 자동 갱신 시작
    private void startChartAutoRefresh() {
        if (isChartAutoRefreshEnabled || coinInfo == null) {
            return; // 이미 실행 중이거나 코인 정보가 없으면 시작하지 않음
        }

        isChartAutoRefreshEnabled = true;

        chartRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isChartAutoRefreshEnabled && isAdded() && coinInfo != null) {
                    Log.d(TAG, "차트 자동 갱신 실행: " + coinInfo.getSymbol());

                    // 현재 활성화된 전략 프래그먼트의 차트 갱신
                    refreshCurrentStrategyChart();

                    // 다음 갱신 예약 (1분 후)
                    chartRefreshHandler.postDelayed(this, CHART_REFRESH_INTERVAL);
                }
            }
        };

        // 첫 번째 갱신을 1분 후에 시작 (즉시 갱신하지 않음)
        chartRefreshHandler.postDelayed(chartRefreshRunnable, CHART_REFRESH_INTERVAL);

        Log.d(TAG, "차트 자동 갱신 스케줄링 완료 (1분 간격)");
    }

    // ★ 차트 자동 갱신 중지
    private void stopChartAutoRefresh() {
        isChartAutoRefreshEnabled = false;

        if (chartRefreshHandler != null && chartRefreshRunnable != null) {
            chartRefreshHandler.removeCallbacks(chartRefreshRunnable);
            Log.d(TAG, "차트 자동 갱신 중지 완료");
        }
    }

    // ★ 현재 활성화된 전략 프래그먼트의 차트 갱신
    private void refreshCurrentStrategyChart() {
        try {
            // 현재 선택된 탭의 인덱스 가져오기
            int currentTabPosition = binding.viewPagerStrategy.getCurrentItem();

            // 해당 프래그먼트 찾기
            StrategyFragment currentFragment = getCurrentStrategyFragment(currentTabPosition);

            if (currentFragment != null && currentFragment.isAdded() && currentFragment.getView() != null) {
                Log.d(TAG, "현재 활성 탭(" + currentTabPosition + ") 차트 갱신 시작");

                // 차트 갱신 실행
                currentFragment.refreshChartData();

                Log.d(TAG, "차트 갱신 완료: " + getCurrentTabName(currentTabPosition));
            } else {
                Log.w(TAG, "활성 프래그먼트를 찾을 수 없음 또는 준비되지 않음");
            }
        } catch (Exception e) {
            Log.e(TAG, "차트 갱신 중 오류 발생: " + e.getMessage());
        }
    }

    // ★ 현재 탭 이름 반환 (로깅용)
    private String getCurrentTabName(int position) {
        switch (position) {
            case 0: return "단기";
            case 1: return "중기";
            case 2: return "장기";
            default: return "알 수 없음";
        }
    }

    /**
     * 전략 탭 설정 - 탭 전환 시 차트 새로고침 추가
     */
    private void setupStrategyTabs() {
        strategiesAdapter = new StrategiesAdapter(this);
        binding.viewPagerStrategy.setAdapter(strategiesAdapter);

        new TabLayoutMediator(binding.tabsStrategy, binding.viewPagerStrategy, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(getString(R.string.short_term));
                    break;
                case 1:
                    tab.setText(getString(R.string.mid_term));
                    break;
                case 2:
                    tab.setText(getString(R.string.long_term));
                    break;
            }
        }).attach();

        // 탭 선택 리스너 추가 - 차트 새로고침
        binding.tabsStrategy.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                Log.d(TAG, "탭 선택됨: " + position);

                // 현재 선택된 프래그먼트의 차트 새로고침
                refreshStrategyFragmentChart(position);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 사용하지 않음
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 같은 탭 재선택시에도 차트 새로고침
                int position = tab.getPosition();
                Log.d(TAG, "탭 재선택됨: " + position);
                refreshStrategyFragmentChart(position);
            }
        });

        // ViewPager 페이지 변경 리스너도 추가
        binding.viewPagerStrategy.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(TAG, "ViewPager 페이지 선택됨: " + position);

                // 페이지가 완전히 표시된 후 차트 새로고침
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    refreshStrategyFragmentChart(position);
                }, 200);
            }
        });
    }

    /**
     * 전략 프래그먼트 차트 새로고침
     */
    private void refreshStrategyFragmentChart(int position) {
        try {
            StrategyFragment currentFragment = getCurrentStrategyFragment(position);
            if (currentFragment != null) {
                Log.d(TAG, "프래그먼트 찾음, 차트 새로고침 시작: " + position);

                // 약간의 딜레이 후 차트 업데이트 (프래그먼트가 완전히 표시된 후)
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (currentFragment.isAdded() && currentFragment.getView() != null) {
                        currentFragment.forceUpdateChart();
                        Log.d(TAG, "차트 새로고침 완료: " + position);
                    }
                }, 100);
            } else {
                Log.w(TAG, "전략 프래그먼트를 찾을 수 없음: " + position);
            }
        } catch (Exception e) {
            Log.e(TAG, "차트 새로고침 오류: " + e.getMessage());
        }
    }

    /**
     * 현재 전략 프래그먼트 가져오기
     */
    private StrategyFragment getCurrentStrategyFragment(int position) {
        try {
            // ViewPager2의 프래그먼트 태그는 "f" + position 형식
            String tag = "f" + position;
            Fragment fragment = getChildFragmentManager().findFragmentByTag(tag);

            if (fragment instanceof StrategyFragment) {
                return (StrategyFragment) fragment;
            } else {
                // 다른 방법으로 프래그먼트 찾기
                switch (position) {
                    case 0:
                        return shortTermFragment;
                    case 1:
                        return midTermFragment;
                    case 2:
                        return longTermFragment;
                    default:
                        return null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getCurrentStrategyFragment 오류: " + e.getMessage());
            return null;
        }
    }

    /**
     * 코인 정보 업데이트 (코인 선택 시 호출)
     */
    public void updateCoin(CoinInfo coinInfo, ExchangeType exchangeType) {
        this.coinInfo = coinInfo;
        this.exchangeType = exchangeType;

        if (binding != null && coinInfo != null && isAdded()) {
            // 타이틀 설정 - 심볼 중복 추가 없이 displayName만 사용
            binding.tvCoinTitle.setText(coinInfo.getDisplayName() != null ?
                    coinInfo.getDisplayName() : coinInfo.getMarket());

            // 거래소 정보 설정 - 리소스 사용
            binding.tvExchangeInfo.setText(getString(R.string.exchange_info,
                    exchangeType.getDisplayName(requireContext()),
                    exchangeType == ExchangeType.UPBIT ? getString(R.string.krw_unit) : getString(R.string.usd_unit)));

            // AWS Lambda API에서 분석 결과 로드 - 항상 새로고침
            loadAnalysisFromApi();

            // 현재 가격 정보 업데이트
            updatePrice();

            // 모든 UI를 새로고침하여 권한이 올바르게 적용되도록 함
            refreshAllUIs();

            // ★ 새로운 코인이 선택되면 차트 자동 갱신 재시작
            stopChartAutoRefresh();
            startChartAutoRefresh();

            Log.d(TAG, "새로운 코인 선택으로 차트 자동 갱신 재시작: " + coinInfo.getSymbol());
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
                                Log.e(TAG, getString(R.string.price_change_load_failed, t.getMessage()));
                            }
                        });
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                Log.e(TAG, getString(R.string.network_error, t.getMessage()));
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
        String priceText = getString(R.string.analysis_button_format,
                pricePrefix, coinInfo.getFormattedPrice(), coinInfo.getFormattedPriceChange());

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

            // 위치에 따른 문자열 결정
            String zoneText;
            if (position < 0.3) {
                zoneText = getString(R.string.support_zone);
            } else if (position > 0.7) {
                zoneText = getString(R.string.resistance_zone);
            } else {
                zoneText = getString(R.string.neutral_zone);
            }

            // 저항선까지 % 계산
            double percentToResistance = ((closestResistance - currentPrice) / currentPrice) * 100;
            // 지지선까지 % 계산
            double percentToSupport = ((currentPrice - closestSupport) / currentPrice) * 100;

            binding.tvPricePosition.setText(
                    getString(R.string.current_price_position,
                            zoneText, percentToResistance, percentToSupport));
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
    public void loadAnalysisFromApi() {
        if (binding == null || coinInfo == null) return;

        binding.progressAnalysis.setVisibility(View.VISIBLE);

        // 분석 버튼 비활성화
        binding.btnStartAnalysis.setEnabled(false);
        binding.btnStartAnalysis.setText(getString(R.string.analysis_loading));

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
                            Toast.makeText(getContext(), getString(R.string.no_analysis_data), Toast.LENGTH_SHORT).show();
                            binding.progressAnalysis.setVisibility(View.GONE);
                            binding.btnStartAnalysis.setEnabled(true);
                            binding.btnStartAnalysis.setText(getString(R.string.analysis_not_found_retry));
                        });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (getActivity() == null || binding == null) return;

                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), getString(R.string.analysis_load_error, errorMessage), Toast.LENGTH_SHORT).show();
                            binding.progressAnalysis.setVisibility(View.GONE);
                            binding.btnStartAnalysis.setEnabled(true);
                            binding.btnStartAnalysis.setText(getString(R.string.retry));
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

        // 분석 결과의 언어 확인
        String analysisLanguage = analysisResult.getLanguage();
        // 현재 설정된 앱 언어 확인
        SharedPreferences prefs = getContext().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String appLanguage = prefs.getString("pref_language", "ko"); // 기본값은 한국어

        // 언어가 일치하지 않는 경우 로그 출력
        if (!appLanguage.equals(analysisLanguage)) {
            Log.w(TAG, "앱 언어와 분석 결과 언어가 일치하지 않습니다. 앱: " + appLanguage + ", 분석: " + analysisLanguage);
        }

        // coinInfo 확인
        if (coinInfo == null) {
            Log.e("AnalysisFragment", "updateAnalysisUI: coinInfo is null");
        } else {
            Log.d("AnalysisFragment", "updateAnalysisUI: coinInfo symbol = " + coinInfo.getSymbol());
        }

        boolean isSubscribed = subscriptionManager.isSubscribed();

        // 분석 시간 표시
        if (analysisResult.getTimestamp() > 0) {
            Date analysisDate = new Date(analysisResult.getTimestamp());

            // 날짜 형식 리소스에서 가져오기
            String dateFormatPattern = getString(R.string.date_format);
            SimpleDateFormat sdf = new SimpleDateFormat(dateFormatPattern, Locale.getDefault());
            sdf.setTimeZone(TimeZone.getDefault());

            // 날짜를 형식에 맞게 변환
            String formattedDate = sdf.format(analysisDate);

            // 최종 텍스트 생성 ("yyyy년 MM월 dd일 HH:mm에 분석됨")
            String timestampText = String.format(getString(R.string.analysis_timestamp_format), formattedDate);

            binding.tvAnalysisTime.setText(timestampText);
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
            binding.tvAnalysisSummary.setText(getString(R.string.no_analysis_summary));
        }

        // 매수/매도 추천
        AnalysisResult.Recommendation recommendation = analysisResult.getRecommendation();
        if (recommendation != null) {
            // 추천 타입에 따라 색상 변경
            Constants.RecommendationType recommendType = Constants.RecommendationType.fromProbabilities(
                    recommendation.getBuyProbability(),
                    recommendation.getSellProbability()
            );
            // 강조된 추천 메시지 구성
            String recommendationTypeText = getString(recommendType.getDisplayNameResId());
            String recommendText = "<b>" + String.format(getString(R.string.recommendation_text_format), recommendationTypeText) + "</b>";
            if (recommendation.getConfidence() >= 8) {
                recommendText += " " + getString(R.string.high_confidence);
            }

            binding.tvRecommendation.setText(Html.fromHtml(recommendText, Html.FROM_HTML_MODE_LEGACY));
            binding.tvRecommendation.setTextColor(recommendType.getColor());

            // 확률 막대 업데이트
            int buyProgress = (int) Math.round(recommendation.getBuyProbability());
            binding.progressProbability.setProgress(buyProgress);

            // 확률 텍스트 업데이트 - 시각적 강조
            StringBuilder probText = new StringBuilder();
            probText.append("<b>")
                    .append(getString(R.string.buy_label))
                    .append("</b><font color='#4CAF50'>")
                    .append(String.format("%.1f%%", recommendation.getBuyProbability()))
                    .append("</font> / <b>")
                    .append(getString(R.string.sell_label))
                    .append("</b><font color='#F44336'>")
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
        }

        Log.d("AnalysisFragment", "Calling updateStrategyFragments()");
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
                binding.tvShortTerm.setText(getString(R.string.no_info));
            }

            // 중기 전망 - 키워드 강조
            String midTerm = outlook.getMidTerm();
            if (midTerm != null && !midTerm.isEmpty()) {
                midTerm = highlightKeywords(midTerm);
                binding.tvMidTerm.setText(Html.fromHtml(midTerm, Html.FROM_HTML_MODE_LEGACY));
            } else {
                binding.tvMidTerm.setText(getString(R.string.no_info));
            }

            // 장기 전망 - 키워드 강조
            String longTerm = outlook.getLongTerm();
            if (longTerm != null && !longTerm.isEmpty()) {
                longTerm = highlightKeywords(longTerm);
                binding.tvLongTerm.setText(Html.fromHtml(longTerm, Html.FROM_HTML_MODE_LEGACY));
            } else {
                binding.tvLongTerm.setText(getString(R.string.no_info));
            }
        }


        updateOutlookAccessUI();  // 새로 추가
        // 기술적 분석 접근 권한 UI 업데이트
        updateTechnicalAccessUI();

        // 위험 요소 - 시각적 강조
        if (analysisResult.getRiskFactors() != null && !analysisResult.getRiskFactors().isEmpty()) {
            StringBuilder riskFactors = new StringBuilder();

            // 위험 키워드 가져오기
            String severeKeywords = getString(R.string.risk_keyword_severe);
            String moderateKeywords = getString(R.string.risk_keyword_moderate);
            String warningSymbol = getString(R.string.risk_warning_symbol);

            for (int i = 0; i < analysisResult.getRiskFactors().size(); i++) {
                String risk = analysisResult.getRiskFactors().get(i);
                String riskLower = risk.toLowerCase();

                // 위험 요소 심각도에 따른 색상 코드
                String colorCode = "#F44336"; // 기본 빨간색

                // 키워드 기반 중요도 판단 - 언어별 키워드 사용
                String[] severeKeywordArray = severeKeywords.split("\\|");
                String[] moderateKeywordArray = moderateKeywords.split("\\|");

                boolean isSevere = false;
                boolean isModerate = false;

                // 심각한 위험 키워드 검사
                for (String keyword : severeKeywordArray) {
                    if (riskLower.contains(keyword)) {
                        isSevere = true;
                        break;
                    }
                }

                // 중간 위험 키워드 검사
                if (!isSevere) {
                    for (String keyword : moderateKeywordArray) {
                        if (riskLower.contains(keyword)) {
                            isModerate = true;
                            break;
                        }
                    }
                }

                if (isSevere) {
                    colorCode = "#D32F2F"; // 더 진한 빨간색
                } else if (isModerate) {
                    colorCode = "#FF9800"; // 주황색
                }

                riskFactors.append("<font color='")
                        .append(colorCode)
                        .append("'>")
                        .append(warningSymbol)
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
     * 기술적 분석 접근 UI 업데이트
     */
    private void updateTechnicalAccessUI() {
        if (coinInfo == null || coinInfo.getSymbol() == null || analysisResult == null) return;

        boolean isSubscribed = subscriptionManager.isSubscribed();
        boolean hasAdPermission = adManager.hasActiveAdPermission(coinInfo.getSymbol());
        boolean isPremiumCoin = false;

        if (coinInfo != null && coinInfo.getSymbol() != null) {
            hasAdPermission = adManager.hasActiveAdPermission(coinInfo.getSymbol());
            isPremiumCoin = coinInfo.isPremium(); // 프리미엄 플래그 확인
        }

        AnalysisResult.TechnicalAnalysis technicalAnalysis = analysisResult.getTechnicalAnalysis();

        if (isSubscribed || hasAdPermission) {
            // 구독자이거나 광고 시청한 경우 콘텐츠 표시
            binding.technicalBlurOverlay.setVisibility(View.GONE);
            binding.technicalPixelatedOverlay.setVisibility(View.GONE);
            binding.btnTechnicalSubscribe.setVisibility(View.GONE);
            btnTechnicalWatchAd.setVisibility(View.GONE);
            binding.cardTechnical.setAlpha(1.0f);

            // 구독자가 아니고 광고 시청한 경우 남은 시간 표시
            if (!isSubscribed && hasAdPermission) {
                int remainingMinutes = adManager.getRemainingMinutes(coinInfo.getSymbol());
                tvTechnicalAdStatus.setVisibility(View.VISIBLE);
                tvTechnicalAdStatus.setText(getString(R.string.ad_remaining_minutes_format, remainingMinutes));
            } else {
                tvTechnicalAdStatus.setVisibility(View.GONE);
            }

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

                    // 언어에 독립적인 비교를 위해 리소스 값과 비교
                    if (trendStrength.equalsIgnoreCase(getString(R.string.trend_strength_strong))) {
                        colorCode = "#4CAF50"; // 녹색
                        strengthText = getString(R.string.trend_strong);
                    } else if (trendStrength.equalsIgnoreCase(getString(R.string.trend_strength_medium))) {
                        colorCode = "#FFC107"; // 노란색
                        strengthText = getString(R.string.trend_medium);
                    } else {
                        colorCode = "#F44336"; // 빨간색
                        strengthText = getString(R.string.trend_weak);
                    }

                    binding.tvTrendStrength.setText(Html.fromHtml("<font color='" +
                            colorCode + "'><b>" + strengthText + "</b></font>", Html.FROM_HTML_MODE_LEGACY));
                } else {
                    binding.tvTrendStrength.setText(getString(R.string.no_info));
                }

                // 주요 패턴 - 키워드 강조
                String pattern = technicalAnalysis.getPattern();
                if (pattern != null && !pattern.isEmpty()) {
                    pattern = highlightKeywords(pattern);
                    binding.tvPattern.setText(Html.fromHtml(pattern, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    binding.tvPattern.setText(getString(R.string.no_info));
                }

                // 이동평균선 신호 표시 - 새로 추가 (있는 경우에만)
                if (binding.tvCrossSignal != null) {
                    String crossSignal = technicalAnalysis.getCrossSignal();
                    if (crossSignal != null && !crossSignal.isEmpty()) {
                        String displayText;
                        String colorCode;

                        switch (crossSignal) {
                            case "GOLDEN_CROSS":
                                displayText = getString(R.string.golden_cross);
                                colorCode = "#4CAF50"; // 녹색
                                break;
                            case "DEATH_CROSS":
                                displayText = getString(R.string.death_cross);
                                colorCode = "#F44336"; // 빨간색
                                break;
                            default:
                                displayText = getString(R.string.no_cross);
                                colorCode = "#FFC107"; // 노란색
                                break;
                        }

                        binding.tvCrossSignal.setText(Html.fromHtml("<font color='" + colorCode + "'><b>" +
                                displayText + "</b></font>", Html.FROM_HTML_MODE_LEGACY));
                    }
                }

                // 롱:숏 비율 표시 - 새로 추가
                // 롱:숏 비율 시각화
                // Use the actual longPercent and shortPercent from the analysis result
                double longPercent = technicalAnalysis.getLongPercent();
                double shortPercent = technicalAnalysis.getShortPercent();

                // Fallback to buySellRatio for backward compatibility
                if (longPercent == 0 && shortPercent == 0 && technicalAnalysis.getBuySellRatio() > 0) {
                    longPercent = technicalAnalysis.getBuySellRatio() * 100;
                    shortPercent = 100 - longPercent;
                }

                // 프로그레스바 설정 (롱 포지션 비율을 표시)
                if (binding.progressLongShortRatio != null) {
                    binding.progressLongShortRatio.setMax(100);
                    binding.progressLongShortRatio.setProgress((int)Math.round(longPercent));
                }

                // 텍스트로 비율 표시
                if (binding.tvLongShortRatioText != null) {
                    String longRatioFormatted = String.format(getString(R.string.long_ratio_format), longPercent);
                    String shortRatioFormatted = String.format(getString(R.string.short_ratio_format), shortPercent);

                    String ratioText = String.format("<font color='#4CAF50'><b>%s</b></font>%s<font color='#F44336'><b>%s</b></font>",
                            longRatioFormatted, getString(R.string.ratio_vs), shortRatioFormatted);

                    binding.tvLongShortRatioText.setText(Html.fromHtml(ratioText, Html.FROM_HTML_MODE_LEGACY));

                    // 추가 정보 - 현재 시장 편향
                    String marketBias;
                    String biasColor;
                    if (longPercent > shortPercent + 10) {
                        marketBias = getString(R.string.market_bias_strong_long);
                        biasColor = getString(R.string.color_strong_long); // "#4CAF50" 대신 리소스 사용
                    } else if (longPercent > shortPercent) {
                        marketBias = getString(R.string.market_bias_weak_long);
                        biasColor = getString(R.string.color_weak_long); // "#8BC34A" 대신 리소스 사용
                    } else if (shortPercent > longPercent + 10) {
                        marketBias = getString(R.string.market_bias_strong_short);
                        biasColor = getString(R.string.color_strong_short); // "#F44336" 대신 리소스 사용
                    } else if (shortPercent > longPercent) {
                        marketBias = getString(R.string.market_bias_weak_short);
                        biasColor = getString(R.string.color_weak_short); // "#FF9800" 대신 리소스 사용
                    } else {
                        marketBias = getString(R.string.market_bias_neutral);
                        biasColor = getString(R.string.color_neutral); // "#9E9E9E" 대신 리소스 사용
                    }

                    binding.tvLongShortRatioText.append("\n");
                    binding.tvLongShortRatioText.append(Html.fromHtml(
                            String.format("<br><font color='%s'>%s<b>%s</b></font>",
                                    biasColor, getString(R.string.current_market), marketBias),
                            Html.FROM_HTML_MODE_LEGACY
                    ));
                }

                // 매수/매도 세력 비율 원래 코드 유지 (이미 존재하는 경우)
                if (binding.tvBuySellRatio != null) {
                    double buySellRatio = technicalAnalysis.getBuySellRatio();
                    if (buySellRatio > 0) {
                        String displayText;
                        String colorCode;

                        // 비율에 따른 텍스트 및 색상 결정
                        if (buySellRatio > 0.65) {
                            displayText = String.format(getString(R.string.buy_strength_strong), buySellRatio * 100);
                            colorCode = "#4CAF50"; // 녹색
                        } else if (buySellRatio < 0.35) {
                            displayText = String.format(getString(R.string.sell_strength_strong), (1 - buySellRatio) * 100);
                            colorCode = "#F44336"; // 빨간색
                        } else {
                            displayText = String.format(getString(R.string.market_neutral), buySellRatio * 100);
                            colorCode = "#FFC107"; // 노란색
                        }

                        binding.tvBuySellRatio.setText(Html.fromHtml("<font color='" + colorCode + "'><b>" +
                                displayText + "</b></font>", Html.FROM_HTML_MODE_LEGACY));
                    }
                }
            }
        } else {
            // 구독자도 아니고 광고도 안 본 경우 콘텐츠 가림
            binding.technicalBlurOverlay.setVisibility(View.VISIBLE);
            binding.technicalPixelatedOverlay.setVisibility(View.VISIBLE);
            binding.btnTechnicalSubscribe.setVisibility(View.VISIBLE);
            btnTechnicalWatchAd.setVisibility(isPremiumCoin ? View.GONE : View.VISIBLE);
            binding.cardTechnical.setAlpha(0.5f);
            tvTechnicalAdStatus.setVisibility(View.GONE);

            // 콘텐츠 마스킹 처리
            binding.tvSupport.setText(getString(R.string.masked_content));
            binding.tvResistance.setText(getString(R.string.masked_content));
            binding.tvTrendStrength.setText(getString(R.string.masked_content_short));
            binding.tvPattern.setText(getString(R.string.masked_content));
            binding.tvCrossSignal.setText(getString(R.string.masked_content_short));

            // 롱:숏 비율 마스킹
            if (binding.progressLongShortRatio != null) {
                binding.progressLongShortRatio.setProgress(50); // 중립 상태로 표시
            }
            if (binding.tvLongShortRatioText != null) {
                binding.tvLongShortRatioText.setText(getString(R.string.masked_ratio));
            }
        }
    }

    /**
     * 텍스트에서 주요 키워드 강조 처리
     */
    private String highlightKeywords(String text) {
        if (text == null || text.isEmpty()) return "";

        // 상승/하락 관련 키워드
        String upPattern = getString(R.string.keyword_pattern_up);
        String downPattern = getString(R.string.keyword_pattern_down);
        String neutralPattern = getString(R.string.keyword_pattern_neutral);
        String buyPattern = getString(R.string.keyword_pattern_buy);
        String sellPattern = getString(R.string.keyword_pattern_sell);
        String supportPattern = getString(R.string.keyword_pattern_support);
        String resistancePattern = getString(R.string.keyword_pattern_resistance);
        String patternKeywords = getString(R.string.keyword_pattern_patterns);
        String timeframePattern = getString(R.string.keyword_pattern_timeframe);

        // 색상 값 가져오기
        String upColor = getString(R.string.color_up);
        String downColor = getString(R.string.color_down);
        String neutralColor = getString(R.string.color_neutral);
        String buyColor = getString(R.string.color_buy);
        String sellColor = getString(R.string.color_sell);
        String supportColor = getString(R.string.color_support);
        String resistanceColor = getString(R.string.color_resistance);
        String patternColor = getString(R.string.color_pattern);
        String timeframeColor = getString(R.string.color_timeframe);

        // 상승/하락 관련 키워드
        text = text.replaceAll(upPattern, "<font color='" + upColor + "'><b>$1</b></font>");
        text = text.replaceAll(downPattern, "<font color='" + downColor + "'><b>$1</b></font>");

        // 중립/횡보 관련 키워드
        text = text.replaceAll(neutralPattern, "<font color='" + neutralColor + "'><b>$1</b></font>");

        // 투자 전략 관련 키워드
        text = text.replaceAll(buyPattern, "<font color='" + buyColor + "'><b>$1</b></font>");
        text = text.replaceAll(sellPattern, "<font color='" + sellColor + "'><b>$1</b></font>");

        // 가격 및 패턴 관련 키워드
        text = text.replaceAll(supportPattern, "<font color='" + supportColor + "'><b>$1</b></font>");
        text = text.replaceAll(resistancePattern, "<font color='" + resistanceColor + "'><b>$1</b></font>");
        text = text.replaceAll(patternKeywords, "<font color='" + patternColor + "'><b>$1</b></font>");

        // 시간 관련 키워드
        text = text.replaceAll(timeframePattern, "<font color='" + timeframeColor + "'><b>$1</b></font>");

        return text;
    }

    // 모든 UI를 새로 고치는 새 메서드 추가
    public void refreshAllUIs() {
        updateOutlookAccessUI();    // 새로 추가
        // 자신의 UI 업데이트
        updateTechnicalAccessUI();

        // 전략 프래그먼트들의 UI 업데이트 - 각 프래그먼트가 준비되었는지 확인
        if (shortTermFragment != null && shortTermFragment.getView() != null) {
            // coinInfo 정보 전달 및 UI 업데이트
            shortTermFragment.setCoinInfo(coinInfo);
            shortTermFragment.updateContentAccessUI();
        }
        if (midTermFragment != null && midTermFragment.getView() != null) {
            midTermFragment.setCoinInfo(coinInfo);
            midTermFragment.updateContentAccessUI();
        }
        if (longTermFragment != null && longTermFragment.getView() != null) {
            longTermFragment.setCoinInfo(coinInfo);
            longTermFragment.updateContentAccessUI();
        }
    }

    /**
     * 전략 프래그먼트 업데이트
     */
    private void updateStrategyFragments() {
        if (analysisResult == null) return;

        // coinInfo가 null인지 확인
        if (coinInfo == null) {
            Log.e("AnalysisFragment", "updateStrategyFragments: coinInfo is null");
            return;
        }

        String currencySymbol = analysisResult.getCurrencySymbol();

        // 단기 전략 업데이트
        if (shortTermFragment != null && analysisResult.getShortTermStrategy() != null) {
            shortTermFragment.setStrategy(analysisResult.getShortTermStrategy());
            shortTermFragment.setCoinInfo(coinInfo); // coinInfo를 프래그먼트에 전달
            Log.d("AnalysisFragment", getString(R.string.short_term_fragment_updated));
        }

        // 중기 전략 업데이트
        if (midTermFragment != null && analysisResult.getMidTermStrategy() != null) {
            midTermFragment.setStrategy(analysisResult.getMidTermStrategy());
            midTermFragment.setCoinInfo(coinInfo); // coinInfo를 프래그먼트에 전달
            Log.d("AnalysisFragment", getString(R.string.mid_term_fragment_updated));
        }

        // 장기 전략 업데이트
        if (longTermFragment != null && analysisResult.getLongTermStrategy() != null) {
            longTermFragment.setStrategy(analysisResult.getLongTermStrategy());
            longTermFragment.setCoinInfo(coinInfo); // coinInfo를 프래그먼트에 전달
            Log.d("AnalysisFragment", getString(R.string.long_term_fragment_updated));
        }
    }

    /**
     * 광고 대화상자 표시
     */
    private void showAdDialog() {
        if (getActivity() == null || coinInfo == null) return;

        AdViewDialog dialog = AdViewDialog.newInstance(
                coinInfo.getSymbol(),
                coinInfo.getDisplayName()
        );

        dialog.setCompletionListener(coinSymbol -> {
            // 광고 시청 완료 - UI 업데이트
            updateTechnicalAccessUI();
        });

        dialog.show(getParentFragmentManager(), "ad_dialog");
    }

    /**
     * 광고 타이머 시작
     */
    private void startAdTimer() {
        adTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded() && coinInfo != null) {
                    updateTechnicalAccessUI();
                }
                adTimerHandler.postDelayed(this, 60000); // 1분마다 업데이트
            }
        };

        adTimerHandler.post(adTimerRunnable);
    }

    /**
     * 광고 타이머 중지
     */
    private void stopAdTimer() {
        if (adTimerHandler != null && adTimerRunnable != null) {
            adTimerHandler.removeCallbacks(adTimerRunnable);
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