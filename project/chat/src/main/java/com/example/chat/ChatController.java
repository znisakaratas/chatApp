// ChatController.java
package com.example.chat;

import com.example.chat.dto.ChatInbound;
import com.example.chat.dto.ChatOutbound;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

@Controller
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // /app/chat.send
    @MessageMapping("/chat.send")
    public void send(@Payload ChatInbound inbound, Principal principal) {
        Long fromId = Long.valueOf(principal.getName()); // JwtHandshakeHandler -> AppUser.id
        chatService.handleSend(fromId, inbound);
    }
}
