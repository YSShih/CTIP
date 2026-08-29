package com.ctip.domain.stix;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * STIX id 的產生規則(docs/spec/07-domain-intel.md §7.8.2:使用 domain 物件自己的 UUID,
 * 保證穩定且可逆)。
 *
 * <p>{@code observed-data} 與 {@code relationship} 沒有對應的 domain UUID——它們的識別是
 * 一組欄位(indicator + source、relationship 三元組)。這裡以那組欄位的
 * <strong>名稱型 UUID</strong> 產生:同樣的輸入永遠得到同樣的 id,重投影才會是 UPSERT 而非
 * 每次新增一列。不得改用 {@code UUID.randomUUID()}(ArchUnit 規則 9,且會破壞冪等)。
 */
final class StixIds {

    private StixIds() {}

    static UUID deterministic(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
