package com.example.cryptoanalysisai;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.cryptoanalysisai.databinding.ActivityMainBinding;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.ui.fragments.AnalysisFragment;
import com.example.cryptoanalysisai.ui.fragments.CoinListFragment;
import com.example.cryptoanalysisai.utils.Constants;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity implements CoinListFragment.OnCoinSelectedListener {

    private ActivityMainBinding binding;
    private CoinInfo selectedCoin;
    private ExchangeType selectedExchange = ExchangeType.BINANCE; // 바이낸스로 변경

    // 차트 탭 제거: 코인 목록과 분석 탭만 유지
    private final String[] tabTitles = new String[]{"코인 목록", "분석"};

    // 가격 업데이트 핸들러
    private final Handler priceUpdateHandler = new Handler(Looper.getMainLooper());
    private boolean isAutoRefreshEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 툴바 설정
        setSupportActionBar(binding.toolbar);

        // 바이낸스로 고정하므로 저장된 설정은 필요없음
        // loadPreferences();

        // ViewPager 설정
        setupViewPager();

        // 3초마다 가격 갱신 시작
        startPriceUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAutoRefreshEnabled = true;
        startPriceUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAutoRefreshEnabled = false;
        stopPriceUpdates();
        // 설정 저장 필요 없음
        // savePreferences();
    }

    @Override
    protected void onDestroy() {
        stopPriceUpdates();
        super.onDestroy();
    }

    /**
     * 가격 업데이트 시작
     */
    private void startPriceUpdates() {
        priceUpdateHandler.postDelayed(priceUpdateRunnable, Constants.PRICE_REFRESH_INTERVAL);
    }

    /**
     * 가격 업데이트 중지
     */
    private void stopPriceUpdates() {
        priceUpdateHandler.removeCallbacks(priceUpdateRunnable);
    }

    /**
     * 가격 업데이트 Runnable
     */
    private final Runnable priceUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAutoRefreshEnabled) {
                updateCoinsPrice();
                priceUpdateHandler.postDelayed(this, Constants.PRICE_REFRESH_INTERVAL);
            }
        }
    };

    /**
     * 코인 가격 업데이트
     */
    private void updateCoinsPrice() {
        // 코인 목록 탭이 표시 중일 때
        if (binding.viewPager.getCurrentItem() == 0) {
            CoinListFragment coinListFragment = (CoinListFragment) getSupportFragmentManager()
                    .findFragmentByTag("f0");
            if (coinListFragment != null) {
                coinListFragment.refreshPrices();
            }
        }

        // 분석 탭이 표시 중이고 선택된 코인이 있을 때
        if (binding.viewPager.getCurrentItem() == 1 && selectedCoin != null) {
            AnalysisFragment analysisFragment = (AnalysisFragment) getSupportFragmentManager()
                    .findFragmentByTag("f1");
            if (analysisFragment != null) {
                analysisFragment.updatePrice();
            }
        }
    }

    /**
     * ViewPager와 TabLayout 설정
     */
    private void setupViewPager() {
        ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(this);
        binding.viewPager.setAdapter(viewPagerAdapter);

        // 탭 레이아웃과 뷰페이저 연결
        new TabLayoutMediator(binding.tabs, binding.viewPager, (tab, position) -> {
            tab.setText(tabTitles[position]);
        }).attach();

        // 페이지 변경 리스너
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // 페이지 변경 시 필요한 작업이 있으면 여기에 구현
            }
        });
    }

    /**
     * 저장된 설정 불러오기 - 필요 없음 (바이낸스로 고정)
     */
    /*
    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String exchangeCode = prefs.getString(Constants.PREF_EXCHANGE_TYPE, ExchangeType.BINANCE.getCode());
        selectedExchange = ExchangeType.fromCode(exchangeCode);

        // 바이낸스로 고정
        selectedExchange = ExchangeType.BINANCE;
    }
    */

    /**
     * 설정 저장하기 - 필요 없음 (바이낸스로 고정)
     */
    /*
    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREF_EXCHANGE_TYPE, selectedExchange.getCode());
        if (selectedCoin != null) {
            editor.putString(Constants.PREF_LAST_MARKET, selectedCoin.getMarket());
        }
        editor.apply();
    }
    */

    /**
     * 코인 선택 이벤트 처리
     */
    @Override
    public void onCoinSelected(CoinInfo coinInfo, ExchangeType exchangeType) {
        this.selectedCoin = coinInfo;
        this.selectedExchange = exchangeType;

        // 코인이 선택되면 분석 페이지로 이동
        binding.viewPager.setCurrentItem(1);

        // 다른 프래그먼트에 선택된 코인 정보 전달
        updateFragmentsWithCoin();
    }

    /**
     * 선택된 코인 정보로 프래그먼트 갱신
     */
    private void updateFragmentsWithCoin() {
        AnalysisFragment analysisFragment = (AnalysisFragment) getSupportFragmentManager()
                .findFragmentByTag("f" + 1);

        if (analysisFragment != null) {
            analysisFragment.updateCoin(selectedCoin, selectedExchange);
        }
    }

    /**
     * 분석 결과 전달
     */
    public void deliverAnalysisResult(AnalysisResult result) {
        AnalysisFragment analysisFragment = (AnalysisFragment) getSupportFragmentManager()
                .findFragmentByTag("f" + 1);

        if (analysisFragment != null) {
            analysisFragment.setAnalysisResult(result);
            binding.viewPager.setCurrentItem(1);
        }
    }

    /**
     * ViewPager Adapter
     */
    private class ViewPagerAdapter extends FragmentStateAdapter {

        public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return CoinListFragment.newInstance(selectedExchange);
                case 1:
                    return AnalysisFragment.newInstance(selectedCoin, selectedExchange);
                default:
                    return CoinListFragment.newInstance(selectedExchange);
            }
        }

        @Override
        public int getItemCount() {
            return tabTitles.length;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            refreshCurrentFragment();
            return true;
        } else if (id == R.id.action_settings) {
            // 설정 화면 구현 없음
            Toast.makeText(this, "바이낸스 거래소의 4개 코인에 대한 데이터만 표시합니다.", Toast.LENGTH_SHORT).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * 현재 보이는 프래그먼트 새로고침
     */
    private void refreshCurrentFragment() {
        int currentPage = binding.viewPager.getCurrentItem();

        switch (currentPage) {
            case 0:
                CoinListFragment coinListFragment = (CoinListFragment) getSupportFragmentManager()
                        .findFragmentByTag("f" + currentPage);
                if (coinListFragment != null) {
                    coinListFragment.refreshData();
                }
                break;
            case 1:
                AnalysisFragment analysisFragment = (AnalysisFragment) getSupportFragmentManager()
                        .findFragmentByTag("f" + currentPage);
                if (analysisFragment != null && selectedCoin != null) {
                    analysisFragment.updateCoin(selectedCoin, selectedExchange);
                }
                break;
        }
    }
}