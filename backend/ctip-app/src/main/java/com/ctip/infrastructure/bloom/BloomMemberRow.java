package com.ctip.infrastructure.bloom;

import java.util.UUID;

/** 成員掃描的投影列:只取 id 與 fingerprint,不 hydrate 聚合(10M 成員的 full snapshot 不可行)。 */
public record BloomMemberRow(UUID id, String fingerprint) {}
