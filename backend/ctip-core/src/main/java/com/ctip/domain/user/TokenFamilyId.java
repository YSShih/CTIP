package com.ctip.domain.user;

import java.util.Objects;
import java.util.UUID;

/** Refresh token 輪替家族。重用偵測以此為單位撤銷(不變量 U5)。 */
public record TokenFamilyId(UUID value) {

    public TokenFamilyId {
        Objects.requireNonNull(value, "value 不得為 null");
    }
}
