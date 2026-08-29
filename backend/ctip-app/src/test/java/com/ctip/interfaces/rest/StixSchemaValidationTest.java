package com.ctip.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.application.stix.StixBundle;
import com.ctip.domain.fingerprint.Sha256FingerprintStrategy;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorSource;
import com.ctip.domain.indicator.IndicatorSourceSnapshot;
import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.indicator.NewIndicatorCommand;
import com.ctip.domain.indicator.SourceRecordStatus;
import com.ctip.domain.source.Reputation;
import com.ctip.domain.source.SourceId;
import com.ctip.domain.stix.StixIdentityProjector;
import com.ctip.domain.stix.StixIndicatorProjector;
import com.ctip.domain.stix.StixObservedDataProjector;
import com.ctip.domain.stix.StixProjection;
import com.ctip.domain.stix.StixRelationshipProjector;
import com.ctip.domain.stix.StixThreatProjector;
import com.ctip.domain.stix.StixTlpMarkings;
import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.threat.ExternalReference;
import com.ctip.domain.threat.IndicatorRole;
import com.ctip.domain.threat.Threat;
import com.ctip.domain.threat.ThreatType;
import com.ctip.sdk.Confidence;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 以 STIX 2.1 JSON Schema 驗證實際產出(M1-29;docs/spec/07-domain-intel.md §7.8.6):
 * schema 為 vendored 的 OASIS cti-stix2-json-schemas(src/test/resources/stix-schemas,離線解析),
 * 產出走真實路徑——domain 投影 + 與 adapter 相同的 Jackson 序列化、bundle 走 StixBundleWriter。
 */
@Tag("unit")
class StixSchemaValidationTest {

    private static final String SCHEMA_BASE =
            "http://raw.githubusercontent.com/oasis-open/cti-stix2-json-schemas/stix2.1/schemas";
    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");
    private static final SourceId SOURCE_A = new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final SourceId SOURCE_B = new SourceId(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));
    private static final TenantId PUBLIC = TenantId.PUBLIC;

    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(
            SpecVersion.VersionFlag.V202012,
            builder -> builder.schemaMappers(mappers -> mappers.mapPrefix(SCHEMA_BASE, "classpath:stix-schemas")));
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void multiSourceIndicatorProjectionValidatesAgainstIndicatorSchema() {
        Indicator indicator = domainIndicator();
        String json = contentJson(indicator);
        assertThat(validate("/sdos/indicator.json", json)).isEmpty();
    }

    @Test
    void fileHashProjectionWithoutValidUntilValidates() {
        Indicator indicator = fileHashIndicator();
        String json = contentJson(indicator);
        assertThat(json).doesNotContain("valid_until");
        assertThat(validate("/sdos/indicator.json", json)).isEmpty();
    }

    @Test
    void revokedIndicatorProjectionValidates() {
        Indicator indicator = domainIndicator();
        indicator.revoke(SOURCE_B, new Reputation(90));
        String json = contentJson(indicator);
        assertThat(json).contains("\"revoked\":true");
        assertThat(validate("/sdos/indicator.json", json)).isEmpty();
    }

    @Test
    void allFiveTlpMarkingsValidateAgainstMarkingDefinitionSchema() {
        for (Tlp tlp : Tlp.values()) {
            String json = objectMapper.writeValueAsString(StixTlpMarkings.marking(tlp));
            assertThat(validate("/common/marking-definition.json", json))
                    .as("TLP %s", tlp)
                    .isEmpty();
        }
    }

    @Test
    void exportedBundleValidatesAgainstBundleSchema() {
        StixBundleWriter writer = new StixBundleWriter(objectMapper);
        StixBundle bundle = new StixBundle(
                "bundle--" + UUID.fromString("3c9d8e7f-6b2a-4d5e-a1b2-c3d4e5f60718"),
                List.of(StixTlpMarkings.marking(Tlp.CLEAR), StixTlpMarkings.marking(Tlp.GREEN)),
                List.of(contentJson(domainIndicator()), contentJson(fileHashIndicator())));
        assertThat(validate("/common/bundle.json", writer.toJson(bundle))).isEmpty();
    }

    @Test
    void emptyBundleOmitsObjectsAndStillValidates() {
        // STIX 2.1:objects 存在時 minItems 1——零筆匯出必須整個省略 objects 屬性
        StixBundleWriter writer = new StixBundleWriter(objectMapper);
        StixBundle bundle = new StixBundle(
                "bundle--" + UUID.fromString("9c9d8e7f-6b2a-4d5e-a1b2-c3d4e5f60719"), List.of(), List.of());
        String json = writer.toJson(bundle);
        assertThat(json).doesNotContain("\"objects\"");
        assertThat(validate("/common/bundle.json", json)).isEmpty();
    }

    private Set<ValidationMessage> validate(String schemaPath, String json) {
        JsonSchema schema = schemaFactory.getSchema(SchemaLocation.of(SCHEMA_BASE + schemaPath));
        return schema.validate(json, InputFormat.JSON);
    }

    /** 與 StixObjectAdapter 相同的序列化路徑(writeValueAsString of projection content)。 */
    private String contentJson(Indicator indicator) {
        StixProjection projection = StixIndicatorProjector.project(
                indicator.snapshot(),
                Map.of(SOURCE_A, "OpenPhish (Mock)", SOURCE_B, "AlienVault OTX (Mock)"),
                T0,
                T0.plusSeconds(3600));
        return objectMapper.writeValueAsString(projection.content());
    }

    /**
     * 迴歸鎖(ADR 0015):{@code name} 截斷到 255 char 時不得切斷 surrogate pair。
     *
     * <p>URL 型 IOC 的 normalized 值可達 2048 char;若第 255 個 char 恰好是 astral 字元的
     * 高代理,直接 substring 會產出半個字元——無效的 UTF-16,序列化出去就是壞掉的 JSON 字串。
     */
    // ---- M2 的四種 SDO 與 relationship(§7.8.1、§7.8.7;Phase 18) ----

    @Test
    void malwareProjectionValidatesAgainstMalwareSchema() {
        Threat threat = StixProjectionFixtures.malwareFamily();
        threat.addExternalReference(new ExternalReference("mitre-attack", "S0331", null, "Agent Tesla"));
        String json =
                objectMapper.writeValueAsString(StixThreatProjector.project(threat.snapshot(), T0, T0.plusSeconds(3600))
                        .content());

        assertThat(json).contains("\"is_family\":true").contains("\"aliases\"");
        assertThat(validate("/sdos/malware.json", json)).isEmpty();
    }

    @Test
    void attackPatternProjectionValidatesAndOmitsMalwareOnlyProperties() {
        Threat threat = StixProjectionFixtures.threat(ThreatType.ATTACK_PATTERN, "Phishing", Tlp.CLEAR, Set.of());
        String json = objectMapper.writeValueAsString(
                StixThreatProjector.project(threat.snapshot(), T0, T0).content());

        // attack-pattern 的 schema 沒有 is_family / first_seen / last_seen,也不得給空的 aliases 陣列
        assertThat(json)
                .doesNotContain("is_family")
                .doesNotContain("first_seen")
                .doesNotContain("aliases");
        assertThat(validate("/sdos/attack-pattern.json", json)).isEmpty();
    }

    @Test
    void retiredThreatIsProjectedAsRevoked() {
        Threat threat = StixProjectionFixtures.malwareFamily();
        threat.retire();
        String json = objectMapper.writeValueAsString(
                StixThreatProjector.project(threat.snapshot(), T0, T0).content());

        assertThat(json).contains("\"revoked\":true");
        assertThat(validate("/sdos/malware.json", json)).isEmpty();
    }

    @Test
    void observedDataProjectionValidatesForEveryIocType() {
        Indicator domain = domainIndicator();
        Indicator fileHash = fileHashIndicator();
        for (Indicator indicator : java.util.List.of(domain, fileHash)) {
            var record = indicator.snapshot().sources().getFirst();
            String json = objectMapper.writeValueAsString(
                    StixObservedDataProjector.project(indicator.snapshot(), record, T0, T0)
                            .content());
            // schema 的 oneOf 要求 objects 或 object_refs 至少有一個;平台不持久化 SCO,故內嵌 objects
            assertThat(json).contains("\"objects\"");
            assertThat(validate("/sdos/observed-data.json", json))
                    .as("observed-data for %s", indicator.value().type())
                    .isEmpty();
        }
    }

    @Test
    void identityProjectionValidatesAgainstIdentitySchema() {
        String json = objectMapper.writeValueAsString(
                StixIdentityProjector.project(StixProjectionFixtures.sourceSnapshot(), T0, T0)
                        .content());

        assertThat(json).contains("\"identity_class\":\"organization\"");
        assertThat(validate("/sdos/identity.json", json)).isEmpty();
    }

    @Test
    void relationshipContentValidatesAgainstRelationshipSchema() {
        Threat threat = StixProjectionFixtures.malwareFamily();
        Indicator indicator = domainIndicator();
        threat.linkIndicator(indicator.id(), IndicatorRole.C2, T0);
        String json = objectMapper.writeValueAsString(StixRelationshipProjector.content(
                threat.snapshot(), threat.indicators().getFirst(), T0, T0));

        assertThat(json)
                .contains("\"relationship_type\":\"indicates\"")
                .contains("\"source_ref\":\"indicator--" + indicator.id().value() + "\"");
        assertThat(validate("/sros/relationship.json", json)).isEmpty();
    }

    @Test
    void nameTruncationNeverSplitsASurrogatePair() {
        // "URL: " 前綴 5 char + 249 個 'a' = 254 char,第 255 char 起是 4-byte emoji 的高代理
        String padded = "a".repeat(249) + "\uD83D\uDCA3".repeat(4);
        IocValue value = new IocValue(IocType.URL, null, "http://" + padded, padded);
        Indicator indicator = Indicator.create(
                new NewIndicatorCommand(
                        new IndicatorId(UUID.fromString("3c8f2d1b-44e6-4b7c-8a2d-1f3e5b7c9d0a")),
                        PUBLIC,
                        value,
                        report(SOURCE_A, Tlp.CLEAR, Set.of()),
                        new Reputation(70)),
                new Sha256FingerprintStrategy());

        String name = String.valueOf(StixIndicatorProjector.project(
                        indicator.snapshot(), Map.of(SOURCE_A, "OpenPhish (Mock)"), T0, T0.plusSeconds(3600))
                .content()
                .get("name"));

        assertThat(name).doesNotEndWith("\uD83D");
        assertThat(name.chars().noneMatch(c -> Character.isSurrogate((char) c)))
                .as("截斷不得留下未配對的代理字元")
                .isTrue();
    }

    private static Indicator domainIndicator() {
        IocValue value = new IocValue(IocType.DOMAIN, null, "evil.example.com", "evil.example.com");
        Indicator indicator = Indicator.create(
                new NewIndicatorCommand(
                        new IndicatorId(UUID.fromString("1f0d2c4e-93a5-4f6b-8c1d-2e3a4b5c6d7e")),
                        PUBLIC,
                        value,
                        report(SOURCE_A, Tlp.CLEAR, Set.of("phishing", "botnet")),
                        new Reputation(70)),
                new Sha256FingerprintStrategy());
        indicator.mergeFrom(new IndicatorSource(report(SOURCE_B, Tlp.GREEN, Set.of())), new Reputation(60));
        return indicator;
    }

    private static Indicator fileHashIndicator() {
        String sha256 = "2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae";
        IocValue value = new IocValue(IocType.FILE_HASH, IocHashType.SHA256, sha256, sha256);
        return Indicator.create(
                new NewIndicatorCommand(
                        new IndicatorId(UUID.fromString("2b7e1a9c-55d4-4a3b-9f0e-6c5d4b3a2f1e")),
                        PUBLIC,
                        value,
                        report(SOURCE_A, Tlp.CLEAR, Set.of()),
                        new Reputation(70)),
                new Sha256FingerprintStrategy());
    }

    private static IndicatorSourceSnapshot report(SourceId sourceId, Tlp tlp, Set<String> tags) {
        return new IndicatorSourceSnapshot(
                sourceId,
                "evil.example.com",
                Confidence.of(80),
                Severity.HIGH,
                tlp,
                T0,
                T0,
                null,
                RedistributionPolicy.PUBLIC_REDISTRIBUTABLE,
                1,
                SourceRecordStatus.ACTIVE,
                tags,
                java.util.Map.of());
    }
}
