package com.ctip.application.stix;

/**
 * bundle 匯出上限(§7.8.5)。M1 以 property 預設承載(plans 表為 M2;
 * 預設 1000 取 FREE 方案值,10 §10.6);Phase 14 改由 plans.stix_export_max_objects 查表。
 */
public record StixExportSettings(int maxObjects) {

    public StixExportSettings {
        if (maxObjects <= 0) {
            throw new IllegalArgumentException("maxObjects 必須為正數");
        }
    }
}
