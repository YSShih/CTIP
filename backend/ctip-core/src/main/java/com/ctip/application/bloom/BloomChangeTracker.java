package com.ctip.application.bloom;

import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.tenant.TenantId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 「這個 scope 自上次生成以來有沒有新成員」的訊號,由 {@code BloomUpdateStage} 在攝取管線寫入。
 *
 * <p><strong>它不是成員的真相來源</strong>——真相是資料庫。記憶體緩衝若因重啟遺失,
 * 會產生 Bloom false negative(client 以為安全),那是本規格最不能出的錯。
 * 這裡只用來<strong>跳過沒有變動的 scope</strong>:每小時產生空 delta 會白白吃掉
 * 24 段的 chain 預算(§11.3),逼 client 無謂地重下 full。
 *
 * <p>fail-safe:沒有被明確標記為「乾淨」的 scope 一律視為有變動(剛啟動時即如此)。
 */
@Component
public class BloomChangeTracker {

    private final Set<String> clean = ConcurrentHashMap.newKeySet();

    public void markChanged(BloomScope scope, TenantId tenantId) {
        clean.remove(key(scope, tenantId));
    }

    public boolean hasChanges(BloomScope scope, TenantId tenantId) {
        return !clean.contains(key(scope, tenantId));
    }

    /** 生成成功後呼叫;之後若沒有新的攝取,下一次排程就會跳過這個 scope。 */
    public void markGenerated(BloomScope scope, TenantId tenantId) {
        clean.add(key(scope, tenantId));
    }

    private static String key(BloomScope scope, TenantId tenantId) {
        return scope.name() + ':' + tenantId.value();
    }
}
