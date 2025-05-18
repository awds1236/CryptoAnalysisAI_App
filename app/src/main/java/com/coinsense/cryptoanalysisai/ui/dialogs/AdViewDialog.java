package com.coinsense.cryptoanalysisai.ui.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.coinsense.cryptoanalysisai.MainActivity;
import com.coinsense.cryptoanalysisai.R;
import com.coinsense.cryptoanalysisai.services.AdManager;
import com.coinsense.cryptoanalysisai.ui.activities.SubscriptionActivity;
import com.coinsense.cryptoanalysisai.ui.fragments.AnalysisFragment;

public class AdViewDialog extends DialogFragment {

    private static final String ARG_COIN_SYMBOL = "coin_symbol";
    private static final String ARG_COIN_NAME = "coin_name";

    private String coinSymbol;
    private String coinName;
    private AdManager adManager;
    private AdCompletionListener completionListener;

    // 뷰 참조
    private TextView tvAdTitle;
    private TextView tvAdDescription;
    private ProgressBar progressBar;
    private Button btnCancel;
    private Button btnSubscribe;

    public static AdViewDialog newInstance(String coinSymbol, String coinName) {
        AdViewDialog dialog = new AdViewDialog();
        Bundle args = new Bundle();
        args.putString(ARG_COIN_SYMBOL, coinSymbol);
        args.putString(ARG_COIN_NAME, coinName);
        dialog.setArguments(args);
        return dialog;
    }

    public void setCompletionListener(AdCompletionListener listener) {
        this.completionListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            coinSymbol = getArguments().getString(ARG_COIN_SYMBOL);
            coinName = getArguments().getString(ARG_COIN_NAME);
        }

        adManager = AdManager.getInstance(requireContext());

        // 전체화면 대화상자 스타일 사용
        setStyle(DialogFragment.STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ad_view_dialog, container, false);

        // 뷰 초기화
        tvAdTitle = view.findViewById(R.id.tvAdTitle);
        tvAdDescription = view.findViewById(R.id.tvAdDescription);
        progressBar = view.findViewById(R.id.progressBar);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnSubscribe = view.findViewById(R.id.btnSubscribe);

        // 코인 정보 설정 - 리소스 이용
        if (coinName != null && !coinName.isEmpty()) {
            tvAdTitle.setText(getString(R.string.ad_title_format, coinName));
            tvAdDescription.setText(getString(R.string.ad_description_format, coinName));
        }

        // 버튼 이벤트 설정
        btnCancel.setOnClickListener(v -> dismiss());

        btnSubscribe.setOnClickListener(v -> {
            // 구독 화면으로 이동
            Intent intent = new Intent(getActivity(), SubscriptionActivity.class);
            startActivity(intent);
            dismiss();
        });

        // 광고 표시
        showAd();

        return view;
    }

    private void showAd() {
        if (getActivity() == null) return;

        // 광고 로드 및 표시
        adManager.showAd(getActivity(), coinSymbol, new AdManager.OnAdCompletedListener() {
            @Override
            public void onAdCompleted(String coinSymbol) {
                // 광고 시청 완료
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Log.d("AdViewDialog", getString(R.string.ad_view_completed_log, coinSymbol));

                        if (completionListener != null) {
                            completionListener.onAdCompleted(coinSymbol);
                        }

                        // MainActivity를 찾아 모든 프래그먼트 업데이트
                        if (getActivity() instanceof MainActivity) {
                            MainActivity mainActivity = (MainActivity) getActivity();

                            // 현재 보이는 AnalysisFragment 찾기
                            Fragment fragment = mainActivity.getSupportFragmentManager().findFragmentByTag("f1");
                            if (fragment instanceof AnalysisFragment) {
                                AnalysisFragment analysisFragment = (AnalysisFragment) fragment;
                                analysisFragment.refreshAllUIs();

                                // 콘텐츠 새로고침을 위해 분석 결과 재로드
                                analysisFragment.loadAnalysisFromApi();
                            }
                        }

                        dismiss();
                    });
                }
            }

            @Override
            public void onAdFailed(String message) {
                // 광고 표시 실패
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                        dismiss();
                    });
                }
            }
        });
    }

    // 광고 완료 콜백 인터페이스
    public interface AdCompletionListener {
        void onAdCompleted(String coinSymbol);
    }
}