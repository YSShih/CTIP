package com.ctip.interfaces.rest;

import com.ctip.application.stix.StixBundle;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * bundle 最終 JSON 組裝(docs/spec/07-domain-intel.md §7.8.5):
 * marking 在前、indicator 在後;indicator content 為落庫 JSON 原文,以樹節點嵌入避免二次轉義。
 */
@Component
class StixBundleWriter {

    private final ObjectMapper objectMapper;

    StixBundleWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String toJson(StixBundle bundle) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "bundle");
        root.put("id", bundle.bundleId());
        ArrayNode objects = root.putArray("objects");
        for (Map<String, Object> marking : bundle.markings()) {
            objects.add(objectMapper.valueToTree(marking));
        }
        for (String content : bundle.indicatorContents()) {
            objects.add(objectMapper.readTree(content));
        }
        return objectMapper.writeValueAsString(root);
    }
}
