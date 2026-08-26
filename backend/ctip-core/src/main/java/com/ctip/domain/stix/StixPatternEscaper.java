package com.ctip.domain.stix;

/**
 * STIX Patterning 字串常值的跳脫(docs/spec/07-domain-intel.md §7.8.3):
 * 單引號與反斜線以反斜線跳脫(\'、\\)。先跳脫反斜線再跳脫引號,避免二次跳脫。
 */
public final class StixPatternEscaper {

    private StixPatternEscaper() {}

    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
