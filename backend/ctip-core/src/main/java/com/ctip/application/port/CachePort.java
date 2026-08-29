package com.ctip.application.port;

import java.time.Duration;
import java.util.Optional;

/**
 * 分散式快取(docs/spec/06-tech-stack.md §6.5、01 §1.6 的 out-port 清單)。
 *
 * <p><strong>值一律是字串</strong>:序列化由呼叫端(infrastructure)決定。這不是簡化,
 * 而是這個 port 的邊界條件——§6.5 要求「Redis → Valkey 的替換只需改 infrastructure 實作」,
 * 而泛型化的 {@code get(key, Class<T>)} 會把序列化器的行為(哪些型別可還原、日期怎麼寫)
 * 綁進 port 的契約,替換實作時就不再只是換一個 class。Lettuce／Redis 型別亦不得出現於此
 * (phase-17「不得做的事」;ArchUnit 規則 11 會擋)。
 *
 * <p>快取一律是<strong>可有可無</strong>的:任何實作都可以回 {@link Optional#empty()},
 * 呼叫端必須能重新載入。因此本 port 沒有「快取失敗」的例外——連不上 Redis 時
 * 退化為每次都重新載入,而不是讓請求失敗。
 */
public interface CachePort {

    Optional<String> get(String key);

    /**
     * @param ttl 存活時間;到期後 {@link #get} 必須回 empty(逐出由實作負責,呼叫端不清掃)
     */
    void put(String key, String value, Duration ttl);

    /** 明確失效。跨實例的立即生效只有這條路徑能保證,TTL 到期是最後防線。 */
    void evict(String key);
}
