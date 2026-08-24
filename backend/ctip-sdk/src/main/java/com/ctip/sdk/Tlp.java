package com.ctip.sdk;

/**
 * TLP 2.0 資料分級(docs/spec/07-domain-intel.md §7.7)。
 * 嚴格程度:CLEAR &lt; GREEN &lt; AMBER &lt; AMBER_STRICT &lt; RED。
 * RED 成員保留供合併規則的完整序列使用,但 M1–M3 不得有任何 RED 資料落庫。
 */
public enum Tlp {
    CLEAR,
    GREEN,
    AMBER,
    AMBER_STRICT,
    RED;

    /** 取兩者中較嚴格者(合併規則 I7)。 */
    public static Tlp strictest(Tlp a, Tlp b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /** 本分級是否不嚴於 {@code other}(可見度過濾:tlp &lt;= maxVisibleTlp)。 */
    public boolean isNoStricterThan(Tlp other) {
        return this.ordinal() <= other.ordinal();
    }
}
