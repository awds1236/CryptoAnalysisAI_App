package com.example.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ClaudeResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("type")
    private String type;

    @SerializedName("role")
    private String role;

    @SerializedName("content")
    private List<ContentBlock> content;

    @SerializedName("model")
    private String model;

    @SerializedName("stop_reason")
    private String stopReason;

    @SerializedName("stop_sequence")
    private String stopSequence;

    @SerializedName("usage")
    private Usage usage;

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getRole() {
        return role;
    }

    public List<ContentBlock> getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public String getStopReason() {
        return stopReason;
    }

    public String getStopSequence() {
        return stopSequence;
    }

    public Usage getUsage() {
        return usage;
    }

    // 응답 텍스트 얻기
    public String getText() {
        if (content != null && !content.isEmpty()) {
            return content.get(0).getText();
        }
        return "";
    }

    public static class ContentBlock {
        @SerializedName("type")
        private String type;

        @SerializedName("text")
        private String text;

        public String getType() {
            return type;
        }

        public String getText() {
            return text;
        }
    }

    public static class Usage {
        @SerializedName("input_tokens")
        private int inputTokens;

        @SerializedName("output_tokens")
        private int outputTokens;

        public int getInputTokens() {
            return inputTokens;
        }

        public int getOutputTokens() {
            return outputTokens;
        }
    }
}