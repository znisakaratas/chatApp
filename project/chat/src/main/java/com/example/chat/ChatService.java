// ChatService.java
package com.example.chat;

import com.example.chat.dto.ChatEvent;
import com.example.chat.dto.ChatInbound;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.example.chat.KafkaConfig.CHAT_TOPIC;


@Service
public class ChatService {
    private final KafkaTemplate<String, ChatEvent> kafka;
    private final AppGroupRepository groupRepo;

    public ChatService(KafkaTemplate<String, ChatEvent> kafka,
                       AppGroupRepository groupRepo) {
        this.kafka = kafka;
        this.groupRepo = groupRepo;
    }

    @Transactional(readOnly = true)
    public void handleSend(Long fromUserId, ChatInbound inbound) {
        final String to = String.valueOf(inbound.toUserId());
        final Instant now = Instant.now();

        if (to.startsWith("group:")) {
            Long gid = Long.parseLong(to.substring("group:".length()));
            var g = groupRepo.findByIdWithMembers(gid)
                    .orElseThrow(() -> new IllegalArgumentException("Group not found: " + gid));

            final String groupKey = "group:" + gid;
            long ts = now.toEpochMilli();

            g.getMembers().forEach(m -> {
                String memberId = String.valueOf(m.getId());
                var ev = new ChatEvent(String.valueOf(fromUserId), memberId, inbound.content(), now, groupKey);
                kafka.send(CHAT_TOPIC, null, ts, memberId, ev);
            });

        } else{
             var ev = new ChatEvent(String.valueOf(fromUserId), to, inbound.content(), now, null);

            long ts = now.toEpochMilli();
            // Alıcı mailbox'ı
            kafka.send(CHAT_TOPIC, null, ts, ev.toUserId(), ev);
            // Gönderen mailbox'ı (sender kendi mesajını anında görsün ve history çalışsın)
            kafka.send(CHAT_TOPIC, null, ts, String.valueOf(fromUserId), ev);
        }
    }
}