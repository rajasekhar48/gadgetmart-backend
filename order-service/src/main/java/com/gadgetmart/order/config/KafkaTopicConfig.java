package com.gadgetmart.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /** Created on startup if missing (3 partitions: events of different aggregates are processed in parallel). */
    @Bean
    public NewTopic eventsTopic(@Value("${app.kafka.topic}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
