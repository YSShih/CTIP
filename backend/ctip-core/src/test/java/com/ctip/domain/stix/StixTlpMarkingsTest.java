package com.ctip.domain.stix;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.sdk.Tlp;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 五個 TLP 2.0 marking UUID 與 extension-definition ID 以字面字串斷言完全相符
 * (docs/spec/07-domain-intel.md §7.8.4;OASIS 固定值,不得自行產生)。
 */
@Tag("unit")
class StixTlpMarkingsTest {

    @Test
    void markingIdsMatchOasisDefinitionsLiterally() {
        assertThat(StixTlpMarkings.markingId(Tlp.CLEAR))
                .isEqualTo("marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487");
        assertThat(StixTlpMarkings.markingId(Tlp.GREEN))
                .isEqualTo("marking-definition--bab4a63c-aed9-4cf5-a766-dfca5abac2bb");
        assertThat(StixTlpMarkings.markingId(Tlp.AMBER))
                .isEqualTo("marking-definition--55d920b0-5e8b-4f79-9ee9-91f868d9b421");
        assertThat(StixTlpMarkings.markingId(Tlp.AMBER_STRICT))
                .isEqualTo("marking-definition--939a9414-2ddd-4d32-a0cd-375ea402b003");
        assertThat(StixTlpMarkings.markingId(Tlp.RED))
                .isEqualTo("marking-definition--e828b379-4e03-4974-9ac4-e53a884c97c1");
    }

    @Test
    void extensionDefinitionIdMatchesOasisLiterally() {
        assertThat(StixTlpMarkings.TLP_EXTENSION_DEFINITION_ID)
                .isEqualTo("extension-definition--60a3c5c5-0d10-413e-aab3-9e08dde9e88d");
    }

    @Test
    void markingObjectMatchesSpecFormatVerbatim() {
        Map<String, Object> clear = StixTlpMarkings.marking(Tlp.CLEAR);
        assertThat(clear.get("type")).isEqualTo("marking-definition");
        assertThat(clear.get("spec_version")).isEqualTo("2.1");
        assertThat(clear.get("id")).isEqualTo("marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487");
        assertThat(clear.get("created")).isEqualTo("2022-10-01T00:00:00.000Z");
        assertThat(clear.get("name")).isEqualTo("TLP:CLEAR");
        assertThat(clear.get("extensions"))
                .isEqualTo(Map.of(
                        "extension-definition--60a3c5c5-0d10-413e-aab3-9e08dde9e88d",
                        Map.of("extension_type", "property-extension", "tlp_2_0", "clear")));
        // 屬性順序依 §7.8.4 原樣輸出
        assertThat(clear.keySet()).containsExactly("type", "spec_version", "id", "created", "name", "extensions");
    }

    @Test
    void namesAndLabelsFollowTlp20Convention() {
        assertThat(StixTlpMarkings.markingName(Tlp.AMBER_STRICT)).isEqualTo("TLP:AMBER+STRICT");
        assertThat(StixTlpMarkings.tlp20Label(Tlp.AMBER_STRICT)).isEqualTo("amber+strict");
        assertThat(StixTlpMarkings.markingName(Tlp.RED)).isEqualTo("TLP:RED");
        assertThat(StixTlpMarkings.tlp20Label(Tlp.RED)).isEqualTo("red");
    }

    @Test
    void markingByStixIdResolvesAllFiveAndRejectsUnknown() {
        for (Tlp tlp : Tlp.values()) {
            assertThat(StixTlpMarkings.markingByStixId(StixTlpMarkings.markingId(tlp)))
                    .contains(StixTlpMarkings.marking(tlp));
        }
        assertThat(StixTlpMarkings.markingByStixId("marking-definition--00000000-0000-0000-0000-000000000000"))
                .isEmpty();
    }
}
