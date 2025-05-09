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
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.models.TickerData;

import java.util.ArrayList;
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

    private FragmentCoinListBinding binding;
    private CoinListAdapter adapter;
    private OnCoinSelectedListener listener;
    private ExchangeType exchangeType = ExchangeType.UPBIT;

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

            // 거래소 변경 시 코인 목록 새로고침
            loadCoinList();
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
            loadUpbitMarkets();
        } else {
            loadBinanceMarkets();
        }
    }

    /**
     * 업비트 마켓 목록 로드
     */
    private void loadUpbitMarkets() {
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

                    // 가격 정보 로드
                    loadUpbitPrices(krwMarkets);
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
    private void loadUpbitPrices(List<CoinInfo> markets) {
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
     * 바이낸스 마켓 목록 로드
     */
    private void loadBinanceMarkets() {
        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.getExchangeInfo().enqueue(new Callback<BinanceModels.BinanceExchangeInfo>() {
            @Override
            public void onResponse(@NonNull Call<BinanceModels.BinanceExchangeInfo> call, @NonNull Response<BinanceModels.BinanceExchangeInfo> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BinanceModels.BinanceExchangeInfo exchangeInfo = response.body();
                    List<CoinInfo> usdtMarkets = new ArrayList<>();

                    // USDT 마켓만 필터링 및 변환
                    for (BinanceModels.BinanceExchangeInfo.SymbolInfo symbolInfo : exchangeInfo.getSymbols()) {
                        if ("USDT".equals(symbolInfo.getQuoteAsset()) && "TRADING".equals(symbolInfo.getStatus())) {
                            CoinInfo coinInfo = symbolInfo.toUpbitFormat();

                            // 한글 이름 추가 (코드 간소화를 위해 임의로 설정)
                            String koreanName = getKoreanName(symbolInfo.getBaseAsset());
                            coinInfo.setKoreanName(koreanName);
                            coinInfo.setEnglishName(symbolInfo.getBaseAsset());

                            usdtMarkets.add(coinInfo);
                        }
                    }

                    // 가격 정보 로드
                    loadBinancePrices(usdtMarkets);
                } else {
                    showError("바이낸스 마켓 정보를 가져오는데 실패했습니다.");
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
     * 바이낸스 가격 정보 로드
     */
    private void loadBinancePrices(List<CoinInfo> markets) {
        if (markets.isEmpty()) {
            showError("마켓 정보가 없습니다.");
            showLoading(false);
            return;
        }

        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.getAllTickers().enqueue(new Callback<List<BinanceModels.BinanceTicker>>() {
            @Override
            public void onResponse(@NonNull Call<List<BinanceModels.BinanceTicker>> call, @NonNull Response<List<BinanceModels.BinanceTicker>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<BinanceModels.BinanceTicker> tickers = response.body();

                    // 코인 정보에 가격 데이터 추가
                    for (CoinInfo market : markets) {
                        for (BinanceModels.BinanceTicker ticker : tickers) {
                            if (market.getMarket().equals(ticker.getSymbol())) {
                                market.setCurrentPrice(ticker.getPrice());

                                // 추가로 24시간 가격 변화 정보 가져오기
                                load24hTickerForCoin(market);
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
                } else {
                    showError("가격 정보를 가져오는데 실패했습니다.");
                    showLoading(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<BinanceModels.BinanceTicker>> call, @NonNull Throwable t) {
                showError("네트워크 오류: " + t.getMessage());
                showLoading(false);
            }
        });
    }

    /**
     * 바이낸스 24시간 가격 변화 정보 가져오기
     */
    private void load24hTickerForCoin(CoinInfo coinInfo) {
        BinanceApiService apiService = RetrofitClient.getBinanceApiService();

        apiService.get24hTicker(coinInfo.getMarket()).enqueue(new Callback<BinanceModels.BinanceTicker>() {
            @Override
            public void onResponse(@NonNull Call<BinanceModels.BinanceTicker> call, @NonNull Response<BinanceModels.BinanceTicker> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BinanceModels.BinanceTicker ticker = response.body();
                    coinInfo.setPriceChange(ticker.getPriceChangePercent() / 100.0);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(@NonNull Call<BinanceModels.BinanceTicker> call, @NonNull Throwable t) {
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
            loadCoinList();
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
