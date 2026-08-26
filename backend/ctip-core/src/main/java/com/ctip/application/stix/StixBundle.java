package com.ctip.application.stix;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * bundle 匯出結果(docs/spec/07-domain-intel.md §7.8.5):marking 在前、其餘物件在後。
 * markings 為物件模型、indicatorContents 為落庫 JSON 原文;最終 JSON 組裝在 app 層。
 */
public record StixBundle(String bundleId, List<Map<String, Object>> markings, List<String> indicatorContents) {

    public StixBundle {
        Objects.requireNonNull(bundleId, "bundleId 不得為 null");
        markings = List.copyOf(markings);
        indicatorContents = List.copyOf(indicatorContents);
    }

    public int objectCount() {
        return markings.size() + indicatorContents.size();
    }
}
