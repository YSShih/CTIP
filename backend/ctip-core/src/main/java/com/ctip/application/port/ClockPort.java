package com.ctip.application.port;

import java.time.Instant;

/** 時間來源;domain 禁止直接呼叫 Instant.now()(ArchUnit 規則 9),一律經此 port 注入。 */
public interface ClockPort {

    Instant now();
}
