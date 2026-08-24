package com.ctip.sdk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Shared Kernel 列舉的成員契約(docs/spec/04-data-dictionary.md §4.5)。
 * 這些列舉與 DB CHECK 約束一一對應;任何成員增減都是 SDK 版本事件(02 §2.5),必須先改這裡的預期。
 */
@Tag("unit")
class SharedKernelEnumContractTest {

    @Test
    void iocTypeMembersMatchDataDictionary() {
        assertThat(IocType.values())
                .extracting(Enum::name)
                .containsExactly("IPV4", "IPV6", "DOMAIN", "URL", "FILE_HASH", "EMAIL");
    }

    @Test
    void iocHashTypeMembersMatchDataDictionary() {
        assertThat(IocHashType.values()).extracting(Enum::name).containsExactly("MD5", "SHA1", "SHA256", "SHA512");
        assertThat(IocHashType.valueOf("SHA256")).isNotNull();
    }

    @Test
    void fingerprintAlgorithmMembersMatchDataDictionary() {
        assertThat(FingerprintAlgorithm.values()).extracting(Enum::name).containsExactly("SHA256", "SHA512");
        assertThat(FingerprintAlgorithm.valueOf("SHA256")).isNotNull();
    }

    @Test
    void redistributionPolicyMembersMatchDataDictionary() {
        assertThat(RedistributionPolicy.values())
                .extracting(Enum::name)
                .containsExactly("PUBLIC_REDISTRIBUTABLE", "ATTRIBUTION_REQUIRED", "DERIVED_ONLY", "INTERNAL_ONLY");
    }

    @Test
    void sourceTypeMembersMatchSeededSources() {
        assertThat(SourceType.values())
                .extracting(Enum::name)
                .containsExactly("MOCK_OPENPHISH", "MOCK_ABUSEIPDB", "MOCK_ALIENVAULT", "MANUAL");
        assertThat(SourceType.valueOf("MANUAL")).isNotNull();
    }

    @Test
    void tlpAndSeverityMembersMatchDataDictionary() {
        assertThat(Tlp.values())
                .extracting(Enum::name)
                .containsExactly("CLEAR", "GREEN", "AMBER", "AMBER_STRICT", "RED");
        assertThat(Severity.values())
                .extracting(Enum::name)
                .containsExactly("INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL");
        assertThat(Tlp.valueOf("AMBER_STRICT")).isNotNull();
        assertThat(Severity.valueOf("HIGH")).isNotNull();
    }
}
