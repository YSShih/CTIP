package com.ctip.sdk;

/** 嚴重度定性分級,INFO &lt; LOW &lt; MEDIUM &lt; HIGH &lt; CRITICAL(docs/spec/02-ddd-model.md §2.1)。 */
public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /** 取兩者中較高者(合併規則 I8)。 */
    public static Severity max(Severity a, Severity b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
