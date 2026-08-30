package com.ctip.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ctip.application.port.IndicatorRepository;
import com.ctip.application.port.SearchDocumentPort;
import com.ctip.application.port.SearchIndexPort;
import com.ctip.application.port.SearchPort;
import com.ctip.application.search.SearchReconciliationService;
import com.ctip.infrastructure.elasticsearch.ElasticsearchIndexAdapter;
import com.ctip.infrastructure.elasticsearch.ElasticsearchSearchAdapter;
import com.ctip.infrastructure.elasticsearch.IndicatorSearchIndex;
import com.ctip.infrastructure.observability.ElasticsearchClusterHealthBinder;
import com.ctip.infrastructure.scheduling.SearchSchedulers;
import com.ctip.infrastructure.search.FallbackSearchAdapter;
import com.ctip.infrastructure.search.NoopSearchIndexAdapter;
import com.ctip.infrastructure.search.SearchIndexBootstrap;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 搜尋後端的裝配(docs/spec/13-platform-ops.md §13.7)。
 *
 * <p>三個 {@link SearchPort} bean 會同時存在({@code PostgresSearchAdapter} 是 {@code @Component}、
 * ES 的實作、以及組合兩者的 {@code FallbackSearchAdapter}),而 {@code IndicatorQueryService} 注入單一
 * {@code SearchPort}——因此組合實作標 {@link Primary},PostgreSQL 的以 bean 名稱取用
 * (它是 package-private 類別,型別在這裡看不到)。ADR 0020 §8 點名的歧義,解法記於 ADR 0028。
 *
 * <p>{@code SEARCH_BACKEND=postgres} 時完全不建立任何 ES bean:mvp/dev 的 compose 不啟動
 * Elasticsearch,憑空多一條永遠打不通的路只會讓每個查詢先等一次逾時。
 */
@Configuration(proxyBeanMethods = false)
class SearchConfig {

    /** 沒有外部索引時的寫入面;與下方 ES 區塊以同一個屬性互斥(比照 RateLimitConfig / RedisConfig)。 */
    @Bean
    @ConditionalOnProperty(name = "ctip.search.backend", havingValue = "postgres", matchIfMissing = true)
    SearchIndexPort noopSearchIndexPort() {
        return new NoopSearchIndexAdapter();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "ctip.search.backend", havingValue = "elasticsearch")
    static class ElasticsearchSearchConfig {

        @Bean
        IndicatorSearchIndex indicatorSearchIndex(ElasticsearchClient client) {
            IndicatorSearchIndex index = new IndicatorSearchIndex(client);
            // 機會性建立:ES 還沒起來時只記錄,第一次讀寫前會再試(§13.7 不得因 ES 而啟動失敗)
            index.ensureExists();
            return index;
        }

        @Bean
        ElasticsearchSearchAdapter elasticsearchSearchAdapter(
                ElasticsearchClient client, IndicatorSearchIndex index, IndicatorRepository indicators) {
            return new ElasticsearchSearchAdapter(client, index, indicators);
        }

        @Bean
        SearchIndexPort elasticsearchIndexPort(ElasticsearchClient client, IndicatorSearchIndex index) {
            return new ElasticsearchIndexAdapter(client, index);
        }

        @Bean
        @Primary
        SearchPort fallbackSearchAdapter(
                ElasticsearchSearchAdapter elasticsearch, @Qualifier("postgresSearchAdapter") SearchPort postgres) {
            return new FallbackSearchAdapter(elasticsearch, postgres);
        }

        /** 全新的 ES 叢集不必等到 05:00:索引空而資料庫非空時,啟動後在背景補建一次。 */
        @Bean
        SearchIndexBootstrap searchIndexBootstrap(
                SearchIndexPort index, SearchDocumentPort documents, SearchReconciliationService reconciliation) {
            return new SearchIndexBootstrap(index, documents, reconciliation);
        }

        /**
         * {@code elasticsearch.cluster.health}(13 §13.6)。{@code spring-boot-elasticsearch}
         * 沒有 metrics autoconfig,而 ES 只在此 profile 存在——binder 因此跟著 ES 的裝配走。
         */
        @Bean
        ElasticsearchClusterHealthBinder elasticsearchClusterHealthBinder(ElasticsearchClient client) {
            return new ElasticsearchClusterHealthBinder(() -> {
                try {
                    return client.cluster().health().status().jsonValue();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        /** 對帳排程只在 ES 後端且排程總開關開啟時註冊(§13.7 每日 05:00)。 */
        @Bean
        @ConditionalOnProperty(prefix = "ctip.scheduler", name = "enabled", havingValue = "true")
        SearchSchedulers searchSchedulers(SearchReconciliationService reconciliation) {
            return new SearchSchedulers(reconciliation);
        }
    }
}
