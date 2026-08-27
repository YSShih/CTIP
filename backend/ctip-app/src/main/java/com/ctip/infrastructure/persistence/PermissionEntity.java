package com.ctip.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** permissions(表 12)。參考資料,由 V24 種入;無 updated_at(表定義如此)。 */
@Entity
@Table(name = "permissions")
class PermissionEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 64)
    String code;

    @Column(columnDefinition = "text")
    String description;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
