package com.ctip.application.port;

import java.util.UUID;

/** 識別碼產生;domain 禁止直接呼叫 UUID.randomUUID()(ArchUnit 規則 9)。 */
public interface IdGeneratorPort {

    UUID nextId();
}
