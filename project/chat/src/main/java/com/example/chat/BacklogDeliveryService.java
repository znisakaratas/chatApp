// BacklogDeliveryService.java
package com.example.chat;

import com.example.chat.dto.ChatEvent;
import com.example.chat.dto.ChatOutbound;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static com.example.chat.KafkaConfig.CHAT_TOPIC;

@Service
public class BacklogDeliveryService {

    private final org.springframework.boot.autoconfigure.kafka.KafkaProperties props;
    private final SimpMessagingTemplate template;

    // userId → consumer runner
    private final ConcurrentHashMap<String, Future<?>> runners = new ConcurrentHashMap<>();
    private final ExecutorService exec = Executors.newCachedThreadPool();

    public BacklogDeliveryService(org.springframework.boot.autoconfigure.kafka.KafkaProperties props,
                                  SimpMessagingTemplate template) {
        this.props = props;
        this.template = template;
    }

    /** Kullanıcı subscribe olunca, kendi groupId’siyle consume etmeye başla (varsa kaldığı yerden). */
    public void startConsumingForUser(String userId) {
        runners.computeIfAbsent(userId, uid -> exec.submit(() -> runLoop(uid)));
    }

    public void stopConsumingForUser(String userId) {
        Optional.ofNullable(runners.remove(userId)).ifPresent(f -> f.cancel(true));
    }


    private void runLoop(String userId) {
        Map<String, Object> cfg = new HashMap<>(props.buildConsumerProperties());
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonDeserializer.class);
        cfg.put(org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES, "com.example.chat.dto");
        cfg.put(org.springframework.kafka.support.serializer.JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        cfg.put(org.springframework.kafka.support.serializer.JsonDeserializer.VALUE_DEFAULT_TYPE, "com.example.chat.dto.ChatEvent");

        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, "chat-user-" + userId);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        var cf = new org.springframework.kafka.core.DefaultKafkaConsumerFactory<String, ChatEvent>(cfg);

        try (var consumer = cf.createConsumer()) {

            //  sadece benim mailbox’ımın olduğu partition’a assign et:
            var parts = consumer.partitionsFor(CHAT_TOPIC, Duration.ofSeconds(3));
            int pCount = parts.size();
            int pIndex = org.apache.kafka.common.utils.Utils.toPositive(
                    org.apache.kafka.common.utils.Utils.murmur2(userId.getBytes())) % pCount;
            var tp = new org.apache.kafka.common.TopicPartition(CHAT_TOPIC, pIndex);
            consumer.assign(java.util.Collections.singletonList(tp));
            while (!Thread.currentThread().isInterrupted()) {
                var records = consumer.poll(Duration.ofMillis(500));
                for (var rec : records) {
                    ChatEvent ev = rec.value();
                    if (ev == null) continue;

                    // Bu kullanıcı için anlamlı kopyalar: ya bana yazılmış, ya da benim outbound’um
                    if (userId.equals(ev.toUserId()) || userId.equals(ev.fromUserId())) {
                        var out = new ChatOutbound(
                                ev.fromUserId(),
                                ev.toUserId(),
                                ev.content(),
                                ev.createdAt(),
                                ev.groupId()
                        );
                        template.convertAndSendToUser(userId, "/queue/messages", out);
                    }
                }
                consumer.commitSync();
            }
        } catch (Exception ignored) {
        }
    }}
