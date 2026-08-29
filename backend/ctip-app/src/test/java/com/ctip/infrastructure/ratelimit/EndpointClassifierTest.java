package com.ctip.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.plan.EndpointClass;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 限流維度 5 的端點分類(docs/spec/10-identity-plans.md §10.7)。 */
@Tag("unit")
class EndpointClassifierTest {

    @Test
    void getRequestsAreRead() {
        assertThat(EndpointClassifier.classify("GET", "/api/v1/iocs")).isEqualTo(EndpointClass.READ);
    }

    /** §10.7 的 read 明文含「查詢」;以 POST 表達的搜尋不改變狀態,不該吃 write 的 20%。 */
    @Test
    void queryPostsAreRead() {
        assertThat(EndpointClassifier.classify("POST", "/api/v1/iocs/search")).isEqualTo(EndpointClass.READ);
        assertThat(EndpointClassifier.classify("POST", "/api/v1/iocs/lookup")).isEqualTo(EndpointClass.READ);
    }

    @Test
    void stateChangingRequestsAreWrite() {
        assertThat(EndpointClassifier.classify("POST", "/api/v1/iocs")).isEqualTo(EndpointClass.WRITE);
        assertThat(EndpointClassifier.classify("DELETE", "/api/v1/api-keys/abc"))
                .isEqualTo(EndpointClass.WRITE);
    }

    @Test
    void bloomDownloadBundleAndImportAreHeavy() {
        assertThat(EndpointClassifier.classify("GET", "/api/v1/sync/bloom")).isEqualTo(EndpointClass.HEAVY);
        assertThat(EndpointClassifier.classify("GET", "/api/v1/stix/bundle")).isEqualTo(EndpointClass.HEAVY);
        assertThat(EndpointClassifier.classify("POST", "/api/v1/iocs/import")).isEqualTo(EndpointClass.HEAVY);
    }

    /** 尾端斜線是同一支端點,不得因此掉到別的類別(heavy → read 會放寬五倍)。 */
    @Test
    void trailingSlashDoesNotChangeTheClass() {
        assertThat(EndpointClassifier.classify("GET", "/api/v1/sync/bloom/")).isEqualTo(EndpointClass.HEAVY);
    }
}
