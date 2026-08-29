package com.ctip.application.port;

import com.ctip.domain.stix.StixRelationship;
import java.util.List;
import java.util.Optional;

/** stix_relationships 衍生投影的持久化 port(docs/spec/04-data-dictionary.md 表 9、07 §7.8.6)。 */
public interface StixRelationshipPort {

    /**
     * 以某個 target(一個 Threat 的 STIX id)為單位同步關聯:
     * 傳入的一律 UPSERT,同一 target 底下不在清單中的一律刪除。
     *
     * <p>解除關聯必須讓投影跟著消失——只做 UPSERT 會讓 {@code stix_relationships} 永遠只增不減,
     * 對外就會宣稱一個早已解除的關聯仍然成立。
     */
    void syncForTarget(String targetRef, List<StixRelationship> relationships);

    /** {@code GET /stix/{stixId}} 用:三元組;對外 JSON 由投影規則重建(表 9 無 content 欄)。 */
    Optional<StixRelationship> findByStixId(String stixId);
}
