package com.coinsense.cryptoanalysisai;

import static android.content.ContentValues.TAG;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.coinsense.cryptoanalysisai.databinding.ActivityMainBinding;
import com.coinsense.cryptoanalysisai.models.AnalysisResult;
import com.coinsense.cryptoanalysisai.models.CoinInfo;
import com.coinsense.cryptoanalysisai.models.ExchangeType;
import com.coinsense.cryptoanalysisai.services.AdManager;
import com.coinsense.cryptoanalysisai.services.BillingManager;
import com.coinsense.cryptoanalysisai.services.ExchangeRateManager;
import com.coinsense.cryptoanalysisai.services.SubscriptionManager;
import com.coinsense.cryptoanalysisai.ui.activities.LoginActivity;
import com.coinsense.cryptoanalysisai.ui.activities.SubscriptionActivity;
import com.coinsense.cryptoanalysisai.ui.fragments.AnalysisFragment;
import com.coinsense.cryptoanalysisai.ui.fragments.CoinListFragment;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;

import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity implements CoinListFragment.OnCoinSelectedListener {

    private ActivityMainBinding binding;
    private CoinInfo selectedCoin;
    private ExchangeType selectedExchange = ExchangeType.BINANCE; // 바이낸스로 변경

    // 차트 탭 제거: 코인 목록과 분석 탭만 유지
    private final String[] tabTitles = new String[]{"코인 목록", "분석"};

    // 가격 업데이트 핸들러
    private final Handler priceUpdateHandler = new Handler(Looper.getMainLooper());
    private boolean isAutoRefreshEnabled = true;

    private boolean doubleBackToExitPressedOnce = false;
    private final Handler backPressHandler = new Handler(Looper.getMainLooper());
    private final int BACK_PRESS_INTERVAL = 2000; // 2초

    private long backPressedTime;

    private static final String PREF_DARK_MODE = "pref_dark_mode";


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // 저장된 다크 모드 설정 적용
        if (isDarkModeEnabled()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        super.onCreate(savedInstanceState);

        // 로그인 상태 확인 (바인딩 전에)
        if (!isUserSignedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // 한 번만 바인딩 초기화
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 툴바 설정 (한 번만)
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false); // 내비게이션 아이콘 비활성화
        }

        // 로그인 상태 확인
        if (!isUserSignedIn()) {
            // 로그인되지 않은 경우 로그인 화면으로 이동
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // ViewPager 설정
        setupViewPager();

        // 3초마다 가격 갱신 시작
        startPriceUpdates();

        loadExchangeRate();

        restoreSelectedCoin();

        // 구독 상태 동기화
        BillingManager.getInstance(this).syncSubscriptions();

        AdManager.getInstance(this);

        // 테마가 변경되었는지 확인 (Bundle이 null이 아니면 재생성된 것)
        if (savedInstanceState != null) {
            // 테마 변경 후 재생성된 경우, 선택된 코인이 있으면 분석 데이터 새로고침
            if (selectedCoin != null) {
                // 딜레이 증가: 1초 후에 새로고침 수행
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    // 모든 프래그먼트를 찾아서 UI 상태 갱신
                    AnalysisFragment analysisFragment = (AnalysisFragment) getSupportFragmentManager()
                            .findFragmentByTag("f1");
                    if (analysisFragment != null) {
                        analysisFragment.refreshAllUIs();
                        analysisFragment.loadAnalysisFromApi();
                    }
                }, 1000); // 1초로 증가
            }
        }





        // 뒤로가기 처리를 위한 콜백 등록
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            private boolean doubleBackToExitPressedOnce = false;
            private final Handler handler = new Handler(Looper.getMainLooper());

            @Override
            public void handleOnBackPressed() {
                int currentItem = binding.viewPager.getCurrentItem();

                if (currentItem == 1) {
                    // 분석 화면에서는 코인 목록으로 이동
                    binding.viewPager.setCurrentItem(0);
                } else {
                    // 코인 목록에서는 두 번 눌러 종료
                    if (doubleBackToExitPressedOnce) {
                        // 앱 종료
                        finishAffinity();
                        return;
                    }

                    doubleBackToExitPressedOnce = true;
                    //Toast.makeText(MainActivity.this, "뒤로가기를 한번 더 누르면 종료됩니다", Toast.LENGTH_SHORT).show();
                    Snackbar.make(binding.getRoot(), "뒤로가기를 한번 더 누르면 종료됩니다", 1000).show();
                    handler.postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
                }
            }
        });
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

    // 분석 데이터 새로고침 메서드 추가
    private void refreshAnalysisData() {
        if (selectedCoin != null) {
            // 현재 보이는 분석 프래그먼트 찾기
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("f1");
            if (fragment instanceof AnalysisFragment) {
                AnalysisFragment analysisFragment = (AnalysisFragment) fragment;
                // 분석 데이터 새로고침
                analysisFragment.loadAnalysisFromApi();
            }
        }
    }

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
            // 다크 모드 설정 다이얼로그 표시
            showThemeSettingsDialog();
            return true;
        } else if (id == R.id.action_subscription) {
            // 구독 관리 화면으로 이동
            Intent intent = new Intent(this, SubscriptionActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_logout) {
            // 로그아웃 처리
            logout();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * 로그아웃 처리
     */
    private void logout() {
        // Firebase 로그아웃
        FirebaseAuth.getInstance().signOut();

        // 구독 관리자에 사용자 변경 알림
        SubscriptionManager.getInstance(this).updateUser(null);

        // SharedPreferences 로그인 상태와 구독 관련 정보 모두 제거
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Constants.PREF_IS_LOGGED_IN, false);

        // 구독 관련 정보 모두 삭제
        editor.remove(Constants.PREF_IS_SUBSCRIBED);
        editor.remove(Constants.PREF_SUBSCRIPTION_EXPIRY);
        editor.remove(Constants.PREF_SUBSCRIPTION_TYPE);
        editor.remove(Constants.PREF_SUBSCRIPTION_START_TIME);
        editor.remove(Constants.PREF_SUBSCRIPTION_AUTO_RENEWING);
        editor.remove(Constants.PREF_MONTHLY_PRICE);
        editor.remove(Constants.PREF_YEARLY_PRICE);

        // AdManager 관련 SharedPreferences도 초기화
        editor.apply();

        // 광고 관련 캐시도 초기화
        AdManager.getInstance(this).resetAllPermissions();

        // 로그인 화면으로 이동
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * 현재 보이는 프래그먼트 새로고침
     */
    // MainActivity.java의 refreshCurrentFragment 메서드 수정
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
                    // 분석 결과 새로고침
                    analysisFragment.updateCoin(selectedCoin, selectedExchange);
                    // 추가: 모든 UI 상태도 새로고침
                    analysisFragment.refreshAllUIs();
                    // 추가: 분석 데이터 다시 로드
                    analysisFragment.loadAnalysisFromApi();
                }
                break;
        }
    }

    private boolean isUserSignedIn() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(Constants.PREF_IS_LOGGED_IN, false);
    }

    private void loadExchangeRate() {
        ExchangeRateManager.getInstance().fetchExchangeRate(new ExchangeRateManager.OnExchangeRateListener() {
            @Override
            public void onExchangeRateUpdated(double rate) {
                Log.d(TAG, "환율 업데이트 완료: 1 USD = " + rate + " KRW");
                // 필요한 경우 UI 업데이트
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "환율 업데이트 실패: " + errorMessage);
            }
        });
    }

    public void navigateToCoinsTab() {
        binding.viewPager.setCurrentItem(0);
    }

    /**
     * 현재 다크 모드 상태를 확인합니다.
     */
    private boolean isDarkModeEnabled() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_DARK_MODE, true); // 기본값은 true (다크 모드)
    }

    /**
     * 테마 상태를 설정합니다.
     */
    private void setDarkMode(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_DARK_MODE, enabled);
        editor.apply();

        // 현재 선택된 코인 정보 저장
        saveCurrentCoinInfo();

        // 테마 모드 설정
        if (enabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // 대신 앱을 완전히 재시작하는 코드
        Intent intent = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            // 전환 애니메이션 제거 (선택사항)
            overridePendingTransition(0, 0);
        }
    }

    // 이 메서드 추가
    private void saveCurrentCoinInfo() {
        // 현재 선택된 코인 정보를 SharedPreferences에 저장
        if (selectedCoin != null) {
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("SELECTED_COIN_MARKET", selectedCoin.getMarket());
            editor.putString("SELECTED_COIN_SYMBOL", selectedCoin.getSymbol());
            editor.putString("SELECTED_COIN_NAME", selectedCoin.getDisplayName());
            editor.apply();
        }
    }

    // onCreate() 메서드에서 복원 로직 추가 (기존 onCreate 내부)
    private void restoreSelectedCoin() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        String market = prefs.getString("SELECTED_COIN_MARKET", null);
        String symbol = prefs.getString("SELECTED_COIN_SYMBOL", null);
        String name = prefs.getString("SELECTED_COIN_NAME", null);

        if (market != null && symbol != null) {
            selectedCoin = new CoinInfo();
            selectedCoin.setMarket(market);
            selectedCoin.setSymbol(symbol);
            if (name != null) {
                selectedCoin.setKoreanName(name);
            }
        }
    }

    /**
     * 테마 설정 다이얼로그를 표시합니다.
     */
    private void showThemeSettingsDialog() {
        boolean isDarkMode = isDarkModeEnabled();

        // 다이얼로그 생성
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("앱 테마 설정");

        // 라디오 버튼 항목 생성
        String[] themes = {"라이트 모드", "다크 모드"};
        int checkedItem = isDarkMode ? 1 : 0;

        builder.setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
            // 선택한 항목에 따라 다크 모드 설정
            setDarkMode(which == 1);
            dialog.dismiss();
        });

        // 취소 버튼
        builder.setNegativeButton("취소", (dialog, which) -> dialog.dismiss());

        // 다이얼로그 표시
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}