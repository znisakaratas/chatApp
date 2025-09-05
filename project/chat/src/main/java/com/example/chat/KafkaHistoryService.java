// KafkaHistoryService.java
package com.example.chat;

import com.example.chat.dto.ChatEvent;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

import static com.example.chat.KafkaConfig.CHAT_TOPIC;
import static com.example.chat.UserProvisioningService.log;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import java.time.Instant;
import java.util.*;

@Service
public class KafkaHistoryService {
    private final KafkaProperties props;
    public KafkaHistoryService(KafkaProperties props) { this.props = props; }
    private int partitionForKey(String key, int pCount) {
        return org.apache.kafka.common.utils.Utils.toPositive(
                org.apache.kafka.common.utils.Utils.murmur2(key.getBytes())
        ) % pCount;
    }
    public record HistoryPage(List<ChatEvent> items, Long nextCursor, boolean hasMore) {}
    public HistoryPage fetchConversationByTime(String meId, String peerId, int limit, Long beforeTs) {
        Map<String, Object> cfg = new HashMap<>(props.buildConsumerProperties());
        // Aynı konfigürasyon, ancak her istekte yeni bir consumer oluşturmak yerine
        // bu metodu tek bir consumer'ın metodlarından çağırmak daha iyi olur.

        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, "history-ts-" + meId + "-" + UUID.randomUUID());

        try (var consumer = new KafkaConsumer<String, ChatEvent>(cfg)) {
            var partitionsInfo = consumer.partitionsFor(CHAT_TOPIC, Duration.ofSeconds(3));
            if (partitionsInfo == null || partitionsInfo.isEmpty()) {
                return new HistoryPage(List.of(), null, false);
            }

            List<TopicPartition> tps;
            // ... (partition atama mantığı, önceki gibi)
            if (peerId.startsWith("group:")) {
                tps = new ArrayList<>();
                for (var i : partitionsInfo) tps.add(new TopicPartition(i.topic(), i.partition()));
            } else {
                int pCount = partitionsInfo.size();
                int pMe = partitionForKey(meId, pCount);
                int pPeer = partitionForKey(peerId, pCount);
                tps = (pMe == pPeer)
                        ? List.of(new TopicPartition(CHAT_TOPIC, pMe))
                        : List.of(new TopicPartition(CHAT_TOPIC, pMe), new TopicPartition(CHAT_TOPIC, pPeer));
            }

            consumer.assign(tps);
            consumer.poll(Duration.ofMillis(1));

            Map<TopicPartition, Long> startOffsets = consumer.beginningOffsets(new HashSet<>(tps));
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(new HashSet<>(tps));

            final List<ChatEvent> allFoundMessages = new ArrayList<>();
            final boolean isGroupReq = peerId.startsWith("group:");
            final Predicate<ChatEvent> matchPredicate = ev -> {
                if (ev == null || ev.createdAt() == null) return false;
                long ts = ev.createdAt().toEpochMilli();

               if (isGroupReq) {
                   return peerId.equals(ev.groupId()) && (beforeTs == null || ts < beforeTs);
               } else {
                       // DM geçmişi: GRUP OLMAYAN event’ler  iki taraf eşleşmesi
                   boolean participants = (meId.equals(ev.toUserId()) && peerId.equals(ev.fromUserId())) ||
                                   (meId.equals(ev.fromUserId()) && peerId.equals(ev.toUserId()));
                   return ev.groupId() == null && participants && (beforeTs == null || ts < beforeTs);
               }
                //return isMatch && (beforeTs == null || ts < beforeTs); // 'ts <= beforeTs' yerine 'ts < beforeTs' kullanın
            };

            for (var tp : tps) {
                long currentOffset = endOffsets.getOrDefault(tp, 0L);
                consumer.seek(tp, currentOffset);
                int idleCount = 0;

                while (allFoundMessages.size() < limit && currentOffset > startOffsets.getOrDefault(tp, 0L)) {
                    consumer.seek(tp, Math.max(startOffsets.get(tp), currentOffset - 200)); // Geriye doğru 200 mesaj tara

                    ConsumerRecords<String, ChatEvent> records = consumer.poll(Duration.ofMillis(100));
                    if (records.isEmpty()) {
                        if (++idleCount >= 5) break;
                        continue;
                    }

                    List<ConsumerRecord<String, ChatEvent>> batch = records.records(tp);
                    var mutableBatch = new ArrayList<>(batch); // Değiştirilebilir kopya oluştur
                    Collections.reverse(mutableBatch); // Kopyayı tersine çevir

                    long lastReadOffset = currentOffset;
                    for (var r : mutableBatch) {
                        if (r.offset() >= lastReadOffset) continue; // Aynı mesajları tekrar okumamak için

                        var ev = r.value();
                        if (matchPredicate.test(ev)) {
                            allFoundMessages.add(ev);
                        }

                        if (allFoundMessages.size() >= limit) {
                            break;
                        }
                    }

                    if (allFoundMessages.size() >= limit) break;
                    currentOffset = Math.max(startOffsets.get(tp), currentOffset - 200);
                }
            }

            allFoundMessages.sort(Comparator.comparing(ChatEvent::createdAt));
            boolean hasMore = allFoundMessages.size() >= limit;
            Long nextCursor = hasMore ? allFoundMessages.get(0).createdAt().toEpochMilli() : null;

            if (allFoundMessages.size() > limit) {
                allFoundMessages.subList(0, allFoundMessages.size() - limit).clear();
            }

            return new HistoryPage(allFoundMessages, nextCursor, hasMore);
        }
    }

    private boolean matchesConversation(String meId, String peerId, ChatEvent ev) {
        if (peerId.startsWith("group:")) {
            return peerId.equals(ev.groupId()) && meId.equals(ev.toUserId());
        }
        return (meId.equals(ev.toUserId()) && peerId.equals(ev.fromUserId()))
                || (meId.equals(ev.fromUserId()) && peerId.equals(ev.toUserId()));
    }
    public List<ChatEvent> fetchConversation(long meId, String peerIdRaw, int limit) {
        if (peerIdRaw != null && peerIdRaw.startsWith("group:")) {
            long gid = Long.parseLong(peerIdRaw.substring("group:".length()));
            return fetchGroupConversationAllPartitions(gid, limit);   // 👈 değişti
        } else {
            long peerId = Long.parseLong(Objects.requireNonNull(peerIdRaw));
            return fetchDirectConversation(meId, peerId, limit);
        }
    }

    // DM: sadece benim mailbox’ımı tara
    private List<ChatEvent> fetchDirectConversation(long meId, long peerId, int limit) {
        var cfg = baseConsumerCfg();
        try (var consumer = new KafkaConsumer<String, ChatEvent>(cfg)) {
            var partsInfo = consumer.partitionsFor(CHAT_TOPIC, Duration.ofSeconds(3));
            if (partsInfo == null || partsInfo.isEmpty()) return List.of();

            int pCount = partsInfo.size();
            int pMe    = partitionForKey(String.valueOf(meId),  pCount);
            int pPeer  = partitionForKey(String.valueOf(peerId), pCount);

            var tpMe   = new TopicPartition(CHAT_TOPIC, pMe);
            List<TopicPartition> tps = (pMe == pPeer)
                    ? List.of(tpMe)
                    : List.of(tpMe, new TopicPartition(CHAT_TOPIC, pPeer));

            consumer.assign(tps);
            consumer.poll(Duration.ofMillis(1)); // assignment ısınsın

            var begins = consumer.beginningOffsets(new HashSet<>(tps));
            var ends   = consumer.endOffsets(new HashSet<>(tps));

            final String meStr   = String.valueOf(meId);
            final String peerStr = String.valueOf(peerId);

            return scanWindowed(
                    consumer, tps, begins, ends, limit,
                    ev -> ev != null && ev.groupId() == null &&
                            (
                                    (meStr.equals(ev.toUserId()) && peerStr.equals(ev.fromUserId())) ||
                                            (meStr.equals(ev.fromUserId()) && peerStr.equals(ev.toUserId()))
                            )
            );
        }
    }
    private List<ChatEvent> fetchGroupConversationAllPartitions(long groupId, int limit) {
        final String groupKey = "group:" + groupId;
        return scanTopicAllPartitions(limit, ev -> groupKey.equals(ev.groupId()));
    }

    //Ortak olanlar

    private Map<String, Object> baseConsumerCfg() {
        var cfg = new HashMap<>(props.buildConsumerProperties());
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonDeserializer.class);
        cfg.put(org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES, "com.example.chat.dto");
        cfg.put(org.springframework.kafka.support.serializer.JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        cfg.put(org.springframework.kafka.support.serializer.JsonDeserializer.VALUE_DEFAULT_TYPE, "com.example.chat.dto.ChatEvent");
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return cfg;
    }

    /** Sadece "benim" partition’ımı tara. */
    private List<ChatEvent> scanMyMailboxPartition(long meId, int limit, Predicate<ChatEvent> pick) {
        var cfg = baseConsumerCfg();
        try (var consumer = new KafkaConsumer<String, ChatEvent>(cfg)) {
            var parts = consumer.partitionsFor(CHAT_TOPIC, Duration.ofSeconds(3));
            if (parts == null || parts.isEmpty()) return List.of();

            int pCount = parts.size();
            int pIndex = org.apache.kafka.common.utils.Utils.toPositive(
                    org.apache.kafka.common.utils.Utils.murmur2(String.valueOf(meId).getBytes())) % pCount;

            var tp = new TopicPartition(CHAT_TOPIC, pIndex);
            consumer.assign(Collections.singletonList(tp));
            consumer.poll(Duration.ofMillis(1)); // assignment ısınsın

            long begin = consumer.beginningOffsets(Set.of(tp)).get(tp);
            long end   = consumer.endOffsets(Set.of(tp)).get(tp);
            return scanWindowed(consumer, List.of(tp), Map.of(tp, begin), Map.of(tp, end), limit, pick);
        }
    }

    /** Tüm partition’ları tara  */
    private List<ChatEvent> scanTopicAllPartitions(int limit, Predicate<ChatEvent> pick) {
        var cfg = baseConsumerCfg();
        try (var consumer = new KafkaConsumer<String, ChatEvent>(cfg)) {
            var infos = consumer.partitionsFor(CHAT_TOPIC, Duration.ofSeconds(3));
            if (infos == null || infos.isEmpty()) return List.of();

            var tps = new ArrayList<TopicPartition>(infos.size());
            for (var i : infos) tps.add(new TopicPartition(i.topic(), i.partition()));
            consumer.assign(tps);
            consumer.poll(Duration.ofMillis(1));

            var begins = consumer.beginningOffsets(new HashSet<>(tps));
            var ends   = consumer.endOffsets(new HashSet<>(tps));
            return scanWindowed(consumer, tps, begins, ends, limit, pick);
        }
    }

    /** Pencere büyütüp küçülterek son N kaydı bul. */
    private List<ChatEvent> scanWindowed(
            KafkaConsumer<String, ChatEvent> consumer,
            List<TopicPartition> tps,
            Map<TopicPartition, Long> begins,
            Map<TopicPartition, Long> ends,
            int limit,
            Predicate<ChatEvent> pick
    ) {
        long window = 5_000L;
        final long MAX_WINDOW = 400_000L;
        var found = new ArrayList<ChatEvent>(limit);

        while (found.size() < limit) {
            boolean moved = false;

            for (var tp : tps) {
                long begin = begins.getOrDefault(tp, 0L);
                long end   = ends.getOrDefault(tp, 0L);
                if (end <= begin) continue;

                long start = Math.max(begin, end - window);
                consumer.seek(tp, start);

                long fetched = start;
                int idle = 0;
                while (fetched < end && found.size() < limit) {
                    var recs = consumer.poll(Duration.ofMillis(250));
                    var batch = recs.records(tp);
                    if (batch.isEmpty()) {
                        if (++idle >= 4) break;
                        continue;
                    }
                    idle = 0;
                    for (var r : batch) {
                        fetched = r.offset() + 1;
                        var ev = r.value();
                        if (ev != null && pick.test(ev)) {
                            found.add(ev);
                            if (found.size() >= limit) break;
                        }
                    }
                }

                ends.put(tp, start); // bir önceki pencereye gerile
                moved = true;
                if (found.size() >= limit) break;
            }

            if (!moved) break;
            window = Math.min(window * 2, MAX_WINDOW);
        }

        // createdAt yoksa 0 kabul
        found.sort(Comparator.comparing(e -> {
            Instant t = e.createdAt();
            return t != null ? t : Instant.EPOCH;
        }));
        if (found.size() > limit) return found.subList(found.size() - limit, found.size());
        return found;
    }


}