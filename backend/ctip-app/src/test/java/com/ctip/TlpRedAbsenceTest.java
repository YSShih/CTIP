package com.ctip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** DoD M1-19:資料庫中無 TLP:RED 資料(docs/spec/14-testing.md §14.7;RED 不進入平台)。 */
class TlpRedAbsenceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void noRedIndicators() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM indicators WHERE tlp = 'RED'", Integer.class))
                .isZero();
    }

    @Test
    void noRedIndicatorSources() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM indicator_sources WHERE source_tlp = 'RED'", Integer.class))
                .isZero();
    }

    @Test
    void noRedSourceDefaults() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sources WHERE default_tlp = 'RED'", Integer.class))
                .isZero();
    }

    @Test
    void noRedStixObjectsOrRelationships() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stix_objects WHERE tlp = 'RED'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stix_relationships WHERE tlp = 'RED'", Integer.class))
                .isZero();
    }
}
