package com.demo.memory;

import lombok.Data;

@Data
public class ChatMessage {
    /** user / assistant / system */
    private String role;
    /** 对话内容 */
    private String content;
    private long timestamp;
}

