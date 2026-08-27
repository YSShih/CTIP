package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * roles(表 11)+ role_permissions(表 13,以 {@code @JoinTable} 表達,無獨立 entity)。
 * 參考資料由 V24 種入;RBAC 是兩模型,沒有 domain model(§4.1)。
 */
@Entity
@Table(name = "roles")
class RoleEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 32)
    String code;

    @Column(nullable = false, length = 64)
    String name;

    @Column(columnDefinition = "text")
    String description;

    @Column(name = "tenant_scoped", nullable = false)
    boolean tenantScoped;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    Set<PermissionEntity> permissions;
}
