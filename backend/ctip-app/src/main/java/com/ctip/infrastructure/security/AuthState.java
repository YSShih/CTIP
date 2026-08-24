package com.ctip.infrastructure.security;

/**
 * 認證狀態(docs/spec/01-architecture.md §1.11)。M1 只有兩態;M2 擴充為完整身分。
 * TLP 可見度由此決定,與方案無關(docs/spec/07-domain-intel.md §7.7)。
 */
public enum AuthState {
    ANONYMOUS,
    AUTHENTICATED
}
