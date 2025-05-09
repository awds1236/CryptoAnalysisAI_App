package com.example.cryptoanalysisai.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

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
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.models.TickerData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CoinListFragment extends Fragment {

    private static final String TAG = "CoinListFragment";
    private static final String ARG_EXCHANGE_TYPE = "arg_exchange_type";
    private static final int REFRESH_INTERVAL_MS = 3000; // 3초마다 갱신

    // 표시할 코인 심볼 목록
    private static final List<String> TARGET_COINS = Arrays.asList("BTC", "ETH", "SOL", "XRP", "DOGE");

    private FragmentCoinListBinding binding;
    private CoinListAdapter adapter;
    private OnCoinSelectedListener listener;
    private ExchangeType exchangeType = ExchangeType.UPBIT;

    // 자동 갱신을 위한 변수들
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private boolean isAutoRefreshEnabled = true;
    private List<CoinInfo> currentCoins = new ArrayList<>();

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

        // 핸들러 초기화
        refreshHandler = new Handler(Looper.getMainLooper());

        // 갱신 작업 정의
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAutoRefreshEnabled) {
                    refreshPrices();
                    // 다음 갱신 예약
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
                }
            }
        };
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

        // 데이터 로드
        loadCoinList();

        // 새로고침 기능 설정
        binding.swipeRefreshLayout.setOnRefreshListener(this::refreshData);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 화면이 보일 때 자동 갱신 시작
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        // 화면이 안 보일 때 자동 갱신 중지
        stopAutoRefresh();
        super.onPause();
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
        stopAutoRefresh();
        super.onDestroyView();
        binding = null;
    }

    /**
     * 자동 갱신 시작
     */
    private void startAutoRefresh() {
        isAutoRefreshEnabled = true;
        refreshHandler.post(refreshRunnable);
    }

    /**
     * 자동 갱신 중지
     */
    private void stopAutoRefresh() {
        isAutoRefreshEnabled = false;
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    /**
     * 가격 정보만 갱신 (전체 코인 목록을 다시 로드하지 않음)
     */
    private void refreshPrices() {
        if (exchangeType == ExchangeType.UPBIT) {
            refreshUpbitPrices();
        } else {
            refreshBinancePrices();
        }
    }

    /**
     * 업비트 가격 정보만 갱신
     */
    private void refreshUpbitPrices() {
        if (currentCoins.isEmpty()) {
            return;
        }

        UpbitApiService apiService = RetrofitClient.getUpbitApiService();

        // 마켓 코드 문자열 생성 (KRW-BTC,KRW-ETH,...)
        StringBuilder marketCodesBuilder = new StringBuilder();
        for (int i = 0; i < currentCoins.size(); i++) {
            if (i > 0) marketCodesBuilder.append(",");
            marketCodesBuilder.append(currentCoins.get(i).getMarket());
        }
        String marketCodes = marketCodesBuilder.toString();

        // 현재가 정보 로드
        apiService.getTicker(marketCodes).enqueue(new Callback<List<TickerData>>() {
            @Override
            public void onResponse(@NonNull Call<List<TickerData>> call, @NonNull Response<List<TickerData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<TickerData> tickers = response.body();

                    // 코인 정보에 가격 데이터 업데이트
                    boolean hasUpdate = false;
                    for (CoinInfo coin : currentCoins) {
                        for (TickerData ticker : tickers) {
                            if (coin.getMarket().equals(ticker.getMarket())) {
                                // 가격이 변경되었는지 확인
                                if (coin.getCurrentPrice() != ticker.getTradePrice() ||
                                        coin.getPriceChange() != ticker.getChangeRate()) {

                                    coin.setCurrentPrice(ticker.getTradePrice());
                                    coin.setPriceChange(ticker.getChangeRate());
                                    hasUpdate = true;
                                }
                                break;
                            }
                        }
                    }

                    // 가격이 변경된 경우에만 UI 업데이트
                    if (hasUpdate && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TickerData>> call, @NonNull Throwable t) {
                Log.e(TAG, "가격 갱신 실패: " + t.getMessage());
            }
        });
    }

    /**
     * 바이낸스 가격 정보만 갱신
     */
    private void refreshBinancePrices() {
        if (currentCoins.isEmpty()) {
            return;
        }

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        // 모든 코인 티커 정보 가져오기
        apiService.getAllTickers().enqueue(new Callback<List<BinanceTicker>>() {
            @Override
            public void onResponse(@NonNull Call<List<BinanceTicker>> call, @NonNull Response<List<BinanceTicker>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<BinanceTicker> tickers = response.body();

                    // 필요한 코인만 필터링하여 가격 업데이트
                    boolean hasUpdate = false;
                    for (CoinInfo coin : currentCoins) {
                        for (BinanceTicker ticker : tickers) {
                            if (coin.getMarket().equals(ticker.getSymbol())) {
                                double newPrice = ticker.getPrice();

                                // 가격이 변경된 경우에만 업데이트
                                if (coin.getCurrentPrice() != newPrice) {
                                    coin.setCurrentPrice(newPrice);
                                    hasUpdate = true;
                                }
                                break;
                            }
                        }
                    }

                    // 변화율 정보도 필요에 따라 갱신 (필요시에만, 성능상 이유로 생략 가능)
                    if (hasUpdate) {
                        refreshBinancePriceChanges();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<BinanceTicker>> call, @NonNull Throwable t) {
                Log.e(TAG, "바이낸스 가격 갱신 실패: " + t.getMessage());
            }
        });
    }

    /**
     * 바이낸스 변화율 정보 갱신 (24시간)
     */
    private void refreshBinancePriceChanges() {
        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        // 각 코인의 24시간 변화율 정보 가져오기
        for (CoinInfo coin : currentCoins) {
            apiService.get24hTicker(coin.getMarket()).enqueue(new Callback<BinanceTicker>() {
                @Override
                public void onResponse(@NonNull Call<BinanceTicker> call, @NonNull Response<BinanceTicker> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BinanceTicker ticker = response.body();

                        // 변화율 업데이트
                        double newChangeRate = ticker.getPriceChangePercent() / 100.0;
                        if (coin.getPriceChange() != newChangeRate) {
                            coin.setPriceChange(newChangeRate);

                            // UI 업데이트
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (adapter != null) {
                                        adapter.notifyDataSetChanged();
                                    }
                                });
                            }
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                    Log.e(TAG, "24시간 변화율 갱신 실패: " + t.getMessage());
                }
            });
        }
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

            // 자동 갱신 중지
            stopAutoRefresh();

            // 거래소 변경 시 코인 목록 새로고침
            loadCoinList();

            // 자동 갱신 재시작
            startAutoRefresh();
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
     * 코인 목록 로드
     */
    private void loadCoinList() {
        showLoading(true);

        if (exchangeType == ExchangeType.UPBIT) {
            loadUpbitCoins();
        } else {
            loadBinanceCoins();
        }
    }

    /**
     * 업비트 특정 코인 정보 로드
     */
    private void loadUpbitCoins() {
        UpbitApiService apiService = RetrofitClient.getUpbitApiService();

        // 먼저 기본 코인 정보 생성 (심볼과 시장 코드 매핑)
        List<CoinInfo> targetCoins = new ArrayList<>();
        for (String symbol : TARGET_COINS) {
            CoinInfo coinInfo = new CoinInfo();
            coinInfo.setMarket("KRW-" + symbol);
            coinInfo.setSymbol(symbol);
            coinInfo.setKoreanName(getKoreanName(symbol));
            coinInfo.setEnglishName(symbol);
            targetCoins.add(coinInfo);
        }

        // 마켓 코드 문자열 생성 (KRW-BTC,KRW-ETH,...)
        StringBuilder marketCodesBuilder = new StringBuilder();
        for (int i = 0; i < targetCoins.size(); i++) {
            if (i > 0) marketCodesBuilder.append(",");
            marketCodesBuilder.append(targetCoins.get(i).getMarket());
        }
        String marketCodes = marketCodesBuilder.toString();

        // 현재가 정보 로드
        apiService.getTicker(marketCodes).enqueue(new Callback<List<TickerData>>() {
            @Override
            public void onResponse(@NonNull Call<List<TickerData>> call, @NonNull Response<List<TickerData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<TickerData> tickers = response.body();

                    // 코인 정보에 가격 데이터 추가
                    for (CoinInfo coin : targetCoins) {
                        for (TickerData ticker : tickers) {
                            if (coin.getMarket().equals(ticker.getMarket())) {
                                coin.setCurrentPrice(ticker.getTradePrice());
                                coin.setPriceChange(ticker.getChangeRate());
                                break;
                            }
                        }
                    }

                    // 코인 한글명 정보 가져오기 - 실제 업비트 API에서 가져오기
                    apiService.getMarkets(true).enqueue(new Callback<List<CoinInfo>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<CoinInfo>> call, @NonNull Response<List<CoinInfo>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                List<CoinInfo> allMarkets = response.body();

                                // 한글명 업데이트
                                for (CoinInfo targetCoin : targetCoins) {
                                    for (CoinInfo market : allMarkets) {
                                        if (targetCoin.getMarket().equals(market.getMarket())) {
                                            targetCoin.setKoreanName(market.getKoreanName());
                                            targetCoin.setEnglishName(market.getEnglishName());
                                            break;
                                        }
                                    }
                                }
                            }

                            // 전역 변수에 저장
                            currentCoins = targetCoins;

                            // 최종 목록 업데이트
                            updateCoinList(targetCoins);
                        }

                        @Override
                        public void onFailure(@NonNull Call<List<CoinInfo>> call, @NonNull Throwable t) {
                            // 실패해도 기본 정보로 UI 업데이트
                            currentCoins = targetCoins;
                            updateCoinList(targetCoins);
                        }
                    });
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
     * 바이낸스 특정 코인 정보 로드
     */
    private void loadBinanceCoins() {
        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        // 먼저 기본 코인 정보 생성 (심볼과 시장 코드 매핑)
        List<CoinInfo> targetCoins = new ArrayList<>();
        for (String symbol : TARGET_COINS) {
            CoinInfo coinInfo = new CoinInfo();
            coinInfo.setMarket(symbol + "USDT");  // 바이낸스 형식 (BTCUSDT)
            coinInfo.setSymbol(symbol);
            coinInfo.setKoreanName(getKoreanName(symbol));
            coinInfo.setEnglishName(symbol);
            coinInfo.setBaseAsset(symbol);
            coinInfo.setQuoteAsset("USDT");
            targetCoins.add(coinInfo);
        }

        // 각 코인에 대한 가격 정보 로드
        final AtomicInteger completedCalls = new AtomicInteger(0);
        for (CoinInfo coin : targetCoins) {
            // 현재가 정보 로드
            apiService.getTicker(coin.getMarket()).enqueue(new Callback<BinanceTicker>() {
                @Override
                public void onResponse(@NonNull Call<BinanceTicker> call, @NonNull Response<BinanceTicker> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BinanceTicker ticker = response.body();
                        coin.setCurrentPrice(ticker.getPrice());

                        // 24시간 변화율 정보 로드
                        apiService.get24hTicker(coin.getMarket()).enqueue(new Callback<BinanceTicker>() {
                            @Override
                            public void onResponse(@NonNull Call<BinanceTicker> call, @NonNull Response<BinanceTicker> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    BinanceTicker ticker24h = response.body();
                                    coin.setPriceChange(ticker24h.getPriceChangePercent() / 100.0);
                                }

                                // 모든 API 호출이 완료되면 UI 업데이트
                                if (completedCalls.incrementAndGet() == targetCoins.size()) {
                                    currentCoins = targetCoins;
                                    updateCoinList(targetCoins);
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                                Log.e(TAG, "24시간 변화율 로딩 실패: " + t.getMessage());
                                // 실패해도 카운트 증가
                                if (completedCalls.incrementAndGet() == targetCoins.size()) {
                                    currentCoins = targetCoins;
                                    updateCoinList(targetCoins);
                                }
                            }
                        });
                    } else {
                        // 실패해도 카운트 증가
                        if (completedCalls.incrementAndGet() == targetCoins.size()) {
                            currentCoins = targetCoins;
                            updateCoinList(targetCoins);
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call<BinanceTicker> call, @NonNull Throwable t) {
                    Log.e(TAG, "현재가 로딩 실패: " + t.getMessage());
                    // 실패해도 카운트 증가
                    if (completedCalls.incrementAndGet() == targetCoins.size()) {
                        currentCoins = targetCoins;
                        updateCoinList(targetCoins);
                    }
                }
            });
        }
    }

    /**
     * 코인 목록 UI 업데이트
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
            stopAutoRefresh();
            loadCoinList();
            startAutoRefresh();
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

        public CoinListAdapter(List<CoinInfo> coins, OnCoinClickListener listener) {
            this.originalList = new ArrayList<>(coins);
            this.filteredList = new ArrayList<>(coins);
            this.listener = listener;
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
            android.widget.TextView tvCoinSymbol;
            android.widget.TextView tvCoinName;
            android.widget.TextView tvPrice;
            android.widget.TextView tvPriceChange;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvCoinSymbol = itemView.findViewById(R.id.tvCoinSymbol);
                tvCoinName = itemView.findViewById(R.id.tvCoinName);
                tvPrice = itemView.findViewById(R.id.tvPrice);
                tvPriceChange = itemView.findViewById(R.id.tvPriceChange);
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