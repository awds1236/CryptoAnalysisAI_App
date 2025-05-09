package com.example.cryptoanalysisai.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.ExistingPeriodicWorkPolicy;

import com.example.cryptoanalysisai.api.BinanceApiService;
import com.example.cryptoanalysisai.api.RetrofitClient;
import com.example.cryptoanalysisai.api.UpbitApiService;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.CandleData;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.models.TickerData;
import com.example.cryptoanalysisai.services.AnalysisService;
import com.example.cryptoanalysisai.services.FirebaseManager;
import com.example.cryptoanalysisai.services.TechnicalIndicatorService;
import com.example.cryptoanalysisai.utils.Constants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AnalysisWorker extends Worker {
    private static final String TAG = "AnalysisWorker";

    // 분석할 주요 코인 목록
    private static final String[] MAIN_COINS = {
            "BTC", "ETH", "XRP", "SOL"
    };

    private final AnalysisService analysisService;
    private final FirebaseManager firebaseManager;

    public AnalysisWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        this.analysisService = new AnalysisService();
        this.firebaseManager = FirebaseManager.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "시간별 코인 분석 작업 시작");

        // 업비트와 바이낸스에서 코인 분석 수행
        for (String coinSymbol : MAIN_COINS) {
            analyzeForExchange(coinSymbol, ExchangeType.UPBIT);
            analyzeForExchange(coinSymbol, ExchangeType.BINANCE);
        }

        return Result.success();
    }

    /**
     * 특정 거래소에서 코인 분석 수행
     */
    private void analyzeForExchange(String coinSymbol, ExchangeType exchangeType) {
        String market = formatMarketCode(coinSymbol, exchangeType);
        if (market == null) return;

        Log.d(TAG, "코인 분석: " + coinSymbol + " on " + exchangeType.getDisplayName());

        // 코인 정보 로드
        loadCoinInfo(market, coinSymbol, exchangeType);
    }

    /**
     * 거래소 별 마켓 코드 포맷팅
     */
    private String formatMarketCode(String coinSymbol, ExchangeType exchangeType) {
        if (exchangeType == ExchangeType.UPBIT) {
            return "KRW-" + coinSymbol;
        } else if (exchangeType == ExchangeType.BINANCE) {
            return coinSymbol + "USDT";
        }
        return null;
    }

    /**
     * 코인 정보 로드
     */
    private void loadCoinInfo(String market, String coinSymbol, ExchangeType exchangeType) {
        AtomicBoolean processed = new AtomicBoolean(false);

        if (exchangeType == ExchangeType.UPBIT) {
            UpbitApiService apiService = RetrofitClient.getUpbitApiService();

            apiService.getMarkets(true).enqueue(new Callback<List<CoinInfo>>() {
                @Override
                public void onResponse(@NonNull Call<List<CoinInfo>> call, @NonNull Response<List<CoinInfo>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        // 해당 마켓의 코인 정보 찾기
                        CoinInfo targetCoin = null;
                        for (CoinInfo coin : response.body()) {
                            if (market.equals(coin.getMarket())) {
                                targetCoin = coin;
                                break;
                            }
                        }

                        if (targetCoin != null && !processed.get()) {
                            processed.set(true);
                            // 캔들 데이터 로드
                            loadCandleData(market, targetCoin, exchangeType);
                        }
                    } else {
                        Log.e(TAG, "업비트 마켓 정보 로드 실패: " + response.code());
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<CoinInfo>> call, @NonNull Throwable t) {
                    Log.e(TAG, "업비트 API 호출 실패: " + t.getMessage());
                }
            });
        } else if (exchangeType == ExchangeType.BINANCE) {
            BinanceApiService apiService = RetrofitClient.getBinanceApiService();

            // 바이낸스는 코인 정보를 가져오는 별도의 API가 없으므로 기본 정보로 생성
            CoinInfo coinInfo = new CoinInfo();
            coinInfo.setMarket(market);
            coinInfo.setSymbol(coinSymbol);
            coinInfo.setEnglishName(coinSymbol);

            if (!processed.get()) {
                processed.set(true);
                // 캔들 데이터 로드
                loadCandleData(market, coinInfo, exchangeType);
            }
        }
    }

    /**
     * 캔들 데이터 로드
     */
    private void loadCandleData(String market, CoinInfo coinInfo, ExchangeType exchangeType) {
        List<CandleData> candleDataList = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        if (exchangeType == ExchangeType.UPBIT) {
            UpbitApiService apiService = RetrofitClient.getUpbitApiService();

            apiService.getDayCandles(market, 30).enqueue(new Callback<List<CandleData>>() {
                @Override
                public void onResponse(@NonNull Call<List<CandleData>> call, @NonNull Response<List<CandleData>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        candleDataList.addAll(response.body());
                        latch.countDown();
                    } else {
                        Log.e(TAG, "업비트 캔들 데이터 로드 실패: " + response.code());
                        latch.countDown();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<CandleData>> call, @NonNull Throwable t) {
                    Log.e(TAG, "업비트 캔들 API 호출 실패: " + t.getMessage());
                    latch.countDown();
                }
            });
        } else if (exchangeType == ExchangeType.BINANCE) {
            BinanceApiService apiService = RetrofitClient.getBinanceApiService();

            apiService.getKlines(market, Constants.CandleInterval.DAY_1.getBinanceCode(), 30)
                    .enqueue(new Callback<List<List<Object>>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<List<Object>>> call,
                                               @NonNull Response<List<List<Object>>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                for (List<Object> kline : response.body()) {
                                    com.example.cryptoanalysisai.models.BinanceModels.BinanceKline binanceKline =
                                            new com.example.cryptoanalysisai.models.BinanceModels.BinanceKline(kline);
                                    candleDataList.add(binanceKline.toUpbitFormat(market));
                                }
                                latch.countDown();
                            } else {
                                Log.e(TAG, "바이낸스 캔들 데이터 로드 실패: " + response.code());
                                latch.countDown();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<List<List<Object>>> call, @NonNull Throwable t) {
                            Log.e(TAG, "바이낸스 캔들 API 호출 실패: " + t.getMessage());
                            latch.countDown();
                        }
                    });
        } else {
            latch.countDown();
        }

        try {
            // 최대 30초 대기
            latch.await(30, TimeUnit.SECONDS);

            if (!candleDataList.isEmpty()) {
                // 현재가 로드
                loadCurrentPrice(market, coinInfo, exchangeType, candleDataList);
            } else {
                Log.e(TAG, "캔들 데이터를 가져오지 못했습니다: " + market);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "캔들 데이터 로드 중단: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 현재가 로드
     */
    private void loadCurrentPrice(String market, CoinInfo coinInfo, ExchangeType exchangeType,
                                  List<CandleData> candleDataList) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean processed = new AtomicBoolean(false);

        if (exchangeType == ExchangeType.UPBIT) {
            UpbitApiService apiService = RetrofitClient.getUpbitApiService();

            apiService.getTicker(market).enqueue(new Callback<List<TickerData>>() {
                @Override
                public void onResponse(@NonNull Call<List<TickerData>> call,
                                       @NonNull Response<List<TickerData>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        TickerData tickerData = response.body().get(0);
                        coinInfo.setCurrentPrice(tickerData.getTradePrice());
                        coinInfo.setPriceChange(tickerData.getChangeRate());

                        if (!processed.get()) {
                            processed.set(true);
                            // 기술적 지표 계산 후 분석 수행
                            performAnalysis(coinInfo, exchangeType, candleDataList, tickerData);
                        }
                    }
                    latch.countDown();
                }

                @Override
                public void onFailure(@NonNull Call<List<TickerData>> call, @NonNull Throwable t) {
                    Log.e(TAG, "업비트 현재가 로드 실패: " + t.getMessage());
                    latch.countDown();
                }
            });
        } else if (exchangeType == ExchangeType.BINANCE) {
            BinanceApiService apiService = RetrofitClient.getBinanceApiService();

            apiService.getTicker(market).enqueue(new Callback<com.example.cryptoanalysisai.models.BinanceTicker>() {
                @Override
                public void onResponse(@NonNull Call<com.example.cryptoanalysisai.models.BinanceTicker> call,
                                       @NonNull Response<com.example.cryptoanalysisai.models.BinanceTicker> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        final com.example.cryptoanalysisai.models.BinanceTicker ticker = response.body();
                        coinInfo.setCurrentPrice(ticker.getPrice());

                        apiService.get24hTicker(market).enqueue(new Callback<com.example.cryptoanalysisai.models.BinanceTicker>() {
                            @Override
                            public void onResponse(@NonNull Call<com.example.cryptoanalysisai.models.BinanceTicker> call,
                                                   @NonNull Response<com.example.cryptoanalysisai.models.BinanceTicker> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    coinInfo.setPriceChange(response.body().getPriceChangePercent() / 100.0);
                                }

                                if (!processed.get()) {
                                    processed.set(true);
                                    // 티커 데이터 생성
                                    TickerData tickerData = new TickerData();
                                    tickerData.setMarket(market);
                                    tickerData.setTradePrice(coinInfo.getCurrentPrice());
                                    tickerData.setChangeRate(coinInfo.getPriceChange());

                                    // 분석 수행
                                    performAnalysis(coinInfo, exchangeType, candleDataList, tickerData);
                                }
                                latch.countDown();
                            }

                            @Override
                            public void onFailure(@NonNull Call<com.example.cryptoanalysisai.models.BinanceTicker> call,
                                                  @NonNull Throwable t) {
                                Log.e(TAG, "바이낸스 24시간 변화율 로드 실패: " + t.getMessage());

                                if (!processed.get()) {
                                    processed.set(true);
                                    TickerData tickerData = new TickerData();
                                    tickerData.setMarket(market);
                                    tickerData.setTradePrice(coinInfo.getCurrentPrice());
                                    tickerData.setChangeRate(0);

                                    performAnalysis(coinInfo, exchangeType, candleDataList, tickerData);
                                }
                                latch.countDown();
                            }
                        });
                    } else {
                        latch.countDown();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<com.example.cryptoanalysisai.models.BinanceTicker> call, @NonNull Throwable t) {
                    Log.e(TAG, "바이낸스 현재가 로드 실패: " + t.getMessage());
                    latch.countDown();
                }
            });
        } else {
            latch.countDown();
        }

        try {
            // 최대 30초 대기
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "현재가 로드 중단: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 분석 수행
     */
    private void performAnalysis(CoinInfo coinInfo, ExchangeType exchangeType,
                                 List<CandleData> candleDataList, TickerData tickerData) {
        if (candleDataList.isEmpty()) {
            Log.e(TAG, "분석할 캔들 데이터가 없습니다");
            return;
        }

        // 기술적 지표 계산
        TechnicalIndicatorService indicatorService = new TechnicalIndicatorService();
        Map<String, Object> technicalIndicators = indicatorService.calculateAllIndicators(candleDataList);

        // Claude API로 분석 요청
        analysisService.generateAnalysis(coinInfo, candleDataList, tickerData, exchangeType, technicalIndicators,
                new AnalysisService.AnalysisCallback() {
                    @Override
                    public void onAnalysisSuccess(AnalysisResult result, String rawResponse) {
                        Log.d(TAG, "코인 분석 성공: " + coinInfo.getSymbol());

                        // Firebase에 저장
                        firebaseManager.saveAnalysisResult(result, coinInfo, exchangeType,
                                new FirebaseManager.OnAnalysisSavedListener() {
                                    @Override
                                    public void onSuccess(String documentId) {
                                        Log.d(TAG, "Firebase 저장 성공: " + documentId);
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        Log.e(TAG, "Firebase 저장 실패: " + errorMessage);
                                    }
                                });
                    }

                    @Override
                    public void onAnalysisFailure(String error) {
                        Log.e(TAG, "코인 분석 실패: " + error);
                    }
                });
    }

    /**
     * 주기적 작업 스케줄링
     */
    public static void scheduleHourlyAnalysis(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)  // 네트워크 연결 필요
                .build();

        // 1시간마다 실행하는 주기적 작업 요청
        PeriodicWorkRequest analysisWorkRequest = new PeriodicWorkRequest.Builder(
                AnalysisWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();

        // 기존 작업이 있으면 교체
        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "hourly_crypto_analysis",
                        androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                        analysisWorkRequest);

        Log.d(TAG, "시간별 분석 작업 스케줄링 완료");
    }
}