package com.example.cryptoanalysisai.models;

// 이 클래스는 더 이상 실제 모델 클래스를 포함하지 않고 필요에 따라 유틸리티나 상수를 정의할 수 있습니다
public class ClaudeModels {
    // Claude 모델 상수
    public static final String CLAUDE_3_LATEST = "claude-3-7-sonnet-latest";
    public static final String CLAUDE_3_SONNET = "claude-3-sonnet-20240229";
    public static final String CLAUDE_3_OPUS = "claude-3-opus-20240229";

    // 기본 토큰 설정
    public static final int DEFAULT_MAX_TOKENS = 4000;

    // 역할 타입
    public static class Roles {
        public static final String USER = "user";
        public static final String ASSISTANT = "assistant";
    }
}