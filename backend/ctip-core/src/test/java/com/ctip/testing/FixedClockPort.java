package com.ctip.testing;

import com.ctip.application.port.ClockPort;
import java.time.Instant;

/** 測試專用固定時鐘(docs/spec/14-testing.md §14.7:測試中不得出現 Instant.now())。 */
public final class FixedClockPort implements ClockPort {

    public static final Instant DEFAULT_NOW = Instant.parse("2026-08-21T12:00:00Z");

    private final Instant fixed;

    public FixedClockPort(Instant fixed) {
        this.fixed = fixed;
    }

    public static FixedClockPort at(Instant fixed) {
        return new FixedClockPort(fixed);
    }

    @Override
    public Instant now() {
        return fixed;
    }
}
