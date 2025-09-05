package com.example.chat.dto;
import java.time.Instant;

public record ChatOutbound(String fromUserId, String toUserId, String content, Instant createdAt,String groupId) {}
