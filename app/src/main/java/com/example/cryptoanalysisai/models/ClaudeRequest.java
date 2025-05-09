package com.example.cryptoanalysisai.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ClaudeRequest {
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