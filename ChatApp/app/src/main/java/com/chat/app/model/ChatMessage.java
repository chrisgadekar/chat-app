package com.chat.app.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatMessage {
    private String id;
    private MessageType type;
    private String sender;
    private String content;
    private String room;
    private Long timestamp;

    // Only used for REACTION messages.
    private String emoji;
    private String targetId;
}
