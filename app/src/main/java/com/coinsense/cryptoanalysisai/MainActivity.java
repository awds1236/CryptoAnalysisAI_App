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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.billingclient.api.ProductDetails;
import com.coinsense.cryptoanalysisai.databinding.ActivityMainBinding;
import com.coinsense.cryptoanalysisai.models.AnalysisResult;
import com.coinsense.cryptoanalysisai.models.CoinInfo;
import com.coinsense.cryptoanalysisai.models.ExchangeType;
import com.coinsense.cryptoanalysisai.services.AdManager;
import com.coinsense.cryptoanalysisai.services.BillingManager;
import com.coinsense.cryptoanalysisai.services.ExchangeRateManager;
import com.coinsense.cryptoanalysisai.services.SubscriptionManager;
import com.coinsense.cryptoanalysisai.ui.activities.BaseActivity;
import com.coinsense.cryptoanalysisai.ui.activities.LoginActivity;
import com.coinsense.cryptoanalysisai.ui.activities.SettingsActivity;
import com.coinsense.cryptoanalysisai.ui.activities.SubscriptionActivity;
import com.coinsense.cryptoanalysisai.ui.fragments.AnalysisFragment;
import com.coinsense.cryptoanalysisai.ui.fragments.CoinListFragment;
import com.coinsense.cryptoanalysisai.utils.Constants;
import com.coinsense.cryptoanalysisai.utils.LocaleHelper;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import androidx.appcompat.app.AlertDialog;

import java.util.List;

public class MainActivity extends BaseActivity implements
        CoinListFragment.OnCoinSelectedListener,
        BillingManager.BillingStatusListener {  // 🔧 BillingStatusListener 인터페이스 구현 추가

    private static final String TAG = "MainActivity";
    private static final String PREF_DARK_MODE = "pref_dark_mode";

    private ActivityMainBinding binding;
    private CoinInfo selectedCoin;
    private ExchangeType selectedExchangeType = ExchangeType.UPBIT;

    // 서비스 인스턴스들
    private SubscriptionManager subscriptionManager;
    private BillingManager billingManager;  // 🔧 BillingManager 인스턴스 추가
    private AdManager adManager;

    // UI 업데이트를 위한 핸들러
    private Handler uiUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable subscriptionUpdateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔧 상태바 숨기기 (라이트모드에서도 적용)
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // 다크 모드 설정 적용
        boolean isDarkMode = isDarkModeEnabled();
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 서비스 초기화
        initializeServices();

        // UI 초기화
        initializeUI();

        // 선택된 코인 정보 복원
        restoreSelectedCoin();

        // 뒤로가기 버튼 핸들러 설정
        setupBackPressHandler();

        // 정기적인 UI 업데이트 시작
        startPeriodicUIUpdates();
    }

    /**
     * 🔧 서비스들 초기화 (구독 상태 모니터링 포함)
     */
    private void initializeServices() {
        // SubscriptionManager 초기화
        subscriptionManager = SubscriptionManager.getInstance(this);

        // 🔧 BillingManager 초기화 및 리스너 설정
        billingManager = BillingManager.getInstance(this);
        billingManager.setBillingStatusListener(this);  // 리스너 등록

        // AdManager 초기화 (파라미터 필요)
        adManager = AdManager.getInstance(this);

        // ExchangeRateManager 초기화 (파라미터 없음)
        ExchangeRateManager.getInstance();

        Log.d(TAG, "모든 서비스 초기화 완료");
    }

    private void initializeUI() {
        setSupportActionBar(binding.toolbar);

        // ViewPager2와 TabLayout 설정 (올바른 바인딩 이름 사용)
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);

        new TabLayoutMediator(binding.tabs, binding.viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("코인 목록");
                    break;
                case 1:
                    tab.setText("AI 분석");
                    break;
            }
        }).attach();

        // 🔧 수정: 기본 화면을 코인 목록(0번)으로 설정
        binding.viewPager.setCurrentItem(0);
    }

    /**
     * 🔧 Activity Resume 시 구독 상태 강화된 확인 (조용한 모드)
     */
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "🔄 MainActivity onResume - 구독 상태 확인 시작");


        // BillingManager 연결 및 구독 상태 확인 (조용한 모드)
        if (billingManager != null) {
            billingManager.connectToPlayBillingService();

            // 🔧 조용한 구독 상태 모니터링 시작 (메시지 표시 안함)
            billingManager.startSubscriptionMonitoring();

            // 즉시 구독 상태 확인 (조용한 모드)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                billingManager.queryPurchases();
                Log.d(TAG, "✅ 구독 상태 조용한 확인 완료");
            }, 1000);
        }

        // 구독 상태 검증
        if (subscriptionManager != null) {
            subscriptionManager.verifySubscription();
        }

        // UI 업데이트 시작
        startPeriodicUIUpdates();
    }

    /**
     * 🔧 Activity Pause 시 모니터링 중지
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "⏸️ MainActivity onPause - 모니터링 중지");

        // 🔧 구독 상태 모니터링 중지 (리소스 절약)
        if (billingManager != null) {
            billingManager.stopSubscriptionMonitoring();
        }

        // UI 업데이트 중지
        stopPeriodicUIUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 🔧 리소스 정리
        if (billingManager != null) {
            billingManager.stopSubscriptionMonitoring();
            billingManager.setBillingStatusListener(null);
        }

        stopPeriodicUIUpdates();

        if (binding != null) {
            binding = null;
        }
    }

    /**
     * 🔧 BillingStatusListener 인터페이스 구현 - 구독 상태 변경 감지
     */
    /**
     * 🔧 BillingStatusListener 인터페이스 구현 - 구독 상태 변경 감지
     */
    @Override
    public void onPurchaseComplete() {
        runOnUiThread(() -> {
            Log.d(TAG, "✅ 구매 완료 - UI 업데이트 (조용한 모드)");

            // UI 즉시 업데이트 (메시지 표시 안함 - 조용한 상태 확인)
            updateUIBasedOnSubscription();

            // AnalysisFragment가 있다면 UI 새로고침
            Fragment analysisFragment = getSupportFragmentManager().findFragmentByTag("f1");
            if (analysisFragment instanceof AnalysisFragment) {
                ((AnalysisFragment) analysisFragment).refreshAllUIs();

                // 코인이 선택되어 있다면 다시 설정
                if (selectedCoin != null) {
                    ((AnalysisFragment) analysisFragment).updateCoin(selectedCoin, selectedExchangeType);
                }
            }

            // 🔧 수정: 구매 완료 메시지 표시하지 않음 (기존 상태 확인과 구분 불가)
            // 실제 신규 구매는 onSubscriptionStatusChanged에서 감지
        });
    }

    @Override
    public void onBillingError(String errorMessage) {
        runOnUiThread(() -> {
            Log.e(TAG, "❌ 결제 오류: " + errorMessage);
            Snackbar.make(binding.getRoot(), "구독 처리 중 오류: " + errorMessage,
                    Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onProductDetailsReceived(List<ProductDetails> productDetailsList) {
        Log.d(TAG, "✅ 구독 상품 정보 로드 완료: " + productDetailsList.size() + "개");
        // 필요한 경우 상품 정보를 UI에 반영할 수 있음
    }

    @Override
    public void onSubscriptionStatusChanged(boolean isSubscribed, boolean isAutoRenewing) {
        runOnUiThread(() -> {
            Log.d(TAG, "📱 구독 상태 변경 감지: 구독=" + isSubscribed +
                    ", 자동갱신=" + isAutoRenewing);

            // UI 즉시 업데이트
            updateUIBasedOnSubscription();

            // AnalysisFragment가 있다면 UI 새로고침
            Fragment analysisFragment = getSupportFragmentManager().findFragmentByTag("f1");
            if (analysisFragment instanceof AnalysisFragment) {
                ((AnalysisFragment) analysisFragment).refreshAllUIs();

                // 코인이 선택되어 있다면 다시 설정
                if (selectedCoin != null) {
                    ((AnalysisFragment) analysisFragment).updateCoin(selectedCoin, selectedExchangeType);
                }
            }
            // 활성 구독 상태는 메시지 표시하지 않음 (스팸 방지)
        });
    }

    /**
     * 구독 상태에 따른 UI 업데이트
     */
    private void updateUIBasedOnSubscription() {
        if (subscriptionManager == null) return;

        boolean isSubscribed = subscriptionManager.isSubscribed();
        Log.d(TAG, "🎨 UI 업데이트: 구독 상태 = " + isSubscribed);

        // AnalysisFragment UI 업데이트
        Fragment analysisFragment = getSupportFragmentManager().findFragmentByTag("f1");
        if (analysisFragment instanceof AnalysisFragment) {
            // 올바른 메서드 이름 사용 - refreshAllUIs()
            ((AnalysisFragment) analysisFragment).refreshAllUIs();
        }
    }

    /**
     * 정기적인 UI 업데이트 시작 (구독 상태 재확인 제거)
     */
    private void startPeriodicUIUpdates() {
        stopPeriodicUIUpdates(); // 기존 업데이트 중지

        subscriptionUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // UI 업데이트만 수행 (구독 상태 재확인 제거)
                updateUIBasedOnSubscription();

                // 🔧 수정: 정기적인 구독 상태 재확인 제거 (스팸 방지)
                // 구독 상태는 BillingManager의 자체 모니터링으로만 확인

                // 다음 업데이트 예약 (간격 증가)
                uiUpdateHandler.postDelayed(this, 60000); // 60초로 증가
            }
        };

        uiUpdateHandler.postDelayed(subscriptionUpdateRunnable, 10000); // 10초 후 시작
        Log.d(TAG, "⏰ 정기적인 UI 업데이트 시작 (구독 재확인 제거)");
    }

    /**
     * 정기적인 UI 업데이트 중지
     */
    private void stopPeriodicUIUpdates() {
        if (subscriptionUpdateRunnable != null) {
            uiUpdateHandler.removeCallbacks(subscriptionUpdateRunnable);
            subscriptionUpdateRunnable = null;
            Log.d(TAG, "⏹️ 정기적인 UI 업데이트 중지");
        }
    }

    @Override
    public void onCoinSelected(CoinInfo coin, ExchangeType exchangeType) {
        this.selectedCoin = coin;
        this.selectedExchangeType = exchangeType;

        // AI 분석 탭으로 이동
        binding.viewPager.setCurrentItem(1);

        // AnalysisFragment에 코인 정보 전달
        Fragment analysisFragment = getSupportFragmentManager().findFragmentByTag("f1");
        if (analysisFragment instanceof AnalysisFragment) {
            // 올바른 메서드 이름 사용 - updateCoin()
            ((AnalysisFragment) analysisFragment).updateCoin(coin, exchangeType);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // 사용자 이름 표시
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            MenuItem userNameItem = menu.findItem(R.id.action_user_name);
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                userNameItem.setTitle(displayName);
            } else {
                String email = currentUser.getEmail();
                if (email != null) {
                    String userName = email.split("@")[0];
                    userNameItem.setTitle(userName);
                }
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            // 새로고침
            Fragment analysisFragment = getSupportFragmentManager().findFragmentByTag("f1");
            if (analysisFragment instanceof AnalysisFragment) {
                ((AnalysisFragment) analysisFragment).refreshAllUIs();
            }

            Fragment coinListFragment = getSupportFragmentManager().findFragmentByTag("f0");
            if (coinListFragment instanceof CoinListFragment) {
                // 올바른 메서드 이름 사용 - refreshData()
                ((CoinListFragment) coinListFragment).refreshData();
            }

            return true;
        } else if (id == R.id.action_settings) {
            // 설정 화면으로 이동
            showThemeSettingsDialog();
            return true;
        } else if (id == R.id.action_subscription) {
            // 구독 화면으로 이동
            Intent intent = new Intent(this, SubscriptionActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_logout) {
            // 로그아웃 확인 다이얼로그
            showLogoutConfirmationDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * 로그아웃 확인 다이얼로그 표시
     */
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃 하시겠습니까?")
                .setPositiveButton("로그아웃", (dialog, which) -> performLogout())
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * 로그아웃 실행
     */
    private void performLogout() {
        // 🔧 구독 모니터링 중지
        if (billingManager != null) {
            billingManager.stopSubscriptionMonitoring();
        }

        // 로컬 구독 데이터 초기화
        if (subscriptionManager != null) {
            subscriptionManager.clearLocalSubscriptionData();
        }

        // Firebase 로그아웃
        FirebaseAuth.getInstance().signOut();

        // SharedPreferences 초기화
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().clear().apply();

        // 로그인 화면으로 이동
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * 뒤로가기 버튼 처리 설정
     */
    private void setupBackPressHandler() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.viewPager.getCurrentItem() != 0) {
                    // AI 분석 탭에서 코인 목록 탭으로 이동
                    binding.viewPager.setCurrentItem(0);
                } else {
                    // 앱 종료 확인
                    showExitConfirmationDialog();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    /**
     * 앱 종료 확인 다이얼로그
     */
    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("앱 종료")
                .setMessage("앱을 종료하시겠습니까?")
                .setPositiveButton("종료", (dialog, which) -> {
                    // 🔧 리소스 정리 후 종료
                    if (billingManager != null) {
                        billingManager.stopSubscriptionMonitoring();
                    }
                    stopPeriodicUIUpdates();
                    finish();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /**
     * ViewPager2 어댑터
     */
    private static class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new CoinListFragment();
                case 1:
                    return new AnalysisFragment();
                default:
                    return new CoinListFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    /**
     * 다크 모드 활성화 여부 확인
     */
    private boolean isDarkModeEnabled() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_DARK_MODE, true); // 기본값은 true (다크 모드)
    }

    /**
     * 테마 상태를 설정
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

        // 앱 재시작
        Intent intent = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            overridePendingTransition(0, 0);
        }
    }

    /**
     * 현재 코인 정보 저장
     */
    private void saveCurrentCoinInfo() {
        if (selectedCoin != null) {
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("SELECTED_COIN_MARKET", selectedCoin.getMarket());
            editor.putString("SELECTED_COIN_SYMBOL", selectedCoin.getSymbol());
            editor.putString("SELECTED_COIN_NAME", selectedCoin.getDisplayName());
            editor.apply();
        }
    }

    /**
     * 선택된 코인 정보 복원
     */
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
     * 테마 설정 다이얼로그 표시
     */
    private void showThemeSettingsDialog() {
        boolean isDarkMode = isDarkModeEnabled();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("앱 테마 설정");

        String[] themes = {"라이트 모드", "다크 모드"};
        int checkedItem = isDarkMode ? 1 : 0;

        builder.setSingleChoiceItems(themes, checkedItem, (dialog, which) -> {
            boolean enableDarkMode = (which == 1);
            setDarkMode(enableDarkMode);
            dialog.dismiss();
        });

        builder.setNegativeButton("취소", null);
        builder.show();
    }

    /**
     * 현재 선택된 코인 정보 반환
     */
    public CoinInfo getSelectedCoin() {
        return selectedCoin;
    }

    /**
     * 현재 선택된 거래소 타입 반환
     */
    public ExchangeType getSelectedExchangeType() {
        return selectedExchangeType;
    }

    /**
     * 코인 목록 탭으로 이동
     */
    public void navigateToCoinsTab() {
        if (binding != null) {
            binding.viewPager.setCurrentItem(0);
        }
    }
}