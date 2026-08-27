package com.ctip.domain.event;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.TokenFamilyId;
import com.ctip.domain.user.UserId;

/**
 * User 聚合發佈的 M2 事件(docs/spec/02-ddd-model.md §2.4)。
 * 事件不攜帶 email / 姓名等個資——與 JWT claims 同一原則(§10.4)。
 */
public interface UserEvents {

    record UserRegistered(TenantId tenantId, UserId userId) implements DomainEvent {}

    /** 不變量 U5:已使用的 refresh token 再次出現,該 family 已全數撤銷。 */
    record TokenReuseDetected(TenantId tenantId, UserId userId, TokenFamilyId familyId) implements DomainEvent {}
}
