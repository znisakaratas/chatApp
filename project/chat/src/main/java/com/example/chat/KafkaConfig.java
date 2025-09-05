// KafkaConfig.java
package com.example.chat;

import com.example.chat.dto.ChatEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    public static final String CHAT_TOPIC = "chat-messages";

    // --------- Producer ----------
    @Bean
    public ProducerFactory<String, ChatEvent> producerFactory(KafkaProperties props) {
        Map<String, Object> cfg = new HashMap<>(props.buildProducerProperties());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(cfg);
    }

    @Bean
    public KafkaTemplate<String, ChatEvent> kafkaTemplate(ProducerFactory<String, ChatEvent> pf) {
        return new KafkaTemplate<>(pf);
    }

    // --------- Consumer ----------
    @Bean
    public ConsumerFactory<String, ChatEvent> consumerFactory(KafkaProperties props) {
        Map<String, Object> cfg = new HashMap<>(props.buildConsumerProperties());
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        cfg.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.chat.dto");
        cfg.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        cfg.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.example.chat.dto.ChatEvent");

        return new DefaultKafkaConsumerFactory<>(
                cfg,
                new StringDeserializer(),
                new JsonDeserializer<>(ChatEvent.class, false)
        );
    }

    // --------- Listener Factory (manuel set) ----------
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, ChatEvent> consumerFactory
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ChatEvent>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setMissingTopicsFatal(false);
        // factory.setConcurrency(3); // istersen
        return factory;
    }

    // KafkaConfig.java
    @Bean
    public NewTopic chatTopic() {
        return TopicBuilder.name(CHAT_TOPIC)
                .partitions(3).replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(30L*24*60*60*1000)) // 30 gün
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

}
