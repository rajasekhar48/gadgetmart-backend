package com.gadgetmart.notification.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    /**
     * A message that keeps failing (e.g. malformed JSON) is retried 3 times, one second apart, and then parked on
     * "<topic>.DLT" so it can be inspected, instead of blocking the partition forever.
     * Spring Boot plugs this handler into the listener container automatically.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template, (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));   // -1: any partition
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }

    // Declared here too so the consumer never starts before the topics exist.
    @Bean
    public NewTopic orderEventsTopic(@Value("${app.kafka.topics.orders}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic userEventsTopic(@Value("${app.kafka.topics.users}") String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
