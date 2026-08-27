package com.ctip.domain.user;

/** Refresh token 撤銷原因(docs/spec/04-data-dictionary.md 表 15)。 */
public enum RevokedReason {
    LOGOUT,
    ROTATED,
    REUSE_DETECTED,
    ADMIN,
    EXPIRED_CLEANUP
}
