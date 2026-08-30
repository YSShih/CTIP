package com.ctip.infrastructure.audit;

import com.ctip.domain.audit.AuditActorType;
import com.ctip.domain.tenant.TenantId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 從 handler 傳給稽核 filter 的兩個訊號。
 *
 * <p>filter 排在 handler 之外,兩件事它自己看不到:
 * <ul>
 *   <li><strong>行為者</strong>:{@code /auth/*} 是匿名端點,登入成功之後才知道是誰
 *       ——沒有這個訊號,整張表裡的 {@code LOGIN} 都不會有 actor。</li>
 *   <li><strong>回應筆數</strong>:{@code IOC_DOWNLOAD} 的判準是「回應筆數 > 單頁上限的一半」
 *       (§13.5 觸發點對照表),而筆數只有組完回應的那個方法知道。</li>
 * </ul>
 *
 * <p>以 request attribute 傳遞而不是新增建構子參數:controller 的建構子已在 checkstyle
 * 的參數上限邊緣(§1.8 規則 3),而這是稽核的橫切關注,不屬於任何一個 controller 的協作者。
 */
public final class AuditSignals {

    private static final String ACTOR = AuditSignals.class.getName() + ".actor";
    private static final String PAGE = AuditSignals.class.getName() + ".page";

    private AuditSignals() {}

    /** handler 在確定身分之後呼叫(登入、輪替、登出)。 */
    public static void actor(AuditActorType type, UUID actorId, TenantId tenantId) {
        set(ACTOR, new Actor(type, actorId, tenantId));
    }

    /** handler 回傳一頁資料之後呼叫。 */
    public static void page(int returned, int maxPageSize) {
        set(PAGE, new Page(returned, maxPageSize));
    }

    static Optional<Actor> currentActor() {
        return get(ACTOR, Actor.class);
    }

    static Optional<Page> currentPage() {
        return get(PAGE, Page.class);
    }

    record Actor(AuditActorType type, UUID actorId, TenantId tenantId) {}

    /** §13.5:回應筆數超過單頁上限的一半即視為下載,取樣 100%。 */
    record Page(int returned, int maxPageSize) {

        boolean isDownload() {
            return maxPageSize > 0 && returned * 2 > maxPageSize;
        }
    }

    private static void set(String key, Object value) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            attributes.setAttribute(key, value, RequestAttributes.SCOPE_REQUEST);
        }
    }

    private static <T> Optional<T> get(String key, Class<T> type) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
}
