package com.ctip.support;

import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * L4(heavy)測試共用的 Elasticsearch 容器(docs/spec/14-testing.md §14.1)。
 *
 * <p>單例:ES 啟動慢且吃記憶體,每個測試類各起一個會讓整批 heavy 測試變成不可跑。
 * 版本與 compose 一致({@code elasticsearch:9.5.1};06 §6.2.4 明定不得用已 EOL 的 9.3)。
 * 安全性關閉、single-node——與 staging 的 compose 設定相同,測的是同一組行為。
 */
public final class ElasticsearchTestContainer {

    /** Docker Hub 的 elasticsearch image 與 testcontainers 預設的 docker.elastic.co 座標等價。 */
    private static final DockerImageName IMAGE = DockerImageName.parse("elasticsearch:9.5.1")
            .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch");

    private static final ElasticsearchContainer CONTAINER = new ElasticsearchContainer(IMAGE)
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    private ElasticsearchTestContainer() {}

    public static String url() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        return "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9200);
    }
}
