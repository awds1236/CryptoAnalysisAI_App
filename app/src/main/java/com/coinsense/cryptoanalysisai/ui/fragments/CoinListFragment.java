package com.coinsense.cryptoanalysisai.ui.fragments;

import android.content.Context;
import android.content.SharedPreferences;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.api.BinanceApiService;
import com.coinsense.cryptoanalysisai.api.RetrofitClient;
import com.coinsense.cryptoanalysisai.databinding.FragmentCoinListBinding;
import com.coinsense.cryptoanalysisai.models.AnalysisResult;
import com.coinsense.cryptoanalysisai.models.BinanceModels;
import com.coinsense.cryptoanalysisai.models.BinanceTicker;
import com.coinsense.cryptoanalysisai.models.CoinInfo;
import com.coinsense.cryptoanalysisai.models.ExchangeType;
import com.coinsense.cryptoanalysisai.services.AnalysisApiService;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CoinListFragment extends Fragment {

    private static final String TAG = "CoinListFragment";
    private static final String ARG_EXCHANGE_TYPE = "arg_exchange_type";

    private FragmentCoinListBinding binding;
    private CoinListAdapter adapter;
    private OnCoinSelectedListener listener;
    private ExchangeType exchangeType = ExchangeType.BINANCE;
    private AnalysisApiService analysisApiService;

    // 코인 캐시 (빠른 액세스를 위한)
    private Map<String, CoinInfo> coinCache = new HashMap<>();
    // 분석 결과 캐시
    private Map<String, AnalysisResult> analysisCache = new HashMap<>();

    // ★ 새로 추가: 분석 결과가 있는 코인들의 심볼 목록
    private Set<String> availableAnalysisCoins = new HashSet<>();
    private boolean analysisDataLoaded = false; // 분석 데이터 로드 완료 여부

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

            // 항상 바이낸스로 고정
            exchangeType = ExchangeType.BINANCE;
        }

        analysisApiService = AnalysisApiService.getInstance(requireContext());
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

        // 거래소 라디오 버튼 비활성화 (바이낸스만 사용)
        binding.exchangeGroup.setVisibility(View.GONE);

        // 검색 기능 초기화
        initSearchBar();

        // 리사이클러뷰 초기화
        initRecyclerView();

        // ★ 데이터 로드 순서 변경: 분석 결과 먼저 로드
        loadCoinList();

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
        }, analysisCache);

        binding.recyclerCoins.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerCoins.setAdapter(adapter);
    }

    /**
     * ★ 수정된 코인 목록 로드 - 분석 결과 우선 로드
     */
    private void loadCoinList() {
        showLoading(true);

        // 분석 결과가 있는 코인들의 목록을 먼저 로드
        loadAllAnalyses();
    }

    /**
     * ★ 수정된 모든 코인의 분석 결과 로드 - 완료 후 바이낸스 마켓 로드
     */
    private void loadAllAnalyses() {
        Log.d(TAG, "분석 결과 로드 시작");

        analysisApiService.getAllLatestAnalyses(new AnalysisApiService.OnAllAnalysesRetrievedListener() {
            @Override
            public void onAllAnalysesRetrieved(List<AnalysisResult> resultList) {
                Log.d(TAG, "분석 결과 로드 완료: " + resultList.size() + "개");

                // 분석 결과 캐시에 저장
                analysisCache.clear();
                availableAnalysisCoins.clear();

                for (AnalysisResult result : resultList) {
                    String coinSymbol = result.getCoinSymbol();
                    if (coinSymbol != null && !coinSymbol.isEmpty()) {
                        analysisCache.put(coinSymbol, result);
                        availableAnalysisCoins.add(coinSymbol);
                        Log.d(TAG, "분석 데이터 추가: " + coinSymbol);
                    }
                }

                analysisDataLoaded = true;
                Log.d(TAG, "분석 가능한 코인 수: " + availableAnalysisCoins.size());

                // 분석 결과 로드가 완료되면 바이낸스 마켓 로드
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        loadBinanceMarkets();
                    });
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "분석 결과 로드 실패: " + errorMessage);

                // 분석 결과 로드가 실패해도 빈 목록으로 계속 진행
                analysisDataLoaded = true;
                availableAnalysisCoins.clear();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        showError("분석 데이터를 불러올 수 없습니다: " + errorMessage);
                        showLoading(false);
                    });
                }
            }
        });
    }

    /**
     * ★ 수정된 바이낸스 마켓 목록 로드 - 분석 결과가 있는 코인만 필터링
     */
    private void loadBinanceMarkets() {
        if (!analysisDataLoaded) {
            Log.w(TAG, "분석 데이터가 아직 로드되지 않았습니다");
            return;
        }

        Log.d(TAG, "바이낸스 마켓 로드 시작 - 분석 가능한 코인만 필터링");

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.getExchangeInfo().enqueue(new Callback<BinanceModels.BinanceExchangeInfo>() {
            @Override
            public void onResponse(@NonNull Call<BinanceModels.BinanceExchangeInfo> call, @NonNull Response<BinanceModels.BinanceExchangeInfo> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BinanceModels.BinanceExchangeInfo exchangeInfo = response.body();
                    List<CoinInfo> filteredMarkets = new ArrayList<>();

                    Log.d(TAG, "바이낸스 거래소 정보 수신 완료");

                    // ★ 분석 결과가 있는 코인만 필터링
                    for (BinanceModels.BinanceExchangeInfo.SymbolInfo symbolInfo : exchangeInfo.getSymbols()) {
                        if ("USDT".equals(symbolInfo.getQuoteAsset()) && "TRADING".equals(symbolInfo.getStatus())) {
                            String baseAsset = symbolInfo.getBaseAsset();

                            // ★ 핵심 변경: 분석 결과가 있는 코인인지 확인
                            if (availableAnalysisCoins.contains(baseAsset)) {
                                Log.d(TAG, "분석 데이터 있는 코인 추가: " + baseAsset);

                                CoinInfo coinInfo = symbolInfo.toUpbitFormat();
                                String localizedName = getKoreanName(baseAsset);
                                coinInfo.setKoreanName(localizedName);
                                coinInfo.setEnglishName(localizedName);

                                // 프리미엄 코인 여부 확인 (기존 로직 유지)
                                boolean isPremiumCoin = false;
                                for (String premiumCoin : Constants.PREMIUM_COINS) {
                                    if (premiumCoin.equalsIgnoreCase(baseAsset)) {
                                        isPremiumCoin = true;
                                        break;
                                    }
                                }
                                coinInfo.setPremium(isPremiumCoin);

                                filteredMarkets.add(coinInfo);
                                coinCache.put(coinInfo.getSymbol(), coinInfo);
                            }
                        }
                    }

                    Log.d(TAG, "필터링된 코인 수: " + filteredMarkets.size());

                    if (filteredMarkets.isEmpty()) {
                        showError("분석 가능한 코인이 없습니다");
                        showLoading(false);
                        return;
                    }

                    // 가격 정보 로드
                    loadBinancePrices(filteredMarkets);
                } else {
                    showError(getString(R.string.binance_market_error));
                    showLoading(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<BinanceModels.BinanceExchangeInfo> call, @NonNull Throwable t) {
                showError(getString(R.string.network_error, t.getMessage()));
                showLoading(false);
            }
        });
    }

    /**
     * 가격 정보만 새로고침 (MainActivity에서 호출됨)
     */
    public void refreshPrices() {
        if (!isAdded() || adapter == null) return;

        // 어댑터가 가지고 있는 현재 코인 목록 가져오기
        List<CoinInfo> currentCoins = adapter.getOriginalList();

        if (currentCoins.isEmpty()) return;

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.getAllTickers().enqueue(new Callback<List<BinanceTicker>>() {
            @Override
            public void onResponse(@NonNull Call<List<BinanceTicker>> call, @NonNull Response<List<BinanceTicker>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful() && response.body() != null) {
                    List<BinanceTicker> tickers = response.body();
                    boolean dataChanged = false;

                    // 코인 정보에 가격 데이터 추가
                    for (CoinInfo coin : currentCoins) {
                        for (BinanceTicker ticker : tickers) {
                            if (coin.getMarket().equals(ticker.getSymbol())) {
                                double newPrice = ticker.getPrice();
                                // 가격이 변경된 경우에만 처리
                                if (newPrice != coin.getCurrentPrice()) {
                                    coin.setCurrentPrice(newPrice);
                                    dataChanged = true;
                                }
                                break;
                            }
                        }
                    }

                    // 변경사항이 있는 경우만 UI 갱신
                    if (dataChanged && adapter != null && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            adapter.notifyDataSetChanged();
                        });
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<BinanceTicker>> call, @NonNull Throwable t) {
                // 실패해도 조용히 넘어감 (silent fail) - 다음 갱신 시도에서 다시 시도
                Log.w(TAG, getString(R.string.price_update_failed, t.getMessage()));
            }
        });
    }

    /**
     * 바이낸스 가격 정보 로드
     */
    private void loadBinancePrices(List<CoinInfo> markets) {
        if (markets.isEmpty()) {
            showError(getString(R.string.no_market_info));
            showLoading(false);
            return;
        }

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.getAllTickers().enqueue(new Callback<List<BinanceTicker>>() {
            @Override
            public void onResponse(@NonNull Call<List<BinanceTicker>> call, @NonNull Response<List<BinanceTicker>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<BinanceTicker> tickers = response.body();

                    // 코인 정보에 가격 데이터 추가
                    for (CoinInfo market : markets) {
                        for (BinanceTicker ticker : tickers) {
                            if (market.getMarket().equals(ticker.getSymbol())) {
                                market.setCurrentPrice(ticker.getPrice());

                                // 추가로 24시간 가격 변화 정보 가져오기
                                load24hTickerForCoin(market);
                                break;
                            }
                        }
                    }

                    // ★ 코인 목록 정렬: 매수 추천 코인 먼저, 그 다음 기본 코인, 마지막으로 프리미엄 코인
                    Collections.sort(markets, new Comparator<CoinInfo>() {
                        @Override
                        public int compare(CoinInfo o1, CoinInfo o2) {
                            // 분석 결과에서 추천 정보 가져오기
                            boolean o1Buy = isBuyRecommended(o1.getSymbol());
                            boolean o2Buy = isBuyRecommended(o2.getSymbol());

                            // 매수 추천 상태에 따라 정렬
                            if (o1Buy && !o2Buy) {
                                return -1; // o1(매수 추천)이 앞으로
                            } else if (!o1Buy && o2Buy) {
                                return 1;  // o2(매수 추천)이 앞으로
                            } else {
                                // 매수 추천이 같은 경우, 기본/프리미엄 구분
                                if (!o1.isPremium() && o2.isPremium()) {
                                    return -1; // o1(기본 코인)이 앞으로
                                } else if (o1.isPremium() && !o2.isPremium()) {
                                    return 1;  // o2(기본 코인)이 앞으로
                                } else {
                                    // 같은 카테고리 내에서는 가격 높은 순으로 정렬
                                    return Double.compare(o2.getCurrentPrice(), o1.getCurrentPrice());
                                }
                            }
                        }
                    });

                    // 리스트 갱신
                    updateCoinList(markets);
                } else {
                    showError(getString(R.string.price_info_error));
                    showLoading(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<BinanceTicker>> call, @NonNull Throwable t) {
                showError(getString(R.string.network_error, t.getMessage()));
                showLoading(false);
            }
        });
    }

    /**
     * 특정 코인이 매수 추천인지 확인
     */
    private boolean isBuyRecommended(String symbol) {
        if (symbol == null || analysisCache == null) return false;

        AnalysisResult result = analysisCache.get(symbol);
        if (result != null && result.getRecommendation() != null) {
            String recommendation = result.getRecommendation().getRecommendation();
            return recommendation != null &&
                    (recommendation.equalsIgnoreCase("매수") ||
                            recommendation.equalsIgnoreCase("Buy"));
        }
        return false;
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

                    if (adapter != null && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            adapter.notifyDataSetChanged();
                        });
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                Log.e(TAG, getString(R.string.price_change_load_failed, t.getMessage()));
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
                // 분석 캐시도 어댑터에 전달
                adapter.updateAnalysisCache(analysisCache);
            }

            binding.tvEmpty.setVisibility(coins.isEmpty() ? View.VISIBLE : View.GONE);

            if (coins.isEmpty()) {
                binding.tvEmpty.setText("분석 가능한 코인이 없습니다");
            }

            showLoading(false);

            Log.d(TAG, "코인 목록 갱신 완료: " + coins.size() + "개 (분석 가능한 코인만)");
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
        if (getContext() != null && binding != null) {
            Log.e(TAG, message);
            binding.tvEmpty.setText(message);
            binding.tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 데이터 새로고침 (수동)
     */
    public void refreshData() {
        if (binding != null) {
            binding.etSearch.setText("");
            // ★ 새로고침 시에도 분석 결과부터 로드
            analysisDataLoaded = false;
            availableAnalysisCoins.clear();
            analysisCache.clear();
            loadCoinList();
        }
    }

    /**
     * 코인의 이름을 가져오기
     */
    private String getKoreanName(String symbol) {
        // 리소스 ID가 통일되었으므로 이제 언어 설정을 확인할 필요 없이
        // 바로 해당 리소스 ID를 사용하면 Android 시스템이 현재 언어에 맞는 문자열을 반환합니다
        switch (symbol) {
            case "BTC": return getString(R.string.bitcoin);
            case "ETH": return getString(R.string.ethereum);
            case "XRP": return getString(R.string.ripple);
            case "SOL": return getString(R.string.solana);
            case "DOGE": return getString(R.string.dogecoin);
            case "ADA": return getString(R.string.cardano);
            case "TRX": return getString(R.string.tron);
            case "SUI": return getString(R.string.sui);
            case "LINK": return getString(R.string.chainlink);
            case "AVAX": return getString(R.string.avalanche);
            case "XLM": return getString(R.string.stellar);
            case "HBAR": return getString(R.string.hedera);
            default: return symbol;
        }
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
        private Map<String, AnalysisResult> analysisCache;

        // 생성자 수정
        public CoinListAdapter(List<CoinInfo> coins, OnCoinClickListener listener, Map<String, AnalysisResult> analysisCache) {
            this.originalList = new ArrayList<>(coins);
            this.filteredList = new ArrayList<>(coins);
            this.listener = listener;
            this.analysisCache = analysisCache;
        }

        // 분석 캐시 업데이트 메서드 추가
        public void updateAnalysisCache(Map<String, AnalysisResult> newCache) {
            this.analysisCache = newCache;
            notifyDataSetChanged();
        }

        // 추천 확인 메서드
        private boolean isBuyRecommended(String symbol) {
            if (symbol == null || analysisCache == null) return false;

            AnalysisResult result = analysisCache.get(symbol);
            if (result != null && result.getRecommendation() != null) {
                String recommendation = result.getRecommendation().getRecommendation();
                return recommendation != null &&
                        (recommendation.equalsIgnoreCase("매수") ||
                                recommendation.equalsIgnoreCase("Buy"));
            }
            return false;
        }

        public List<CoinInfo> getOriginalList() {
            return originalList;
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
            Context context = holder.itemView.getContext();

            // 코인 심볼 (BTC, ETH, ...)
            holder.tvCoinSymbol.setText(coin.getSymbol());

            // 코인 이름 (비트코인/Bitcoin, 이더리움/Ethereum, ...)
            String displayName = coin.getKoreanName();

            // 프리미엄 코인 표시
            if (coin.isPremium()) {
                displayName += context.getString(R.string.premium_coin_indicator);
            }

            holder.tvCoinName.setText(displayName);

            // 현재 가격
            holder.tvPrice.setText(coin.getFormattedPrice());

            // 가격 변화율
            holder.tvPriceChange.setText(coin.getFormattedPriceChange());
            holder.tvPriceChange.setTextColor(coin.getPriceChange() >= 0 ?
                    android.graphics.Color.rgb(76, 175, 80) : // 상승: 초록색
                    android.graphics.Color.rgb(244, 67, 54)); // 하락: 빨간색

            // 분석 결과의 추천 상태 표시
            boolean isBuyRecommended = isBuyRecommended(coin.getSymbol());
            if (isBuyRecommended) {
                // 매수 추천 표시
                holder.tvAnalysisRecommendation.setVisibility(View.VISIBLE);
                holder.tvAnalysisRecommendation.setText(context.getString(R.string.buy_recommendation1));
                holder.tvAnalysisRecommendation.setTextColor(android.graphics.Color.rgb(76, 175, 80)); // 녹색

                // 카드 테두리만 얇게 적용 (1dp)
                holder.cardView.setStrokeWidth(4); // 얇은 테두리
                holder.cardView.setStrokeColor(android.graphics.Color.rgb(76, 175, 80)); // 녹색 테두리
                // 배경색은 변경하지 않음 - 기본 흰색 유지
            } else {
                // 비 매수 추천 코인은 강조 없음
                holder.tvAnalysisRecommendation.setVisibility(View.GONE);
                holder.cardView.setStrokeWidth(0); // 테두리 없음
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
        class ViewHolder extends RecyclerView.ViewHolder {
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