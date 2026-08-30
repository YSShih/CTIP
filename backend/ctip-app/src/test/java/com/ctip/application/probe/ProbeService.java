package com.ctip.application.probe;

/**
 * 測試用的 application service:類別名以 {@code Service} 結尾且位於 {@code com.ctip.application..} 下,
 * 因此符合 {@code TracingAspect} 的第一個切入點。生產碼不使用它。
 */
public class ProbeService {

    public String work() {
        return "done";
    }

    public void explode() {
        throw new IllegalStateException("probe");
    }
}
