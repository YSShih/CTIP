package com.ctip.application.threat;

/**
 * Threat 的識別鍵重複(H1)、外部參照重複(H4)或對已退役的 Threat 再動作
 * → 409 CONFLICT(docs/spec/09-api.md §9.4)。
 */
public class ThreatConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ThreatConflictException(String message) {
        super(message);
    }
}
