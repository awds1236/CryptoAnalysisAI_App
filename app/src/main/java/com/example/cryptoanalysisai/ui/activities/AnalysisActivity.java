package com.example.cryptoanalysisai.ui.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cryptoanalysisai.MainActivity;
import com.example.cryptoanalysisai.api.BinanceApiService;
import com.example.cryptoanalysisai.api.RetrofitClient;
import com.example.cryptoanalysisai.api.UpbitApiService;
import com.example.cryptoanalysisai.databinding.ActivityAnalysisBinding;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.CandleData;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.models.BinanceTicker;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AnalysisActivity extends AppCompatActivity {

    private ActivityAnalysisBinding binding;
    private CoinInfo coinInfo;
    private ExchangeType exchangeType = ExchangeType.UPBIT;
    private AnalysisResult analysisResult;
    private AnalysisService analysisService;
    private TechnicalIndicatorService indicatorService;
    private List<CandleData> candleDataList = new ArrayList<>();
    private Map<String, Object> technicalIndicators;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAnalysisBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 툴바 설정
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("상세 분석");

        // 인텐트에서 코인 정보 및 거래소 타입 추출
        if (getIntent() != null) {
            String market = getIntent().getStringExtra(Constants.EXTRA_COIN_INFO);
            if (market != null) {
                coinInfo = new CoinInfo();
                coinInfo.setMarket(market);
            }

            String exchangeCode = getIntent().getStringExtra(Constants.EXTRA_EXCHANGE_TYPE);
            if (exchangeCode != null) {
                exchangeType = ExchangeType.fromCode(exchangeCode);
            }
        }

        // 서비스 초기화
        analysisService = new AnalysisService();
        indicatorService = new TechnicalIndicatorService();

        // 라인 차트 초기화
        setupLineChart();

        // 결과 컨테이너 초기에 숨기기
        binding.resultContainer.setVisibility(View.GONE);

        // 분석하기 버튼 클릭 리스너 설정
        binding.btnAnalyze.setOnClickListener(v -> {
            // 분석 시작
            binding.progressAnalysis.setVisibility(View.VISIBLE);
            loadCandleData(coinInfo.getMarket(), Constants.CandleInterval.DAY_1);
        });

        // 데이터 로드
        if (coinInfo != null && coinInfo.getMarket() != null) {
            // 타이틀 설정
            binding.tvCoinTitle.setText(coinInfo.getDisplayName() != null ?
                    coinInfo.getDisplayName() + " (" + coinInfo.getSymbol() + ")" : coinInfo.getMarket());

            // 거래소 정보 설정
            binding.tvExchangeInfo.setText("거래소: " + exchangeType.getDisplayName() +
                    " / 통화단위: " + (exchangeType == ExchangeType.UPBIT ? "원" : "달러(USD)"));

            // 초기 데이터로드는 하지 않고, 사용자가 분석하기 버튼을 클릭했을 때만 로드
            // loadCandleData(coinInfo.getMarket(), Constants.CandleInterval.DAY_1);
        } else {
            Toast.makeText(this, "코인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 라인 차트 초기화
     */
    private void setupLineChart() {
        // 차트 설정 (binding.lineChart가 XML에 정의되어 있어야 함)
        if (binding.chartContainer != null) {
            // 여기에 차트 설정 코드 추가
            // 예: binding.chartContainer.addView(chartView);
        }
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

                    // 기술적 지표 계산
                    technicalIndicators = indicatorService.calculateAllIndicators(candleDataList);

                    // 차트 데이터 업데이트
                    updateChartData(candleDataList);

                    // 분석 실행
                    requestAnalysis();
                } else {
                    Toast.makeText(AnalysisActivity.this, "데이터 로딩 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    binding.progressAnalysis.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<CandleData>> call, @NonNull Throwable t) {
                Toast.makeText(AnalysisActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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

                    // 날짜 순서대로 정렬 (최신 -> 과거)
                    Collections.reverse(candleDataList);

                    // 기술적 지표 계산
                    technicalIndicators = indicatorService.calculateAllIndicators(candleDataList);

                    // 차트 데이터 업데이트
                    updateChartData(candleDataList);

                    // 분석 실행
                    requestAnalysis();
                } else {
                    Toast.makeText(AnalysisActivity.this, "데이터 로딩 실패: " + response.code(), Toast.LENGTH_SHORT).show();
                    binding.progressAnalysis.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<List<Object>>> call, @NonNull Throwable t) {
                Toast.makeText(AnalysisActivity.this, "네트워크 오류: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                binding.progressAnalysis.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 차트 데이터 업데이트
     */
    private void updateChartData(List<CandleData> candles) {
        // 이 메서드는 binding.lineChart 대신 다른 방식으로 차트를 업데이트해야 함
        binding.progressAnalysis.setVisibility(View.GONE);
    }

    /**
     * Claude API로 분석 요청
     */
    private void requestAnalysis() {
        if (coinInfo == null || candleDataList.isEmpty() || technicalIndicators == null) {
            binding.progressAnalysis.setVisibility(View.GONE);
            return;
        }

        // 코인 정보에서 현재가 업데이트 (최신 캔들 데이터 사용)
        if (!candleDataList.isEmpty()) {
            CandleData latestCandle = candleDataList.get(0);
            coinInfo.setCurrentPrice(latestCandle.getTradePrice());
        }

        analysisService.generateAnalysis(coinInfo, candleDataList, null, exchangeType, technicalIndicators,
                new AnalysisService.AnalysisCallback() {
                    @Override
                    public void onAnalysisSuccess(AnalysisResult result, String rawResponse) {
                        analysisResult = result;

                        runOnUiThread(() -> {
                            // 결과 UI 업데이트
                            updateAnalysisUI();
                            // 프로그레스바 숨기기
                            binding.progressAnalysis.setVisibility(View.GONE);
                            // 결과 컨테이너 보이기
                            binding.resultContainer.setVisibility(View.VISIBLE);

                            // 가장 간단한 방법: 결과 컨테이너에 포커스 주기
                            binding.resultContainer.post(() -> {
                                binding.resultContainer.requestFocus();
                            });

                            // 성공 메시지 표시
                            Toast.makeText(AnalysisActivity.this, "분석이 완료되었습니다!", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onAnalysisFailure(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(AnalysisActivity.this, "분석 실패: " + error, Toast.LENGTH_SHORT).show();
                            binding.progressAnalysis.setVisibility(View.GONE);
                        });
                    }
                });
    }


    /**
     * 분석 결과로 UI 업데이트
     */
    private void updateAnalysisUI() {
        if (analysisResult == null) return;

        // 분석 요약 표시
        binding.tvAnalysisSummary.setText(analysisResult.getSummary());

        // 결과 컨테이너 안에 있는 모든 결과 뷰들 업데이트
        // 이 부분은 UI 구조에 따라 다르게 구현해야 할 수 있습니다

        // 다른 결과 뷰들도 업데이트...
        // 예: 매수/매도 추천, 시간별 전망, 기술적 분석 등

        // 결과 컨테이너 보이기
        binding.resultContainer.setVisibility(View.VISIBLE);
    }

    // 결과 메인 화면으로 전달
    @Override
    public void onBackPressed() {
        if (analysisResult != null) {
            // 메인 화면으로 결과 전달
            if (getParent() instanceof MainActivity) {
                ((MainActivity) getParent()).deliverAnalysisResult(analysisResult);
            }
        }
        super.onBackPressed();
    }
}