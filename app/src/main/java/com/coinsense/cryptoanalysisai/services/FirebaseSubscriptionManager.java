package com.coinsense.cryptoanalysisai.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.coinsense.cryptoanalysisai.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase 실시간 데이터베이스를 사용하여 구독 정보를 관리하는 클래스
 */
public class FirebaseSubscriptionManager {
    private static final String TAG = "FirebaseSubscription";
    private static FirebaseSubscriptionManager instance;

    private final FirebaseDatabase database;
    private final DatabaseReference subscriptionsRef;
    private DatabaseReference userSubscriptionRef;

    private FirebaseUser currentUser;
    private SubscriptionData cachedSubscriptionData;
    private ValueEventListener subscriptionListener;

    // 구독 데이터 변경 리스너 인터페이스
    public interface OnSubscriptionDataChangeListener {
        void onSubscriptionDataChanged(SubscriptionData subscriptionData);
        void onError(String errorMessage);
    }

    // 구독 데이터 모델 클래스
    public static class SubscriptionData {
        private String subscriptionType = Constants.SUBSCRIPTION_NONE;
        private long expiryTimestamp = 0;
        private long startTimestamp = 0;
        private boolean autoRenewing = false;
        private String monthlyPrice = "월 ₩15,000";
        private String yearlyPrice = "연 ₩125,000";

        // 하나의 필드만 유지하고 나머지는 제거
        private boolean subscribed = false;  // 이 필드만 유지
        private boolean isCancelled = false;
        private long lastUpdated = 0;

        public SubscriptionData() {
            // Firebase 필수 기본 생성자
        }

        public String getSubscriptionType() {
            return subscriptionType;
        }

        public void setSubscriptionType(String subscriptionType) {
            this.subscriptionType = subscriptionType;
        }

        public long getExpiryTimestamp() {
            return expiryTimestamp;
        }

        public void setExpiryTimestamp(long expiryTimestamp) {
            this.expiryTimestamp = expiryTimestamp;
        }

        public long getStartTimestamp() {
            return startTimestamp;
        }

        public void setStartTimestamp(long startTimestamp) {
            this.startTimestamp = startTimestamp;
        }

        public boolean isAutoRenewing() {
            return autoRenewing;
        }

        public void setAutoRenewing(boolean autoRenewing) {
            this.autoRenewing = autoRenewing;
        }

        public String getMonthlyPrice() {
            return monthlyPrice;
        }

        public void setMonthlyPrice(String monthlyPrice) {
            this.monthlyPrice = monthlyPrice;
        }

        public String getYearlyPrice() {
            return yearlyPrice;
        }

        public void setYearlyPrice(String yearlyPrice) {
            this.yearlyPrice = yearlyPrice;
        }

        // subscribed 필드의 명확한 getter/setter
        public boolean isSubscribed() {
            return subscribed ||
                    (!Constants.SUBSCRIPTION_NONE.equals(subscriptionType)
                            && expiryTimestamp > System.currentTimeMillis());
        }

        public void setSubscribed(boolean subscribed) {
            this.subscribed = subscribed;
        }

        public boolean isCancelled() {
            return isCancelled;
        }

        public void setIsCancelled(boolean isCancelled) {
            this.isCancelled = isCancelled;
        }

        public long getLastUpdated() {
            return lastUpdated;
        }

        public void setLastUpdated(long lastUpdated) {
            this.lastUpdated = lastUpdated;
        }
    }

    private FirebaseSubscriptionManager() {
        database = FirebaseDatabase.getInstance();
        subscriptionsRef = database.getReference("subscriptions");
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            userSubscriptionRef = subscriptionsRef.child(currentUser.getUid());
        }
    }

    public static synchronized FirebaseSubscriptionManager getInstance() {
        if (instance == null) {
            instance = new FirebaseSubscriptionManager();
        }
        return instance;
    }

    /**
     * 현재 로그인한 사용자 설정
     */
    public void setCurrentUser(FirebaseUser user) {
        this.currentUser = user;

        // 이전 리스너 제거
        if (userSubscriptionRef != null && subscriptionListener != null) {
            userSubscriptionRef.removeEventListener(subscriptionListener);
            subscriptionListener = null;
        }

        // 캐시 데이터 확실히 초기화
        cachedSubscriptionData = null;

        // 새 사용자의 구독 참조 설정
        if (user != null) {
            userSubscriptionRef = subscriptionsRef.child(user.getUid());
            // 새 사용자의 구독 정보 즉시 로드
            getSubscriptionData(null);
        } else {
            userSubscriptionRef = null;
        }
    }

    /**
     * 캐시된 데이터 및 리스너 초기화
     * 로그아웃이나 계정 전환 시 호출됨
     */
    public void clearCachedData() {
        // 캐시된 구독 데이터 초기화
        cachedSubscriptionData = null;

        // 리스너도 초기화
        if (subscriptionListener != null && userSubscriptionRef != null) {
            userSubscriptionRef.removeEventListener(subscriptionListener);
            subscriptionListener = null;
        }

        Log.d(TAG, "구독 정보 캐시 및 리스너가 초기화되었습니다.");
    }

    /**
     * 구독 데이터 가져오기 (비동기)
     */
    public void getSubscriptionData(final OnSubscriptionDataChangeListener listener) {
        if (currentUser == null || userSubscriptionRef == null) {
            if (listener != null) {
                listener.onError("사용자가 로그인되어 있지 않습니다");
            }
            return;
        }

        // 구독 데이터 변경 리스너 설정
        if (subscriptionListener == null) {
            subscriptionListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        SubscriptionData subscriptionData = dataSnapshot.getValue(SubscriptionData.class);

                        if (subscriptionData != null) {
                            // 만료 여부 확인
                            if (subscriptionData.expiryTimestamp > 0 &&
                                    subscriptionData.expiryTimestamp < System.currentTimeMillis()) {
                                // 만료된 구독 처리
                                if (!Constants.SUBSCRIPTION_NONE.equals(subscriptionData.subscriptionType) &&
                                        !subscriptionData.autoRenewing) {
                                    subscriptionData.subscriptionType = Constants.SUBSCRIPTION_NONE;
                                    updateSubscriptionData(subscriptionData, null);
                                }
                            }

                            cachedSubscriptionData = subscriptionData;

                            if (listener != null) {
                                listener.onSubscriptionDataChanged(subscriptionData);
                            }
                        } else if (listener != null) {
                            listener.onError("구독 데이터를 파싱할 수 없습니다");
                        }
                    } else {
                        // 데이터가 없는 경우 초기 데이터 생성
                        SubscriptionData newData = new SubscriptionData();
                        updateSubscriptionData(newData, listener);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.e(TAG, "구독 데이터 로드 실패: " + databaseError.getMessage());
                    if (listener != null) {
                        listener.onError("구독 데이터를 가져오는 중 오류가 발생했습니다: " +
                                databaseError.getMessage());
                    }
                }
            };

            userSubscriptionRef.addValueEventListener(subscriptionListener);
        } else if (cachedSubscriptionData != null && listener != null) {
            // 캐시된 데이터가 있으면 즉시 반환
            listener.onSubscriptionDataChanged(cachedSubscriptionData);
        }
    }

    /**
     * 구독 정보 업데이트
     */
    public void updateSubscriptionData(SubscriptionData subscriptionData,
                                       final OnSubscriptionDataChangeListener listener) {
        if (currentUser == null || userSubscriptionRef == null) {
            if (listener != null) {
                listener.onError("사용자가 로그인되어 있지 않습니다");
            }
            return;
        }

        if (subscriptionData == null) {
            if (listener != null) {
                listener.onError("업데이트할 구독 데이터가 없습니다");
            }
            return;
        }

        // 데이터베이스에 저장할 값들만 Map으로 구성 (가격 정보 제외)
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("subscriptionType", subscriptionData.getSubscriptionType());
        updateData.put("expiryTimestamp", subscriptionData.getExpiryTimestamp());
        updateData.put("startTimestamp", subscriptionData.getStartTimestamp());
        updateData.put("autoRenewing", subscriptionData.isAutoRenewing());

        // 구독 상태 - 양쪽 필드 모두 업데이트
        updateData.put("subscribed", subscriptionData.isSubscribed());
        updateData.put("isSubscribed", subscriptionData.isSubscribed());

        // 취소 상태 - 양쪽 필드 모두 업데이트
        updateData.put("cancelled", subscriptionData.isCancelled());
        updateData.put("isCancelled", subscriptionData.isCancelled());

        // 업데이트 시간
        updateData.put("lastUpdated", System.currentTimeMillis());

        // 가격 정보는 데이터베이스에 저장하지 않음
        // (단, 로컬 캐시에는 유지하기 위해 subscriptionData 객체는 수정하지 않음)

        userSubscriptionRef.updateChildren(updateData)
                .addOnSuccessListener(aVoid -> {
                    cachedSubscriptionData = subscriptionData;
                    if (listener != null) {
                        listener.onSubscriptionDataChanged(subscriptionData);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "구독 데이터 업데이트 실패: " + e.getMessage());
                    if (listener != null) {
                        listener.onError("구독 정보 업데이트에 실패했습니다: " + e.getMessage());
                    }
                });
    }

    /**
     * 구독 상태 설정
     */
    public void setSubscribed(boolean subscribed, long expiryTimestamp, String subscriptionType,
                              final OnSubscriptionDataChangeListener listener) {
        if (currentUser == null || userSubscriptionRef == null) {
            if (listener != null) {
                listener.onError("사용자가 로그인되어 있지 않습니다");
            }
            return;
        }

        // 새 구독 데이터 생성
        SubscriptionData newData = new SubscriptionData();
        newData.subscriptionType = subscribed ? subscriptionType : Constants.SUBSCRIPTION_NONE;
        newData.expiryTimestamp = expiryTimestamp;
        newData.startTimestamp = System.currentTimeMillis();
        newData.autoRenewing = true;
        newData.subscribed = subscribed;
        newData.lastUpdated = System.currentTimeMillis();

        // 기존 데이터가 있으면 일부 정보 유지
        if (cachedSubscriptionData != null) {
            newData.monthlyPrice = cachedSubscriptionData.monthlyPrice;
            newData.yearlyPrice = cachedSubscriptionData.yearlyPrice;

            // 신규 구독이 아닌 경우 시작 시간 유지
            if (subscribed &&
                    !Constants.SUBSCRIPTION_NONE.equals(cachedSubscriptionData.subscriptionType)) {
                newData.startTimestamp = cachedSubscriptionData.startTimestamp;
            }
        }

        // 변경사항 저장
        updateSubscriptionData(newData, listener);
    }

    /**
     * 구독 가격 정보 설정
     */
    public void setSubscriptionPrices(String monthlyPrice, String yearlyPrice,
                                      final OnSubscriptionDataChangeListener listener) {
        if (currentUser == null || userSubscriptionRef == null) {
            if (listener != null) {
                listener.onError("사용자가 로그인되어 있지 않습니다");
            }
            return;
        }

        // 현재 구독 데이터 복제 후 가격 정보만 업데이트
        SubscriptionData updatedData;
        if (cachedSubscriptionData != null) {
            updatedData = cachedSubscriptionData;
            updatedData.monthlyPrice = monthlyPrice;
            updatedData.yearlyPrice = yearlyPrice;
        } else {
            // 기존 데이터가 없으면 새로 생성
            updatedData = new SubscriptionData();
            updatedData.monthlyPrice = monthlyPrice;
            updatedData.yearlyPrice = yearlyPrice;
        }

        // 변경사항 저장 (단, 가격 정보는 Firebase에는 저장하지 않음 - updateSubscriptionData에서 처리)
        updateSubscriptionData(updatedData, listener);
    }

    /**
     * 자동 갱신 상태 설정
     */
    public void setAutoRenewing(boolean autoRenewing, final OnSubscriptionDataChangeListener listener) {
        if (currentUser == null || userSubscriptionRef == null) {
            if (listener != null) {
                listener.onError("사용자가 로그인되어 있지 않습니다");
            }
            return;
        }

        // 현재 구독 데이터 복제 후 자동 갱신 상태만 업데이트
        if (cachedSubscriptionData != null) {
            cachedSubscriptionData.autoRenewing = autoRenewing;
            updateSubscriptionData(cachedSubscriptionData, listener);
        } else {
            // 데이터가 없으면 오류로 처리 (요구사항: 데이터가 있을 때만 작동)
            if (listener != null) {
                listener.onError("업데이트할 구독 데이터가 없습니다");
            }
        }
    }

    /**
     * 구독 취소 처리
     */
    public void cancelSubscription(final OnSubscriptionDataChangeListener listener) {
        if (currentUser == null || userSubscriptionRef == null) {
            if (listener != null) {
                listener.onError("사용자가 로그인되어 있지 않습니다");
            }
            return;
        }

        // 현재 구독 데이터가 있는 경우에만 취소 처리
        if (cachedSubscriptionData != null) {
            cachedSubscriptionData.subscriptionType = Constants.SUBSCRIPTION_NONE;
            cachedSubscriptionData.expiryTimestamp = 0;
            cachedSubscriptionData.autoRenewing = false;
            cachedSubscriptionData.subscribed = false;
            cachedSubscriptionData.isCancelled = true;
            cachedSubscriptionData.lastUpdated = System.currentTimeMillis();

            updateSubscriptionData(cachedSubscriptionData, listener);
        } else {
            // 데이터가 없으면 오류로 처리 (요구사항: 데이터가 있을 때만 작동)
            if (listener != null) {
                listener.onError("취소할 구독 데이터가 없습니다");
            }
        }
    }

    /**
     * 캐시된 구독 데이터 가져오기 (동기)
     */
    public SubscriptionData getCachedSubscriptionData() {
        return cachedSubscriptionData;
    }

    /**
     * 구독 여부 확인 (동기)
     * 데이터가 있을 때만 true 반환
     */
    public boolean isSubscribed() {
        if (cachedSubscriptionData == null) {
            return false;
        }

        return cachedSubscriptionData.isSubscribed();
    }
}