package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** DoD M1-20:所有必要索引存在(docs/spec/04-data-dictionary.md M1 各表的索引與唯一約束)。 */
class RequiredIndexTest extends AbstractPostgresIntegrationTest {

    private static final List<String> REQUIRED_INDEXES = List.of(
            // tenants
            "ux_tenants_slug",
            // sources / source_sync
            "ux_sources_source_type",
            "ix_sources_enabled_status",
            "ix_source_sync_source_started",
            // indicators
            "ux_indicators_identity",
            "ix_indicators_fingerprint",
            "ix_indicators_tenant_status",
            "ix_indicators_last_seen",
            "ix_indicators_valid_until",
            "ix_indicators_tags",
            "ix_indicators_value_trgm",
            // indicator_sources
            "ux_indicator_sources",
            "ix_is_source_status",
            "ix_is_payload_gc",
            // hash_records
            "ux_hash_records",
            "ix_hash_records_digest",
            // ingestion_rejections
            "ix_ir_source_created",
            "ix_ir_gc",
            // stix_objects
            "ux_stix_objects_stix_id",
            "ix_so_tenant_tlp",
            "ix_so_type",
            "ix_so_indicator",
            // stix_relationships
            "ux_stix_rel_stix_id",
            "ux_stix_rel_triple",
            "ix_sr_source",
            "ix_sr_target",
            // threats / threat_indicators / threat_external_references(表 19–21;Phase 18)
            "ux_threats_identity",
            "ix_threats_tenant_status",
            "ix_threats_aliases",
            "ix_threats_last_seen",
            "ix_ti_indicator",
            "ix_ter_external",
            "ux_ter_identity_coalesced",
            // fk_so_threat 帶 ON DELETE CASCADE,沒有索引就是每刪一個 threat 全表掃 stix_objects
            "ix_so_threat",
            // bloom_versions / bloom_artifacts(表 22、23)
            "ux_bv_version",
            "ix_bv_lookup",
            "ux_ba_version",
            "ix_ba_gc");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allRequiredIndexesExist() {
        List<String> present =
                jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
        assertThat(present).containsAll(REQUIRED_INDEXES);
    }

    @Test
    void cursorPaginationIndexHasCompositeDescendingKey() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ix_indicators_last_seen'", String.class);
        assertThat(definition).contains("last_seen DESC", "id DESC");
    }
}
