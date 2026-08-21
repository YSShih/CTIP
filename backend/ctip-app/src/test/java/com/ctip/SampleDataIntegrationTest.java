package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** DoD M1-18:樣本資料寫入成功,>= 1000 IOC,涵蓋所有型別、四種 TLP 與四種 status(docs/spec/14-testing.md §14.7)。 */
class SampleDataIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void atLeastOneThousandSampleIndicatorsSeeded() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM indicators", Integer.class))
                .isGreaterThanOrEqualTo(1000);
    }

    @Test
    void allIocTypesCovered() {
        List<String> types = jdbc.queryForList("SELECT DISTINCT type FROM indicators", String.class);
        assertThat(types).containsExactlyInAnyOrder("IPV4", "IPV6", "DOMAIN", "URL", "FILE_HASH", "EMAIL");
    }

    @Test
    void exactlyFourTlpLevelsCovered() {
        List<String> tlps = jdbc.queryForList("SELECT DISTINCT tlp FROM indicators", String.class);
        assertThat(tlps).containsExactlyInAnyOrder("CLEAR", "GREEN", "AMBER", "AMBER_STRICT");
    }

    @Test
    void allFourStatusesCovered() {
        List<String> statuses = jdbc.queryForList("SELECT DISTINCT status FROM indicators", String.class);
        assertThat(statuses).containsExactlyInAnyOrder("ACTIVE", "EXPIRED", "REVOKED", "FALSE_POSITIVE");
    }

    @Test
    void amberDataBelongsToDemoTenantAndPublicHoldsClearGreenOnly() {
        assertThat(jdbc.queryForList(
                        "SELECT DISTINCT tlp FROM indicators"
                                + " WHERE owner_tenant_id = '00000000-0000-0000-0000-000000000000'::uuid",
                        String.class))
                .containsExactlyInAnyOrder("CLEAR", "GREEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tenants WHERE slug = 'demo'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void everySampleIndicatorHasSourceRecord() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM indicators i WHERE NOT EXISTS"
                                + " (SELECT 1 FROM indicator_sources s WHERE s.indicator_id = i.id)",
                        Integer.class))
                .isZero();
    }

    @Test
    void fourSeedSourcesPresentAndOnlyOpenPhishSyncEnabled() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sources", Integer.class))
                .isEqualTo(4);
        List<String> enabledSyncable =
                jdbc.queryForList("SELECT source_type FROM sources WHERE enabled AND syncable", String.class);
        assertThat(enabledSyncable).containsExactly("MOCK_OPENPHISH");
    }

    @Test
    void stixObjectsAndRelationshipsSeeded() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stix_objects", Integer.class))
                .isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stix_relationships", Integer.class))
                .isPositive();
    }
}
