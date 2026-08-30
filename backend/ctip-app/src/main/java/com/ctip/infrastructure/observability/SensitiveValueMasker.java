package com.ctip.infrastructure.observability;

import net.logstash.logback.mask.ValueMasker;
import tools.jackson.core.TokenStreamContext;

/**
 * JSON 日誌的遮罩(docs/spec/13-platform-ops.md §13.6)。所有寫進 JSON 的字串值都會經過這裡,
 * 因此 message、MDC、參數陣列一起涵蓋——遮罩規則本身在 {@link SensitiveMasks}(與純文字格式共用一份)。
 */
public class SensitiveValueMasker implements ValueMasker {

    @Override
    public Object mask(TokenStreamContext context, Object value) {
        if (!(value instanceof String text)) {
            return null; // 非字串不處理;回 null 表示「不遮罩」
        }
        String masked = SensitiveMasks.apply(text);
        return masked.equals(text) ? null : masked;
    }
}
