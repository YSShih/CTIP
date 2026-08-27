package com.ctip.testing;

import com.ctip.application.port.IdGeneratorPort;
import java.util.UUID;

/** 測試用確定性 UUID 產生器(§14.7:測試中不得出現 UUID.randomUUID())。 */
public final class SequentialIdGenerator implements IdGeneratorPort {

    private long counter;

    @Override
    public UUID nextId() {
        return new UUID(0x5eedL, ++counter);
    }
}
