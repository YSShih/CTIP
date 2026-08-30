package com.ctip.domain.audit;

/** 稽核紀錄的行為者類型(docs/spec/04-data-dictionary.md 表 27 的 {@code actor_type})。 */
public enum AuditActorType {
    ANONYMOUS,
    USER,
    API_KEY,
    SYSTEM
}
