package com.ctip.domain.tenant;

import java.util.Objects;

/**
 * Tenant 聚合根,不變量 T1–T4(docs/spec/02-ddd-model.md §2.3)。
 * T1(slug 全域唯一)由 DB 唯一約束強制;T3(public 無 User/ApiKey/…)由對應聚合與 DB CHECK 強制;
 * T4 以「type = SYSTEM ⟺ id = public」在建構時強制(id 唯一 ⇒ SYSTEM 唯一)。
 */
public final class Tenant {

    private final TenantId id;
    private TenantSlug slug;
    private String name;
    private final TenantType type;
    private TenantStatus status;

    private Tenant(TenantId id, TenantSlug slug, String name, TenantType type, TenantStatus status) {
        this.id = Objects.requireNonNull(id);
        this.slug = Objects.requireNonNull(slug);
        this.name = requireName(name);
        this.type = Objects.requireNonNull(type);
        this.status = Objects.requireNonNull(status);
        if ((type == TenantType.SYSTEM) != id.isPublic()) {
            throw new IllegalArgumentException("type=SYSTEM 僅限 public tenant,且 public tenant 恆為 SYSTEM(T2/T4)");
        }
    }

    public static Tenant create(TenantId id, TenantSlug slug, String name, TenantType type) {
        return new Tenant(id, slug, name, type, TenantStatus.ACTIVE);
    }

    /** 由持久化狀態重建(不重放事件,僅重新驗證不變量)。 */
    public static Tenant reconstitute(TenantId id, TenantSlug slug, String name, TenantType type, TenantStatus status) {
        return new Tenant(id, slug, name, type, status);
    }

    /** T2:public tenant 不可更名。 */
    public void rename(String newName) {
        rejectIfPublic("rename");
        this.name = requireName(newName);
    }

    /** T2:public tenant 不可停用(docs/spec/03-diagrams.md §3.2.3)。 */
    public void suspend() {
        rejectIfPublic("suspend");
        this.status = TenantStatus.SUSPENDED;
    }

    public boolean isPublic() {
        return id.isPublic();
    }

    private void rejectIfPublic(String operation) {
        if (isPublic()) {
            throw new IllegalStateException("public system tenant 不可 " + operation + "(不變量 T2)");
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不得為空");
        }
        return name;
    }

    public TenantId id() {
        return id;
    }

    public TenantSlug slug() {
        return slug;
    }

    public String name() {
        return name;
    }

    public TenantType type() {
        return type;
    }

    public TenantStatus status() {
        return status;
    }
}
