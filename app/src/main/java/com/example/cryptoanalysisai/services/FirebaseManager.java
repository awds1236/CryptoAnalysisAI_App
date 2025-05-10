package com.example.cryptoanalysisai.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.cryptoanalysisai.models.AnalysisResult;
import com.example.cryptoanalysisai.models.CoinInfo;
import com.example.cryptoanalysisai.models.ExchangeType;
import com.example.cryptoanalysisai.models.firebase.FirestoreAnalysisResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirebaseManager {
    private static final String TAG = "FirebaseManager";

    private static final String ANALYSES_COLLECTION = "analyses";
    private static final String HOURLY_ANALYSES_COLLECTION = "analyses_hourly";
    private static final String COINS_COLLECTION = "coins";

    private FirebaseFirestore db;
    private static FirebaseManager instance;

    private boolean isFirebaseAvailable = true;

    private FirebaseManager() {
        try {
            db = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "FirebaseFirestore 초기화 실패: " + e.getMessage());
            isFirebaseAvailable = false;
        }
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    /**
     * 분석 결과를 Firestore에 저장
     */
    public void saveAnalysisResult(AnalysisResult result, CoinInfo coinInfo,
                                   ExchangeType exchangeType, OnAnalysisSavedListener listener) {
        if (result == null || coinInfo == null) {
            if (listener != null) {
                listener.onFailure("분석 결과 또는 코인 정보가 null입니다.");
            }
            return;
        }

        // Firebase 모델로 변환
        FirestoreAnalysisResult firestoreResult = FirestoreAnalysisResult.fromAnalysisResult(
                result,
                coinInfo.getSymbol(),
                coinInfo.getDisplayName(),
                coinInfo.getMarket(),
                exchangeType.getCode());

        // 현재 가격 정보 설정
        firestoreResult.setCurrentPrice(coinInfo.getCurrentPrice());

        // 문서 ID 생성 (코인심볼_거래소_타임스탬프)
        String docId = coinInfo.getSymbol() + "_" + exchangeType.getCode() + "_" + System.currentTimeMillis();

        // Firestore에 저장
        db.collection(ANALYSES_COLLECTION)
                .document(docId)
                .set(firestoreResult)
                .addOnSuccessListener(aVoid -> {
                    // 시간별 요약 저장 호출
                    saveHourlyAnalysisSummary(firestoreResult);

                    if (listener != null) {
                        listener.onSuccess(docId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "분석 결과 저장 실패: " + e.getMessage());
                    if (listener != null) {
                        listener.onFailure("분석 결과 저장 실패: " + e.getMessage());
                    }
                });
    }

    /**
     * 시간별 분석 요약 저장 (1시간마다 저장되는 간략한 분석 정보)
     */
    private void saveHourlyAnalysisSummary(FirestoreAnalysisResult result) {
        if (result == null) return;

        // 현재 시간을 시간 단위로 라운딩 (1시간마다 1개 저장하기 위함)
        long hourTimestamp = (result.getTimestamp() / 3600000) * 3600000;
        String hourlyDocId = result.getCoinSymbol() + "_" + result.getExchange() + "_" + hourTimestamp;

        db.collection(HOURLY_ANALYSES_COLLECTION)
                .document(hourlyDocId)
                .set(result)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "시간별 분석 저장 성공"))
                .addOnFailureListener(e -> Log.e(TAG, "시간별 분석 저장 실패: " + e.getMessage()));
    }

    /**
     * 특정 코인의 최신 분석 결과 가져오기
     */
    public void getLatestAnalysis(String coinSymbol, String exchange, OnAnalysisRetrievedListener listener) {
        db.collection(ANALYSES_COLLECTION)
                .whereEqualTo("coinSymbol", coinSymbol)
                .whereEqualTo("exchange", exchange)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot document = task.getResult().getDocuments().get(0);
                        FirestoreAnalysisResult result = document.toObject(FirestoreAnalysisResult.class);

                        if (result != null) {
                            result.setDocumentId(document.getId());
                            listener.onAnalysisRetrieved(result);
                        } else {
                            listener.onNoAnalysisFound();
                        }
                    } else {
                        listener.onNoAnalysisFound();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "최신 분석 조회 실패: " + e.getMessage());
                    listener.onFailure("분석 결과 조회 실패: " + e.getMessage());
                });
    }

    /**
     * 특정 코인의 분석 이력 가져오기
     */
    public void getAnalysisHistory(String coinSymbol, String exchange, int limit,
                                   OnAnalysisHistoryRetrievedListener listener) {
        db.collection(HOURLY_ANALYSES_COLLECTION)
                .whereEqualTo("coinSymbol", coinSymbol)
                .whereEqualTo("exchange", exchange)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<FirestoreAnalysisResult> resultList = new ArrayList<>();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            FirestoreAnalysisResult result = document.toObject(FirestoreAnalysisResult.class);
                            result.setDocumentId(document.getId());
                            resultList.add(result);
                        }

                        listener.onHistoryRetrieved(resultList);
                    } else {
                        listener.onFailure("분석 이력 조회 실패");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "분석 이력 조회 실패: " + e.getMessage());
                    listener.onFailure("분석 이력 조회 실패: " + e.getMessage());
                });
    }

    /**
     * 모든 코인의 최신 분석 결과 가져오기
     */
    public void getAllLatestAnalyses(OnAllAnalysesRetrievedListener listener) {
        db.collection(HOURLY_ANALYSES_COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<FirestoreAnalysisResult> resultList = new ArrayList<>();

                        // 마지막으로 조회된 코인+거래소 조합을 추적하여 중복 제거
                        List<String> processedCombinations = new ArrayList<>();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            FirestoreAnalysisResult result = document.toObject(FirestoreAnalysisResult.class);
                            String combination = result.getCoinSymbol() + "_" + result.getExchange();

                            if (!processedCombinations.contains(combination)) {
                                processedCombinations.add(combination);
                                result.setDocumentId(document.getId());
                                resultList.add(result);
                            }
                        }

                        listener.onAllAnalysesRetrieved(resultList);
                    } else {
                        listener.onFailure("모든 분석 결과 조회 실패");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "모든 분석 결과 조회 실패: " + e.getMessage());
                    listener.onFailure("모든 분석 결과 조회 실패: " + e.getMessage());
                });
    }

    // 리스너 인터페이스 정의
    public interface OnAnalysisSavedListener {
        void onSuccess(String documentId);
        void onFailure(String errorMessage);
    }

    public interface OnAnalysisRetrievedListener {
        void onAnalysisRetrieved(FirestoreAnalysisResult result);
        void onNoAnalysisFound();
        void onFailure(String errorMessage);
    }

    /**
     * Firebase 사용 가능 여부 설정
     */
    public void setFirebaseAvailable(boolean available) {
        isFirebaseAvailable = available;

        if (!available) {
            Log.d(TAG, "Firebase 사용 불가능 모드로 전환");
        }
    }

    public interface OnAnalysisHistoryRetrievedListener {
        void onHistoryRetrieved(List<FirestoreAnalysisResult> resultList);
        void onFailure(String errorMessage);
    }

    public interface OnAllAnalysesRetrievedListener {
        void onAllAnalysesRetrieved(List<FirestoreAnalysisResult> resultList);
        void onFailure(String errorMessage);
    }
}