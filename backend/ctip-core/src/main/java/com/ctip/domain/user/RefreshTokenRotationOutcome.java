package com.ctip.domain.user;

/** {@link User#rotateRefreshToken} 的判定結果。 */
public enum RefreshTokenRotationOutcome {
    /** 正常輪替:舊枚標記已使用並撤銷,新枚簽發。 */
    ROTATED,
    /** 不變量 U5:偵測到重用,該 family 已全數撤銷。 */
    REUSE_DETECTED,
    /** 已撤銷或已過期,拒絕但不撤銷 family。 */
    INVALID
}
