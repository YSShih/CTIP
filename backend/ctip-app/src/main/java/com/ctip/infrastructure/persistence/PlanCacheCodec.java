package com.ctip.infrastructure.persistence;

import com.ctip.domain.plan.Plan;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link Plan} 與快取字串之間的轉換({@code CachePort} 的值一律是字串,序列化屬 infrastructure)。
 *
 * <p>用專屬的 {@link ObjectMapper} 而非注入 Boot 的那個:快取內容的相容性只該受本類別影響,
 * 不該因為某天有人為了 API 回應調整全域 Jackson 設定而讓既有的快取值解不開。
 * Boot 4 的 Jackson 是 3.x,套件為 {@code tools.jackson..}(06 §6.3.6 第 6 條)。
 *
 * <p>{@link #decode} 解不開時回 empty 而不是往上丟:滾動升級期間,新版寫入的欄位對舊版是未知的
 * (反之亦然),兩個版本會同時對著同一個 Redis 讀寫。解不開就當快取未命中重新載入,
 * 那是 {@code CachePort} 契約允許的;讓它變成例外則是「升級到一半整個 API 掛掉」。
 */
final class PlanCacheCodec {

    private static final Logger log = LoggerFactory.getLogger(PlanCacheCodec.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private PlanCacheCodec() {}

    static String encode(Plan plan) {
        return MAPPER.writeValueAsString(plan);
    }

    static Optional<Plan> decode(String json) {
        try {
            return Optional.of(MAPPER.readValue(json, Plan.class));
        } catch (JacksonException | IllegalArgumentException e) {
            log.warn("快取中的方案無法解析,改為重新載入", e);
            return Optional.empty();
        }
    }
}
