package com.example.cryptoanalysisai;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.cryptoanalysisai.databinding.ActivityMainBinding;
import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.ui.activities.AnalysisActivity;
import com.example.cryptoanalysisai.ui.fragments.AnalysisFragment;
import com.example.cryptoanalysisai.ui.fragments.ChartFragment;
import com.example.cryptoanalysisai.ui.fragments.CoinListFragment;
import com.example.cryptoanalysisai.utils.Constants;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity implements CoinListFragment.OnCoinSelectedListener {

    private ActivityMainBinding binding;
    private CoinInfo selectedCoin;
    private ExchangeType selectedExchange = ExchangeType.UPBIT;

    private final String[] tabTitles = new String[]{"코인 목록", "차트", "분석"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 툴바 설정
        setSupportActionBar(binding.toolbar);

        // 저장된 설정 불러오기
        loadPreferences();

        // ViewPager 설정
        setupViewPager();

        // FAB 설정
        binding.fab.setOnClickListener(v -> {
            if (selectedCoin != null) {
                startAnalysisActivity();
            } else {
                Toast.makeText(this, "먼저 코인을 선택해주세요.", Toast.LENGTH_SHORT).show();
            }
        });
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

                // 분석 페이지일 때만 FAB 표시
                if (position == 2) {
                    binding.fab.show();
                } else {
                    binding.fab.hide();
                }
            }
        });
    }

    /**
     * 저장된 설정 불러오기
     */
    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String exchangeCode = prefs.getString(Constants.PREF_EXCHANGE_TYPE, ExchangeType.UPBIT.getCode());
        selectedExchange = ExchangeType.fromCode(exchangeCode);
    }

    /**
     * 설정 저장하기
     */
    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.PREF_EXCHANGE_TYPE, selectedExchange.getCode());
        if (selectedCoin != null) {
            editor.putString(Constants.PREF_LAST_MARKET, selectedCoin.getMarket());
        }
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePreferences();
    }

    /**
     * 코인 선택 이벤트 처리
     */
    @Override
    public void onCoinSelected(CoinInfo coinInfo, ExchangeType exchangeType) {
        this.selectedCoin = coinInfo;
        this.selectedExchange = exchangeType;

        // 코인이 선택되면 차트 페이지로 이동
        binding.viewPager.setCurrentItem(1);

        // 다른 프래그먼트에 선택된 코인 정보 전달
        updateFragmentsWithCoin();
    }

    /**
     * 선택된 코인 정보로 프래그먼트 갱신
     */
    private void updateFragmentsWithCoin() {
        ChartFragment chartFragment = (ChartFragment) getSupportFragmentManager()
                .findFragmentByTag("f" + binding.viewPager.getCurrentItem());

        if (chartFragment != null) {
            chartFragment.updateCoin(selectedCoin, selectedExchange);
        }

        AnalysisFragment analysisFragment = (AnalysisFragment) getSupportFragmentManager()
                .findFragmentByTag("f" + 2); // 분석 프래그먼트는 인덱스 2

        if (analysisFragment != null) {
            analysisFragment.updateCoin(selectedCoin, selectedExchange);
        }
    }

    /**
     * 분석 액티비티 시작
     */
    private void startAnalysisActivity() {
        Intent intent = new Intent(this, AnalysisActivity.class);
        intent.putExtra(Constants.EXTRA_COIN_INFO, selectedCoin);
        intent.putExtra(Constants.EXTRA_EXCHANGE_TYPE, selectedExchange.getCode());
        startActivity(intent);
    }

    /**
     * 분석 결과 전달
     */
    public void deliverAnalysisResult(AnalysisResult result) {
        AnalysisFragment analysisFragment = (AnalysisFragment) getSupportFragmentManager()
                .findFragmentByTag("f" + 2);

        if (analysisFragment != null) {
            analysisFragment.setAnalysisResult(result);
            binding.viewPager.setCurrentItem(2); // 분석 탭으로 이동
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
                    return ChartFragment.newInstance(selectedCoin, selectedExchange);
                case 2:
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
            // 설정 화면 이동 구현
            Toast.makeText(this, "설정 기능은 추후 추가될 예정입니다.", Toast.LENGTH_SHORT).show();
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
                ChartFragment chartFragment = (ChartFragment) getSupportFragmentManager()
                        .findFragmentByTag("f" + currentPage);
                if (chartFragment != null && selectedCoin != null) {
                    chartFragment.updateCoin(selectedCoin, selectedExchange);
                }
                break;
            case 2:
                AnalysisFragment analysisFragment = (AnalysisFragment) getSupportFragmentManager()
                        .findFragmentByTag("f" + currentPage);
                if (analysisFragment != null && selectedCoin != null) {
                    analysisFragment.updateCoin(selectedCoin, selectedExchange);
                }
                break;
        }
    }
}
