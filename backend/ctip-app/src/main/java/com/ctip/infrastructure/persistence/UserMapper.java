package com.ctip.infrastructure.persistence;

import com.ctip.domain.tenant.TenantId;
import com.ctip.domain.user.EmailAddress;
import com.ctip.domain.user.PasswordHash;
import com.ctip.domain.user.User;
import com.ctip.domain.user.UserId;
import com.ctip.domain.user.UserSnapshot;
import com.ctip.domain.user.UserStatus;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/** User domain ↔ JPA entity。domain 為 private 建構子 + 無 setter,故映射全部手寫。 */
@Mapper(componentModel = "spring")
interface UserMapper {

    default User toDomain(UserEntity e) {
        return User.reconstitute(new UserSnapshot(
                new UserId(e.id),
                new EmailAddress(e.email),
                new PasswordHash(e.passwordHash),
                e.displayName,
                UserStatus.valueOf(e.status),
                new TenantId(e.primaryTenantId),
                e.lastLoginAt,
                e.failedLoginCount,
                e.lockedUntil));
    }

    default void updateEntity(User user, @MappingTarget UserEntity e) {
        e.id = user.id().value();
        e.email = user.email().value();
        e.passwordHash = user.passwordHash().value();
        e.displayName = user.displayName();
        e.status = user.status().name();
        e.primaryTenantId = user.primaryTenantId().value();
        e.lastLoginAt = user.lastLoginAt();
        e.failedLoginCount = (short) user.failedLoginCount();
        e.lockedUntil = user.lockedUntil();
    }
}
