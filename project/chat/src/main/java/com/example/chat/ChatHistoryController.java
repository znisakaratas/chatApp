package com.example.chat;
import com.example.chat.dto.HistoryPageDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatHistoryController {
    private final KafkaHistoryService history;
    private final AppUserRepository userRepo;

    public ChatHistoryController(KafkaHistoryService history, AppUserRepository userRepo) {
        this.history = history;
        this.userRepo = userRepo;
    }

    @GetMapping("/messages")
    public HistoryPageDto getMessages(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("peerId") String peerId,
            @RequestParam(value = "limit", defaultValue = "15") int limit,      // FE ile uyumlu
            @RequestParam(value = "beforeTs", required = false) Long beforeTs
    ) {
        var me = userRepo.findByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException("user not found"));

        var page = history.fetchConversationByTime(String.valueOf(me.getId()), peerId, limit, beforeTs);
        return HistoryPageDto.from(page);
    }
}
