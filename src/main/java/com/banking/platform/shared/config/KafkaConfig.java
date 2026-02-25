package com.banking.platform.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // Producer Configuration
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // Consumer Configuration
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "${spring.kafka.consumer.group-id:banking-platform}");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.lang.Object");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.banking.platform.*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, Object>>
    kafkaListenerContainerFactory(ConsumerFactory<String, Object> consumerFactory,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1000, 3)
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    // Topic Beans
    @Bean
    public NewTopic onboardingEventsTopic() {
        return TopicBuilder.name("onboarding-events")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic onboardingEventsDLT() {
        return TopicBuilder.name("onboarding-events-dlt")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name("transaction-events")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionEventsDLT() {
        return TopicBuilder.name("transaction-events-dlt")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic achEventsTopic() {
        return TopicBuilder.name("ach-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic achEventsDLT() {
        return TopicBuilder.name("ach-events-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic wireEventsTopic() {
        return TopicBuilder.name("wire-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic wireEventsDLT() {
        return TopicBuilder.name("wire-events-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rewardsEventsTopic() {
        return TopicBuilder.name("rewards-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rewardsEventsDLT() {
        return TopicBuilder.name("rewards-events-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic accountEventsTopic() {
        return TopicBuilder.name("account-events")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic accountEventsDLT() {
        return TopicBuilder.name("account-events-dlt")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ledgerEventsTopic() {
        return TopicBuilder.name("ledger-events")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ledgerEventsDLT() {
        return TopicBuilder.name("ledger-events-dlt")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic debitNetworkEventsTopic() {
        return TopicBuilder.name("debit-network-events")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic debitNetworkEventsDLT() {
        return TopicBuilder.name("debit-network-events-dlt")
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reportEventsTopic() {
        return TopicBuilder.name("report-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reportEventsDLT() {
        return TopicBuilder.name("report-events-dlt")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
