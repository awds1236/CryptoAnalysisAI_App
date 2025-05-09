package com.example.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClaudeModels {
}

// Claude API 요청 클래스
class ClaudeRequest {
    @SerializedName("model")
    private String model;

    @SerializedName("max_tokens")
    private int maxTokens;

    @SerializedName("messages")
    private List<Message> messages;

    public ClaudeRequest() {
        // 기본 생성자
        this.model = "claude-3-7-sonnet-latest";
        this.maxTokens = 4000;
        this.messages = new ArrayList<>();
    }

    public ClaudeRequest(String prompt) {
        this();
        this.messages.add(new Message("user", prompt));
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public void addMessage(String role, String content) {
        this.messages.add(new Message(role, content));
    }

    public static class Message {
        @SerializedName("role")
        private String role;

        @SerializedName("content")
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}

// Claude API 응답 클래스
class ClaudeResponse {
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