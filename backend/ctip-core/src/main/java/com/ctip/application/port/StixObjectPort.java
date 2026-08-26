package com.ctip.application.port;

import com.ctip.domain.stix.StixProjection;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * stix_objects 衍生投影的持久化 port(docs/spec/04-data-dictionary.md 表 8、07 §7.8.6)。
 * content 的 JSON 序列化在 infrastructure adapter;讀取回傳落庫的 JSON 原文。
 */
public interface StixObjectPort {

    /** 既有投影的 STIX created(重投影時保持穩定;不存在則 empty)。 */
    Optional<Instant> findCreated(String stixId);

    /** 以 stix_id 為鍵 UPSERT(ux_stix_objects_stix_id)。 */
    void upsert(StixProjection projection);

    /** 落庫的 content JSON 原文。 */
    Optional<String> findContent(String stixId);

    /** 批量讀取(bundle 匯出用):stixId → content JSON,查無者不在結果中。 */
    Map<String, String> findContents(Collection<String> stixIds);
}
