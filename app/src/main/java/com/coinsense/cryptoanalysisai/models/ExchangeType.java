package com.coinsense.cryptoanalysisai.models;

import android.content.Context;

import com.coinsense.cryptoanalysisai.R;

public enum ExchangeType {
    UPBIT("upbit", R.string.upbit),
    BINANCE("binance", R.string.binance);

    private final String code;
    private final int displayNameResId;

    ExchangeType(String code, int displayNameResId) {
        this.code = code;
        this.displayNameResId = displayNameResId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName(Context context) {
        return context.getString(displayNameResId);
    }

    public int getDisplayNameResId() {
        return displayNameResId;
    }

    public static ExchangeType fromCode(String code) {
        for (ExchangeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return UPBIT; // 기본값
    }
}