package com.coinsense.cryptoanalysisai.utils;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.coinsense.cryptoanalysisai.R;

public class TermsDialogUtils {

    /**
     * 이용약관 다이얼로그 표시
     */
    public static void showTermsOfServiceDialog(Context context) {
        showTermsDialog(context,
                context.getString(R.string.terms_of_service_title),
                TermsContent.getTermsOfService(context));
    }

    /**
     * 개인정보처리방침 다이얼로그 표시
     */
    public static void showPrivacyPolicyDialog(Context context) {
        showTermsDialog(context,
                context.getString(R.string.privacy_policy_title),
                TermsContent.getPrivacyPolicy(context));
    }

    /**
     * 공통 다이얼로그 표시 메서드
     */
    private static void showTermsDialog(Context context, String title, String content) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_terms, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
        TextView tvContent = dialogView.findViewById(R.id.tvContent);
        Button btnOk = dialogView.findViewById(R.id.btnOk);

        tvTitle.setText(title);
        tvContent.setText(content);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnOk.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}