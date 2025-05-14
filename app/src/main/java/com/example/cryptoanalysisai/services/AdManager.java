package com.example.cryptoanalysisai.services;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.cryptoanalysisai.utils.Constants;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AdManager {
    private static final String TAG = "AdManager";
    private static final String PREF_AD_STATUS = "ad_status_prefs";
    private static final long AD_VIEW_DURATION = 10 * 60 * 1000; // 10분 (밀리초)

    private static AdManager instance;
    private final Context context;
    private RewardedAd rewardedAd;
    private boolean isAdLoading = false;

    // 코인별 광고 만료 시간 저장용 맵 (메모리 캐시)
    private final Map<String, Long> coinExpiryTimes = new HashMap<>();

    private AdManager(Context context) {
        this.context = context.getApplicationContext();
        // AdMob 초기화
        MobileAds.initialize(context, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(@NonNull InitializationStatus initializationStatus) {
                Log.d(TAG, "AdMob 초기화 완료");
                // 초기화 후 광고 미리 로드
                preloadAd();
            }
        });

        // 저장된 광고 만료 시간 불러오기
        loadSavedExpiryTimes();
    }

    public static synchronized AdManager getInstance(Context context) {
        if (instance == null) {
            instance = new AdManager(context);
        }
        return instance;
    }

    // 광고 미리 로드
    public void preloadAd() {
        if (isAdLoading || rewardedAd != null) return;

        isAdLoading = true;

        // 테스트 광고 ID (실제 앱에서는 실제 광고 ID로 변경 필요)
        String adUnitId = "ca-app-pub-3940256099942544/5224354917";

        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(context, adUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                Log.e(TAG, "광고 로드 실패: " + loadAdError.getMessage());
                rewardedAd = null;
                isAdLoading = false;
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                Log.d(TAG, "광고 로드 성공");
                AdManager.this.rewardedAd = rewardedAd;
                isAdLoading = false;
            }
        });
    }

    // 광고 표시 및 보상 처리
    public void showAd(Activity activity, String coinSymbol, OnAdCompletedListener listener) {
        // 이미 구독 중이면 광고 없이 바로 접근 허용
        if (SubscriptionManager.getInstance(context).isSubscribed()) {
            if (listener != null) {
                listener.onAdCompleted(coinSymbol);
            }
            return;
        }

        // 이미 광고를 봐서 접근 가능한 상태인지 체크
        if (hasActiveAdPermission(coinSymbol)) {
            if (listener != null) {
                listener.onAdCompleted(coinSymbol);
            }
            return;
        }

        // 광고가 로드되지 않았으면 로드 먼저 시도
        if (rewardedAd == null) {
            Log.d(TAG, "광고가 준비되지 않았습니다. 로딩 중...");
            preloadAd();
            // 로딩 중 메시지 전달
            if (listener != null) {
                listener.onAdFailed("광고를 불러오는 중입니다. 잠시 후 다시 시도해주세요.");
            }
            return;
        }

        // 광고 콜백 설정
        rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                Log.d(TAG, "광고가 닫혔습니다.");
                rewardedAd = null;
                // 다음 광고 미리 로드
                preloadAd();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                Log.e(TAG, "광고 표시 실패: " + adError.getMessage());
                rewardedAd = null;
                if (listener != null) {
                    listener.onAdFailed("광고 표시 중 오류가 발생했습니다.");
                }
                // 다음 광고 미리 로드
                preloadAd();
            }
        });

        // 광고 보여주기
        rewardedAd.show(activity, new OnUserEarnedRewardListener() {
            @Override
            public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                Log.d(TAG, "사용자가 보상을 받았습니다: " + rewardItem.getAmount());
                // 해당 코인에 대한 접근 권한 부여
                grantAdPermission(coinSymbol);
                if (listener != null) {
                    listener.onAdCompleted(coinSymbol);
                }
            }
        });
    }

    // 코인에 대한 광고 보상 접근 권한 확인
    public boolean hasActiveAdPermission(String coinSymbol) {
        if (coinSymbol == null || coinSymbol.isEmpty()) return false;

        // 구독자는 항상 접근 가능
        if (SubscriptionManager.getInstance(context).isSubscribed()) {
            return true;
        }

        // 코인별 만료 시간 확인
        Long expiryTime = coinExpiryTimes.get(coinSymbol);
        if (expiryTime == null) {
            // 캐시에 없으면 SharedPreferences에서 확인
            SharedPreferences prefs = context.getSharedPreferences(PREF_AD_STATUS, Context.MODE_PRIVATE);
            expiryTime = prefs.getLong(coinSymbol, 0);
            // 캐시에 저장
            coinExpiryTimes.put(coinSymbol, expiryTime);
        }

        // 현재 시간보다 만료 시간이 더 크면 접근 가능
        return System.currentTimeMillis() < expiryTime;
    }

    // 남은 시간 확인 (분 단위)
    public int getRemainingMinutes(String coinSymbol) {
        if (!hasActiveAdPermission(coinSymbol)) return 0;

        Long expiryTime = coinExpiryTimes.get(coinSymbol);
        if (expiryTime == null) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_AD_STATUS, Context.MODE_PRIVATE);
            expiryTime = prefs.getLong(coinSymbol, 0);
        }

        long remainingMillis = expiryTime - System.currentTimeMillis();
        return Math.max(0, (int) (remainingMillis / 1000 / 60));
    }

    // 광고 시청 후 접근 권한 부여
    private void grantAdPermission(String coinSymbol) {
        if (coinSymbol == null || coinSymbol.isEmpty()) return;

        // 현재 시간 + 10분 계산
        long expiryTime = System.currentTimeMillis() + AD_VIEW_DURATION;

        // 메모리 캐시 업데이트
        coinExpiryTimes.put(coinSymbol, expiryTime);

        // SharedPreferences에 저장
        SharedPreferences prefs = context.getSharedPreferences(PREF_AD_STATUS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(coinSymbol, expiryTime);
        editor.apply();

        Log.d(TAG, "코인 " + coinSymbol + "에 대한 접근 권한 부여 완료. 만료 시간: " + expiryTime);
    }

    // 저장된 만료 시간 불러오기
    private void loadSavedExpiryTimes() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_AD_STATUS, Context.MODE_PRIVATE);
        Map<String, ?> allPrefs = prefs.getAll();

        coinExpiryTimes.clear();
        for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
            if (entry.getValue() instanceof Long) {
                coinExpiryTimes.put(entry.getKey(), (Long) entry.getValue());
            }
        }
    }

    // 모든 코인의 만료 시간 초기화 (테스트용)
    public void resetAllPermissions() {
        coinExpiryTimes.clear();
        SharedPreferences prefs = context.getSharedPreferences(PREF_AD_STATUS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
    }

    // 광고 관련 결과를 전달하기 위한 리스너 인터페이스
    public interface OnAdCompletedListener {
        void onAdCompleted(String coinSymbol);
        void onAdFailed(String message);
    }
}