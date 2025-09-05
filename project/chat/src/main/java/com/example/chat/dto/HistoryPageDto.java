package com.example.chat.dto;

import com.example.chat.KafkaHistoryService;

import java.util.*;

// HistoryPageDto.java
public record HistoryPageDto(
        List<Map<String, Object>> items,
        Long nextCursor,    // epoch ms (beforeTs için)
        boolean hasMore
) {
    public static HistoryPageDto from(KafkaHistoryService.HistoryPage p) {
        var out = new ArrayList<Map<String,Object>>(p.items().size());
        for (var ev : p.items()) {
            var row = new LinkedHashMap<String,Object>();
            row.put("fromUserId", ev.fromUserId());
            row.put("toUserId",   ev.toUserId());
            row.put("content",    ev.content());
            row.put("createdAt",  ev.createdAt() == null ? null : ev.createdAt().toEpochMilli());
            row.put("groupId",    ev.groupId());
            out.add(row);
        }
        return new HistoryPageDto(out, p.nextCursor(), p.hasMore());
    }
}

