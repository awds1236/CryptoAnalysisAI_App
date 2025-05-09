package com.example.cryptoanalysisai.models;

public enum ExchangeType {
    UPBIT("upbit", "업비트"),
    BINANCE("binance", "바이낸스");

    private final String code;
    private final String displayName;

    ExchangeType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
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
