package com.ctip.support;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * L4(heavy)測試共用的 Kafka 容器(docs/spec/14-testing.md §14.1)。
 *
 * <p>單例:broker 啟動慢,每個測試類各起一個會讓整批 heavy 測試變成不可跑
 * (與 {@link ElasticsearchTestContainer} 同一個理由)。
 *
 * <p>版本與 compose 一致({@code apache/kafka:4.2.1};06 §6.2.2)。
 * {@code org.testcontainers.kafka.KafkaContainer} 對應的正是 <strong>KRaft</strong> 模式的
 * {@code apache/kafka} image——Kafka 4.x 已完全移除 ZooKeeper,phase-20 亦明文禁止使用它。
 */
public final class KafkaTestContainer {

    private static final KafkaContainer CONTAINER = new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.1"));

    private KafkaTestContainer() {}

    public static String bootstrapServers() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        return CONTAINER.getBootstrapServers();
    }
}
