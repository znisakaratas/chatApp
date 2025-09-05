package com.example.chat.dto;

import java.time.Instant;

public record ChatEvent(
        String fromUserId,
        String toUserId,
        String content,
        Instant createdAt,
        String groupId
) {}

