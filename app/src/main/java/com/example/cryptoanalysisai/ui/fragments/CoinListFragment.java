package com.example.cryptoanalysisai.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cryptoanalysisai.R;
import com.example.cryptoanalysisai.api.BinanceApiService;
import com.example.cryptoanalysisai.api.RetrofitClient;
import com.example.cryptoanalysisai.api.UpbitApiService;
import com.example.cryptoanalysisai.databinding.FragmentCoinListBinding;
import com.example.cryptoanalysisai.models.BinanceModels;
import com.example.cryptoanalysisai.models.BinanceTicker;
import com.example.cryptoanalysisai.models.BinanceKline;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.models.TickerData;
import com.example.cryptoanalysisai.models.firebase.FirestoreAnalysisResult;
import com.example.cryptoanalysisai.services.AnalysisService;
import com.example.cryptoanalysisai.services.FirebaseManager;
import com.example.cryptoanalysisai.services.TechnicalIndicatorService;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CoinListFragment extends Fragment {

    private static final String TAG = "CoinListFragment";
    private static final String ARG_EXCHANGE_TYPE = "arg_exchange_type";

    // 주요 코인 5개 - 메인 화면에 우선 표시
    private static final List<String> TOP_COINS = Arrays.asList("BTC", "ETH", "XRP", "SOL", "ADA");

    private FragmentCoinListBinding binding;
    private CoinListAdapter adapter;
    private OnCoinSelectedListener listener;
    private ExchangeType exchangeType = ExchangeType.UPBIT;
    private FirebaseManager firebaseManager;
    private AnalysisService analysisService;
    private TechnicalIndicatorService indicatorService;

    // 코인 심볼별 최신 분석 결과 캐시
    private Map<String, FirestoreAnalysisResult> analysisCache = new HashMap<>();

    // 모든 코인 리스트 (검색용)
    private List<CoinInfo> allCoins = new ArrayList<>();

    // 주요 코인만 표시하는 플래그
    private boolean showOnlyTopCoins = true;

    public CoinListFragment() {
        // 기본 생성자
    }

    /**
     * 인스턴스 생성 메서드
     */
    public static CoinListFragment newInstance(ExchangeType exchangeType) {
        CoinListFragment fragment = new CoinListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EXCHANGE_TYPE, exchangeType.getCode());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            String exchangeCode = getArguments().getString(ARG_EXCHANGE_TYPE);
            exchangeType = ExchangeType.fromCode(exchangeCode);
        }

        firebaseManager = FirebaseManager.getInstance();
        analysisService = new AnalysisService();
        indicatorService = new TechnicalIndicatorService();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCoinListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 거래소 라디오 버튼 초기화
        initExchangeRadioGroup();

        // 검색 기능 초기화
        initSearchBar();

        // 리사이클러뷰 초기화
        initRecyclerView();

        // 주요 코인 데이터 로드
        loadTopCoins();

        // 새로고침 기능 설정
        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshData);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnCoinSelectedListener) {
            listener = (OnCoinSelectedListener) context;
        } else {
            throw new RuntimeException(context + " must implement OnCoinSelectedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * 거래소 라디오 버튼 초기화
     */
    private void initExchangeRadioGroup() {
        // 현재 선택된 거래소에 맞게 라디오 버튼 선택
        if (exchangeType == ExchangeType.UPBIT) {
            binding.rbUpbit.setChecked(true);
        } else {
            binding.rbBinance.setChecked(true);
        }

        // 라디오 버튼 변경 리스너
        binding.exchangeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbUpbit) {
                exchangeType = ExchangeType.UPBIT;
            } else if (checkedId == R.id.rbBinance) {
                exchangeType = ExchangeType.BINANCE;
            }

            // 거래소 변경 시 주요 코인만 새로고침
            showOnlyTopCoins = true;
            loadTopCoins();
        });
    }

    /**
     * 검색 기능 초기화
     */
    private void initSearchBar() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) {
                    // 검색 시작하면 모든 코인 로드
                    if (s.length() > 0 && showOnlyTopCoins) {
                        showOnlyTopCoins = false;
                        loadAllCoins();
                        return;
                    }
                    adapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not used
            }
        });
    }

    /**
     * 리사이클러뷰 초기화
     */
    private void initRecyclerView() {
        adapter = new CoinListAdapter(new ArrayList<>(), coinInfo -> {
            if (listener != null) {
                listener.onCoinSelected(coinInfo, exchangeType);
            }
        });

        binding.recyclerCoins.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerCoins.setAdapter(adapter);
    }

    /**
     * 주요 코인만 로드 (성능 향상)
     */
    private void loadTopCoins() {
        showLoading(true);

        // 1. Firebase에서 주요 코인의 분석 결과만 로드
        loadTopCoinsAnalyses();

        // 2. 거래소에 따라 주요 코인 정보 로드
        if (exchangeType == ExchangeType.UPBIT) {
            loadUpbitMarkets(true);
        } else {
            loadBinanceMarkets(true);
        }
    }

    /**
     * 모든 코인 로드 (검색 시 사용)
     */
    private void loadAllCoins() {
        if (!allCoins.isEmpty()) {
            // 이미 모든 코인 데이터가 있으면 바로 표시
            updateCoinList(allCoins);
            // 일정 시간 후 모든 코인에 대한 분석 시작
            performAnalysisForAllCoins(allCoins);
            return;
        }

        showLoading(true);

        // 모든 코인 로드
        if (exchangeType == ExchangeType.UPBIT) {
            loadUpbitMarkets(false);
        } else {
            loadBinanceMarkets(false);
        }
    }

    /**
     * Firebase에서 주요 코인의 분석 결과만 로드
     */
    private void loadTopCoinsAnalyses() {
        firebaseManager.getAllLatestAnalyses(new FirebaseManager.OnAllAnalysesRetrievedListener() {
            @Override
            public void onAllAnalysesRetrieved(List<FirestoreAnalysisResult> resultList) {
                // 분석 결과 캐시 업데이트
                analysisCache.clear();

                // 거래소에 맞는 분석 결과만 필터링 (전체 결과는 캐싱)
                for (FirestoreAnalysisResult result : resultList) {
                    if (exchangeType.getCode().equals(result.getExchange())) {
                        String cacheKey = result.getCoinSymbol();
                        analysisCache.put(cacheKey, result);
                    }
                }

                // 어댑터에 분석 결과 제공 (어댑터가 이미 초기화된 경우)
                if (adapter != null) {
                    adapter.setAnalysisResults(analysisCache);
                    adapter.notifyDataSetChanged();
                }

                Log.d(TAG, "Firebase에서 " + analysisCache.size() + "개의 분석 결과 로드됨");
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Firebase 분석 결과 로드 실패: " + errorMessage);
            }
        });
    }

    /**
     * 업비트 마켓 목록 로드
     */
    private void loadUpbitMarkets(final boolean topCoinsOnly) {
        UpbitApiService apiService = RetrofitClient.getUpbitApiService();

        apiService.getMarkets(true).enqueue(new Callback<List<CoinInfo>>() {
            @Override
            public void onResponse(@NonNull Call<List<CoinInfo>> call, @NonNull Response<List<CoinInfo>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<CoinInfo> allMarkets = response.body();
                    List<CoinInfo> krwMarkets = new ArrayList<>();

                    // KRW 마켓만 필터링
                    for (CoinInfo market : allMarkets) {
                        if (market.getMarket().startsWith("KRW-")) {
                            krwMarkets.add(market);
                        }
                    }

                    // 모든 코인 리스트 업데이트 (검색 시 사용)
                    allCoins = new ArrayList<>(krwMarkets);

                    // 주요 코인만 추출
                    if (topCoinsOnly) {
                        List<CoinInfo> topCoinsList = new ArrayList<>();
                        for (CoinInfo market : krwMarkets) {
                            if (TOP_COINS.contains(market.getSymbol())) {
                                topCoinsList.add(market);
                            }
                        }

                        // 상위 코인 가격 정보 로드
                        loadUpbitPrices(topCoinsList, true);
                    } else {
                        // 모든 코인 가격 정보 로드
                        loadUpbitPrices(krwMarkets, false);
                    }
                } else {
                    showError("업비트 마켓 정보를 가져오는데 실패했습니다.");
                    showLoading(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CoinInfo>> call, @NonNull Throwable t) {
                showError("네트워크 오류: " + t.getMessage());
                showLoading(false);
            }
        });
    }

    /**
     * 업비트 가격 정보 로드
     */
    private void loadUpbitPrices(List<CoinInfo> markets, final boolean analyzeAfterLoading) {
        if (markets.isEmpty()) {
            showError("마켓 정보가 없습니다.");
            showLoading(false);
            return;
        }

        UpbitApiService apiService = RetrofitClient.getUpbitApiService();

        // 마켓 코드 문자열 생성 (KRW-BTC,KRW-ETH,...)
        StringBuilder marketCodesBuilder = new StringBuilder();
        for (int i = 0; i < markets.size(); i++) {
            if (i > 0) marketCodesBuilder.append(",");
            marketCodesBuilder.append(markets.get(i).getMarket());
        }
        String marketCodes = marketCodesBuilder.toString();

        apiService.getTicker(marketCodes).enqueue(new Callback<List<TickerData>>() {
            @Override
            public void onResponse(@NonNull Call<List<TickerData>> call, @NonNull Response<List<TickerData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<TickerData> tickers = response.body();

                    // 코인 정보에 가격 데이터 추가
                    for (CoinInfo market : markets) {
                        for (TickerData ticker : tickers) {
                            if (market.getMarket().equals(ticker.getMarket())) {
                                market.setCurrentPrice(ticker.getTradePrice());
                                market.setPriceChange(ticker.getChangeRate());
                                break;
                            }
                        }
                    }

                    // 코인 목록 정렬 (시가총액 또는 가격 높은 순)
                    Collections.sort(markets, new Comparator<CoinInfo>() {
                        @Override
                        public int compare(CoinInfo o1, CoinInfo o2) {
                            return Double.compare(o2.getCurrentPrice(), o1.getCurrentPrice());
                        }
                    });

                    // 리스트 갱신
                    updateCoinList(markets);

                    // 분석 진행 (주요 코인인 경우만)
                    if (analyzeAfterLoading && showOnlyTopCoins) {
                        Toast.makeText(getContext(), "주요 코인 분석 중...", Toast.LENGTH_SHORT).show();
                        performAnalysisForTopCoins(markets);
                    }
                } else {
                    showError("가격 정보를 가져오는데 실패했습니다.");
                    showLoading(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TickerData>> call, @NonNull Throwable t) {
                showError("네트워크 오류: " + t.getMessage());
                showLoading(false);
            }
        });
    }

    /**
     * 바이낸스 마켓 목록 로드 최적화 버전
     */
    private void loadBinanceMarkets(final boolean topCoinsOnly) {
        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        // JSON 형식의 심볼 목록 생성 (["BTCUSDT", "ETHUSDT", "XRPUSDT", "SOLUSDT", "ADAUSDT"])
        StringBuilder symbolsBuilder = new StringBuilder("[");
        List<String> symbolsToQuery = new ArrayList<>();

        if (topCoinsOnly) {
            // 주요 코인만 조회
            for (String coin : TOP_COINS) {
                symbolsToQuery.add("\"" + coin + "USDT\"");
            }
        } else {
            // 더 많은 코인 추가 (전체는 아니고 자주 거래되는 주요 30개 코인만)
            // 이미 TOP_COINS에 있는 코인들
            symbolsToQuery.add("\"BTCUSDT\""); // 비트코인
            symbolsToQuery.add("\"ETHUSDT\""); // 이더리움
            symbolsToQuery.add("\"XRPUSDT\""); // 리플
            symbolsToQuery.add("\"SOLUSDT\""); // 솔라나
            symbolsToQuery.add("\"ADAUSDT\""); // 에이다

            // 추가 주요 코인들
            symbolsToQuery.add("\"DOGEUSDT\""); // 도지코인
            symbolsToQuery.add("\"BNBUSDT\""); // 바이낸스 코인
            symbolsToQuery.add("\"DOTUSDT\""); // 폴카닷
            symbolsToQuery.add("\"TRXUSDT\""); // 트론
            symbolsToQuery.add("\"LINKUSDT\""); // 체인링크
            symbolsToQuery.add("\"MATICUSDT\""); // 폴리곤
            symbolsToQuery.add("\"AVAXUSDT\""); // 아발란체
            symbolsToQuery.add("\"UNIUSDT\""); // 유니스왑
            symbolsToQuery.add("\"SHIBUSDT\""); // 시바이누
            symbolsToQuery.add("\"ATOMUSDT\""); // 코스모스
            symbolsToQuery.add("\"NEARUSDT\""); // 니어
            symbolsToQuery.add("\"XLMUSDT\""); // 스텔라루멘
            symbolsToQuery.add("\"ETCUSDT\""); // 이더리움 클래식
            symbolsToQuery.add("\"FTMUSDT\""); // 팬텀
            symbolsToQuery.add("\"SANDUSDT\""); // 샌드박스
            symbolsToQuery.add("\"MANAUSDT\""); // 디센트럴랜드
            symbolsToQuery.add("\"APTUSDT\""); // 앱토스
            symbolsToQuery.add("\"LTCUSDT\""); // 라이트코인
            symbolsToQuery.add("\"ICPUSDT\""); // 인터넷 컴퓨터
            symbolsToQuery.add("\"AAVEUSDT\""); // 에이브
        }

        // 심볼 목록 완성
        for (int i = 0; i < symbolsToQuery.size(); i++) {
            if (i > 0) symbolsBuilder.append(",");
            symbolsBuilder.append(symbolsToQuery.get(i));
        }
        symbolsBuilder.append("]");

        String symbolsJson = symbolsBuilder.toString();
        Log.d(TAG, "바이낸스 심볼 요청: " + symbolsJson);

        // 심볼 정보 조회
        apiService.getTopSymbolsInfo(symbolsJson).enqueue(new Callback<BinanceModels.BinanceExchangeInfo>() {
            @Override
            public void onResponse(@NonNull Call<BinanceModels.BinanceExchangeInfo> call, @NonNull Response<BinanceModels.BinanceExchangeInfo> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BinanceModels.BinanceExchangeInfo exchangeInfo = response.body();
                    List<CoinInfo> coinInfoList = new ArrayList<>();

                    // SymbolInfo 객체 변환
                    for (BinanceModels.BinanceExchangeInfo.SymbolInfo symbolInfo : exchangeInfo.getSymbols()) {
                        if ("USDT".equals(symbolInfo.getQuoteAsset()) && "TRADING".equals(symbolInfo.getStatus())) {
                            CoinInfo coinInfo = symbolInfo.toUpbitFormat();

                            // 한글 이름 추가
                            String koreanName = getKoreanName(symbolInfo.getBaseAsset());
                            coinInfo.setKoreanName(koreanName);
                            coinInfo.setEnglishName(symbolInfo.getBaseAsset());

                            // 심볼 설정 확인
                            if (coinInfo.getSymbol() == null) {
                                coinInfo.setSymbol(symbolInfo.getBaseAsset());
                            }

                            coinInfoList.add(coinInfo);
                        }
                    }

                    // 모든 코인 리스트 저장 (검색용)
                    if (!topCoinsOnly) {
                        allCoins = new ArrayList<>(coinInfoList);
                    }
                    final boolean analyzeAfterLoading = topCoinsOnly;

                    // 가격 정보 로드 (동일한 심볼 목록 사용)
                    loadBinancePrices(coinInfoList, symbolsJson, analyzeAfterLoading);
                } else {
                    showError("바이낸스 마켓 정보를 가져오는데 실패했습니다. 코드: " + response.code());
                    showLoading(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<BinanceModels.BinanceExchangeInfo> call, @NonNull Throwable t) {
                showError("네트워크 오류: " + t.getMessage());
                showLoading(false);
            }
        });
    }

    /**
     * 바이낸스 가격 정보 로드 (최적화 버전)
     */
    private void loadBinancePrices(List<CoinInfo> markets, String symbolsJson, final boolean analyzeAfterLoading) {
        if (markets.isEmpty()) {
            showError("마켓 정보가 없습니다.");
            showLoading(false);
            return;
        }

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        // 여러 심볼의 가격 정보를 한 번에 요청
        apiService.getMultipleTickers(symbolsJson).enqueue(new Callback<List<BinanceTicker>>() {
            @Override
            public void onResponse(@NonNull Call<List<BinanceTicker>> call, @NonNull Response<List<BinanceTicker>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<BinanceTicker> tickers = response.body();

                    // 24시간 변화율 정보도 한 번에 가져오기
                    apiService.getMultiple24hTickers(symbolsJson).enqueue(new Callback<List<BinanceTicker>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<BinanceTicker>> call, @NonNull Response<List<BinanceTicker>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                List<BinanceTicker> tickers24h = response.body();
                                Map<String, BinanceTicker> ticker24hMap = new HashMap<>();

                                // 심볼별 24시간 데이터 매핑
                                for (BinanceTicker ticker : tickers24h) {
                                    ticker24hMap.put(ticker.getSymbol(), ticker);
                                }

                                // 코인 정보에 가격 데이터 추가
                                for (CoinInfo market : markets) {
                                    // 1. 기본 가격 정보 설정
                                    for (BinanceTicker ticker : tickers) {
                                        if (market.getMarket().equals(ticker.getSymbol())) {
                                            market.setCurrentPrice(ticker.getPrice());
                                            break;
                                        }
                                    }

                                    // 2. 24시간 변화율 설정
                                    BinanceTicker ticker24h = ticker24hMap.get(market.getMarket());
                                    if (ticker24h != null) {
                                        market.setPriceChange(ticker24h.getPriceChangePercent() / 100.0);
                                    }
                                }

                                // 코인 목록 정렬 (알파벳 순이 아닌 시가총액 또는 가격 높은 순)
                                Collections.sort(markets, new Comparator<CoinInfo>() {
                                    @Override
                                    public int compare(CoinInfo o1, CoinInfo o2) {
                                        return Double.compare(o2.getCurrentPrice(), o1.getCurrentPrice());
                                    }
                                });

                                // 리스트 갱신
                                updateCoinList(markets);

                                // 분석 진행 (주요 코인인 경우만)
                                if (analyzeAfterLoading && showOnlyTopCoins) {
                                    Toast.makeText(getContext(), "주요 코인 분석 중...", Toast.LENGTH_SHORT).show();
                                    performAnalysisForTopCoins(markets);
                                }
                            } else {
                                showError("24시간 데이터를 가져오는데 실패했습니다.");
                                showLoading(false);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<List<BinanceTicker>> call, @NonNull Throwable t) {
                            showError("네트워크 오류: " + t.getMessage());
                            showLoading(false);
                        }
                    });
                } else {
                    showError("가격 정보를 가져오는데 실패했습니다.");
                    showLoading(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<BinanceTicker>> call, @NonNull Throwable t) {
                showError("네트워크 오류: " + t.getMessage());
                showLoading(false);
            }
        });
    }

    /**
     * 주요 코인에 대한 분석 수행 (백그라운드)
     */
    private void performAnalysisForTopCoins(List<CoinInfo> coins) {
        // 주요 코인만 필터링
        List<CoinInfo> topCoins = new ArrayList<>();
        for (CoinInfo coin : coins) {
            if (TOP_COINS.contains(coin.getSymbol())) {
                topCoins.add(coin);
            }
        }

        // 순차적으로 분석 수행 (Firestore에 저장)
        for (CoinInfo coin : topCoins) {
            // 이미 분석 결과가 있는 경우 건너뛰기
            if (analysisCache.containsKey(coin.getSymbol())) {
                continue;
            }

            // 캔들 데이터 로드하여 분석
            loadCandleDataAndAnalyze(coin);
        }
    }

    /**
     * 모든 코인에 대한 분석 수행 (백그라운드)
     */
    private void performAnalysisForAllCoins(List<CoinInfo> coins) {
        // 최대 10개만 처리
        int count = 0;
        for (CoinInfo coin : coins) {
            // 이미 분석된 코인은 건너뛰기
            if (analysisCache.containsKey(coin.getSymbol())) {
                continue;
            }

            // 최대 10개까지만 분석
            if (count++ >= 10) {
                break;
            }

            // 캔들 데이터 로드하여 분석
            loadCandleDataAndAnalyze(coin);
        }
    }

    /**
     * 캔들 데이터 로드하여 분석
     */
    private void loadCandleDataAndAnalyze(CoinInfo coin) {
        if (exchangeType == ExchangeType.UPBIT) {
            UpbitApiService apiService = RetrofitClient.getUpbitApiService();

            // 일봉 캔들 데이터 로드
            apiService.getDayCandles(coin.getMarket(), 30).enqueue(new Callback<List<com.example.cryptoanalysisai.models.CandleData>>() {
                @Override
                public void onResponse(@NonNull Call<List<com.example.cryptoanalysisai.models.CandleData>> call,
                                       @NonNull Response<List<com.example.cryptoanalysisai.models.CandleData>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        List<com.example.cryptoanalysisai.models.CandleData> candles = response.body();

                        // 현재가 가져오기 (ticker)
                        apiService.getTicker(coin.getMarket()).enqueue(new Callback<List<TickerData>>() {
                            @Override
                            public void onResponse(@NonNull Call<List<TickerData>> call, @NonNull Response<List<TickerData>> response) {
                                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                                    TickerData ticker = response.body().get(0);

                                    // 기술적 지표 계산
                                    Map<String, Object> indicators = indicatorService.calculateAllIndicators(candles);

                                    // 분석 요청
                                    analysisService.generateAnalysis(
                                            coin, candles, ticker, exchangeType, indicators,
                                            new AnalysisService.AnalysisCallback() {
                                                @Override
                                                public void onAnalysisSuccess(com.example.cryptoanalysisai.models.AnalysisResult result, String rawResponse) {
                                                    // Firebase에 저장
                                                    firebaseManager.saveAnalysisResult(
                                                            result, coin, exchangeType,
                                                            new FirebaseManager.OnAnalysisSavedListener() {
                                                                @Override
                                                                public void onSuccess(String documentId) {
                                                                    Log.d(TAG, "분석 결과 저장 성공: " + coin.getSymbol());

                                                                    // 분석 결과 캐시에 추가 및 UI 갱신
                                                                    updateAnalysisCache(coin.getSymbol(), result);
                                                                }

                                                                @Override
                                                                public void onFailure(String errorMessage) {
                                                                    Log.e(TAG, "분석 결과 저장 실패: " + errorMessage);
                                                                }
                                                            });
                                                }

                                                @Override
                                                public void onAnalysisFailure(String error) {
                                                    Log.e(TAG, "분석 실패: " + error);
                                                }
                                            });
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<List<TickerData>> call, @NonNull Throwable t) {
                                Log.e(TAG, "현재가 로드 실패: " + t.getMessage());
                            }
                        });
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<com.example.cryptoanalysisai.models.CandleData>> call, @NonNull Throwable t) {
                    Log.e(TAG, "캔들 데이터 로드 실패: " + t.getMessage());
                }
            });
        }
        // 바이낸스도 비슷하게 구현 가능 (지금은 생략)
    }

    /**
     * 분석 결과 캐시 업데이트 및 UI 갱신
     */
    private void updateAnalysisCache(String symbol, com.example.cryptoanalysisai.models.AnalysisResult result) {
        if (getActivity() == null) return;

        // 결과를 Firebase 모델로 변환
        FirestoreAnalysisResult firestoreResult = FirestoreAnalysisResult.fromAnalysisResult(
                result, symbol, getKoreanName(symbol),
                exchangeType == ExchangeType.UPBIT ? "KRW-" + symbol : symbol + "USDT",
                exchangeType.getCode());

        // 캐시에 추가
        analysisCache.put(symbol, firestoreResult);

        // UI 갱신
        getActivity().runOnUiThread(() -> {
            if (adapter != null) {
                adapter.setAnalysisResults(analysisCache);
                adapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * 바이낸스 24시간 가격 변화 정보 가져오기
     */
    private void load24hTickerForCoin(CoinInfo coinInfo) {
        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.get24hTicker(coinInfo.getMarket()).enqueue(new Callback<BinanceTicker>() {
            @Override
            public void onResponse(@NonNull Call<BinanceTicker> call, @NonNull Response<BinanceTicker> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BinanceTicker ticker = response.body();
                    coinInfo.setPriceChange(ticker.getPriceChangePercent() / 100.0);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                Log.e(TAG, "24시간 변화 정보 로딩 실패: " + t.getMessage());
            }
        });
    }

    /**
     * 코인 목록 갱신
     */
    private void updateCoinList(List<CoinInfo> coins) {
        if (getActivity() == null || binding == null) return;

        getActivity().runOnUiThread(() -> {
            if (adapter != null) {
                adapter.updateData(coins);
            }

            binding.tvEmpty.setVisibility(coins.isEmpty() ? View.VISIBLE : View.GONE);
            showLoading(false);
        });
    }

    /**
     * 로딩 표시
     */
    private void showLoading(boolean isLoading) {
        if (binding != null) {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.swipeRefreshLayout.setRefreshing(isLoading);
        }
    }

    /**
     * 오류 메시지 표시
     */
    private void showError(String message) {
        if (getContext() != null) {
            Log.e(TAG, message);
            binding.tvEmpty.setText(message);
            binding.tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 데이터 새로고침
     */
    public void refreshData() {
        if (binding != null) {
            binding.etSearch.setText("");
            showOnlyTopCoins = true;
            loadTopCoins();
        }
    }

    /**
     * 바이낸스 코인 한글명 가져오기
     */
    private String getKoreanName(String symbol) {
        // 주요 코인에 대한 한글 이름 매핑
        Map<String, String> koreanNames = new HashMap<>();
        koreanNames.put("BTC", "비트코인");
        koreanNames.put("ETH", "이더리움");
        koreanNames.put("XRP", "리플");
        koreanNames.put("ADA", "에이다");
        koreanNames.put("DOGE", "도지코인");
        koreanNames.put("SOL", "솔라나");
        koreanNames.put("DOT", "폴카닷");
        koreanNames.put("AVAX", "아발란체");
        koreanNames.put("MATIC", "폴리곤");
        koreanNames.put("LINK", "체인링크");
        koreanNames.put("UNI", "유니스왑");
        koreanNames.put("ATOM", "코스모스");
        koreanNames.put("AAVE", "에이브");
        koreanNames.put("ALGO", "알고랜드");
        koreanNames.put("XLM", "스텔라루멘");
        koreanNames.put("ETC", "이더리움클래식");
        koreanNames.put("NEAR", "니어프로토콜");
        koreanNames.put("SHIB", "시바이누");
        koreanNames.put("SAND", "샌드박스");
        koreanNames.put("MANA", "디센트럴랜드");

        return koreanNames.getOrDefault(symbol, symbol);
    }

    /**
     * 코인 선택 리스너 인터페이스
     */
    public interface OnCoinSelectedListener {
        void onCoinSelected(CoinInfo coinInfo, ExchangeType exchangeType);
    }

    /**
     * 코인 목록 어댑터
     */
    private static class CoinListAdapter extends RecyclerView.Adapter<CoinListAdapter.ViewHolder> implements Filterable {

        private final List<CoinInfo> originalList;
        private List<CoinInfo> filteredList;
        private final OnCoinClickListener listener;
        private Map<String, FirestoreAnalysisResult> analysisResults = new HashMap<>();

        public CoinListAdapter(List<CoinInfo> coins, OnCoinClickListener listener) {
            this.originalList = new ArrayList<>(coins);
            this.filteredList = new ArrayList<>(coins);
            this.listener = listener;
        }

        public void setAnalysisResults(Map<String, FirestoreAnalysisResult> analysisResults) {
            this.analysisResults = analysisResults;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_coin, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CoinInfo coin = filteredList.get(position);

            // 코인 심볼 (BTC, ETH, ...)
            holder.tvCoinSymbol.setText(coin.getSymbol());

            // 코인 이름 (비트코인, 이더리움, ...)
            holder.tvCoinName.setText(coin.getDisplayName());

            // 현재 가격
            holder.tvPrice.setText(coin.getFormattedPrice());

            // 가격 변화율
            holder.tvPriceChange.setText(coin.getFormattedPriceChange());
            holder.tvPriceChange.setTextColor(coin.getPriceChange() >= 0 ?
                    android.graphics.Color.rgb(76, 175, 80) : // 상승: 초록색
                    android.graphics.Color.rgb(244, 67, 54)); // 하락: 빨간색

            // 분석 결과 표시 (있는 경우)
            FirestoreAnalysisResult analysis = analysisResults.get(coin.getSymbol());
            if (analysis != null) {
                holder.tvAnalysisRecommendation.setVisibility(View.VISIBLE);

                // 추천 표시
                String recommendation = analysis.getRecommendation();
                holder.tvAnalysisRecommendation.setText(recommendation);

                // 색상 설정
                if ("매수".equals(recommendation)) {
                    holder.tvAnalysisRecommendation.setTextColor(android.graphics.Color.rgb(76, 175, 80)); // 초록색
                    holder.cardView.setStrokeColor(android.graphics.Color.rgb(76, 175, 80));
                    holder.cardView.setStrokeWidth(2);
                } else if ("매도".equals(recommendation)) {
                    holder.tvAnalysisRecommendation.setTextColor(android.graphics.Color.rgb(244, 67, 54)); // 빨간색
                    holder.cardView.setStrokeColor(android.graphics.Color.rgb(244, 67, 54));
                    holder.cardView.setStrokeWidth(2);
                } else {
                    holder.tvAnalysisRecommendation.setTextColor(android.graphics.Color.rgb(255, 152, 0)); // 주황색
                    holder.cardView.setStrokeColor(android.graphics.Color.rgb(255, 152, 0));
                    holder.cardView.setStrokeWidth(2);
                }
            } else {
                holder.tvAnalysisRecommendation.setVisibility(View.GONE);
                holder.cardView.setStrokeWidth(0);
            }

            // 클릭 리스너
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCoinClick(coin);
                }
            });
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        /**
         * 데이터 갱신
         */
        public void updateData(List<CoinInfo> newCoins) {
            this.originalList.clear();
            this.originalList.addAll(newCoins);

            // 현재 필터 적용
            getFilter().filter(null);
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    List<CoinInfo> filtered = new ArrayList<>();

                    if (constraint == null || constraint.length() == 0) {
                        filtered.addAll(originalList);
                    } else {
                        String filterPattern = constraint.toString().toLowerCase().trim();

                        for (CoinInfo coin : originalList) {
                            if (coin.getSymbol().toLowerCase().contains(filterPattern) ||
                                    (coin.getKoreanName() != null && coin.getKoreanName().toLowerCase().contains(filterPattern)) ||
                                    (coin.getEnglishName() != null && coin.getEnglishName().toLowerCase().contains(filterPattern))) {
                                filtered.add(coin);
                            }
                        }
                    }

                    FilterResults results = new FilterResults();
                    results.values = filtered;
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredList = (List<CoinInfo>) results.values;
                    notifyDataSetChanged();
                }
            };
        }

        /**
         * 뷰홀더
         */
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvCoinSymbol;
            TextView tvCoinName;
            TextView tvPrice;
            TextView tvPriceChange;
            TextView tvAnalysisRecommendation;
            MaterialCardView cardView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvCoinSymbol = itemView.findViewById(R.id.tvCoinSymbol);
                tvCoinName = itemView.findViewById(R.id.tvCoinName);
                tvPrice = itemView.findViewById(R.id.tvPrice);
                tvPriceChange = itemView.findViewById(R.id.tvPriceChange);
                tvAnalysisRecommendation = itemView.findViewById(R.id.tvAnalysisRecommendation);
                cardView = (MaterialCardView) itemView;
            }
        }

        /**
         * 코인 클릭 리스너 인터페이스
         */
        interface OnCoinClickListener {
            void onCoinClick(CoinInfo coinInfo);
        }
    }
}