package com.ctip.domain.stix;

import com.ctip.sdk.Tlp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TLP 2.0 marking-definition 固定值(docs/spec/07-domain-intel.md §7.8.4)。
 * UUID 由 OASIS 定義,必須原樣使用——任何以 UUID.randomUUID() 產生 marking id 的程式碼皆為違規。
 * 已對照 OASIS cti-stix-common-objects 的 tlp-2.0 examples 查證(2026-08-21)。
 */
public final class StixTlpMarkings {

    /** TLP 2.0 擴充定義 ID(extension-definition,OASIS 固定值)。 */
    public static final String TLP_EXTENSION_DEFINITION_ID =
            "extension-definition--60a3c5c5-0d10-413e-aab3-9e08dde9e88d";

    /** 所有 marking-definition 的 created 固定為此值。 */
    public static final String MARKING_CREATED = "2022-10-01T00:00:00.000Z";

    public static final String CLEAR_ID = "marking-definition--94868c89-83c2-464b-929b-a1a8aa3c8487";
    public static final String GREEN_ID = "marking-definition--bab4a63c-aed9-4cf5-a766-dfca5abac2bb";
    public static final String AMBER_ID = "marking-definition--55d920b0-5e8b-4f79-9ee9-91f868d9b421";
    public static final String AMBER_STRICT_ID = "marking-definition--939a9414-2ddd-4d32-a0cd-375ea402b003";
    public static final String RED_ID = "marking-definition--e828b379-4e03-4974-9ac4-e53a884c97c1";

    private StixTlpMarkings() {}

    /** indicator 的 object_marking_refs 引用值(§7.8.2)。 */
    public static String markingId(Tlp tlp) {
        return switch (tlp) {
            case CLEAR -> CLEAR_ID;
            case GREEN -> GREEN_ID;
            case AMBER -> AMBER_ID;
            case AMBER_STRICT -> AMBER_STRICT_ID;
            case RED -> RED_ID;
        };
    }

    /** TLP 2.0 顯示名稱(TLP:AMBER+STRICT 的 '+' 是 OASIS 定義的一部分)。 */
    public static String markingName(Tlp tlp) {
        return switch (tlp) {
            case CLEAR -> "TLP:CLEAR";
            case GREEN -> "TLP:GREEN";
            case AMBER -> "TLP:AMBER";
            case AMBER_STRICT -> "TLP:AMBER+STRICT";
            case RED -> "TLP:RED";
        };
    }

    /** extensions 區塊內的 tlp_2_0 標籤(小寫)。 */
    public static String tlp20Label(Tlp tlp) {
        return switch (tlp) {
            case CLEAR -> "clear";
            case GREEN -> "green";
            case AMBER -> "amber";
            case AMBER_STRICT -> "amber+strict";
            case RED -> "red";
        };
    }

    /** marking-definition 物件(§7.8.4 的格式原樣輸出;一律 TLP 2.0 形式,含 extensions 區塊)。 */
    public static Map<String, Object> marking(Tlp tlp) {
        Map<String, Object> marking = new LinkedHashMap<>();
        marking.put("type", "marking-definition");
        marking.put("spec_version", "2.1");
        marking.put("id", markingId(tlp));
        marking.put("created", MARKING_CREATED);
        marking.put("name", markingName(tlp));
        Map<String, Object> tlp20 = new LinkedHashMap<>();
        tlp20.put("extension_type", "property-extension");
        tlp20.put("tlp_2_0", tlp20Label(tlp));
        marking.put("extensions", Map.of(TLP_EXTENSION_DEFINITION_ID, tlp20));
        return marking;
    }

    /** 依 stixId 反查(GET /api/v1/stix/{stixId} 對 marking 直接由常數供應,不落 stix_objects)。 */
    public static java.util.Optional<Map<String, Object>> markingByStixId(String stixId) {
        return List.of(Tlp.values()).stream()
                .filter(tlp -> markingId(tlp).equals(stixId))
                .findFirst()
                .map(StixTlpMarkings::marking);
    }
}
