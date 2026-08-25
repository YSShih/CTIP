package com.ctip.application.ingestion;

import com.ctip.domain.indicator.normalization.IocNormalizers;
import com.ctip.sdk.IocType;

/**
 * Stage 1 Parse:共通清理、型別解析(來源宣告優先,否則推斷)、STIX revoked 旗標
 * (ADR 0003 決策 4:rawPayload["revoked"] == true → 該來源記錄 RETRACTED)。
 * 來源專屬的格式解析在 adapter 內完成(§8.2)。
 */
public final class ParseStage implements IngestionStage {

    private final IocNormalizers normalizers;

    public ParseStage(IocNormalizers normalizers) {
        this.normalizers = normalizers;
    }

    @Override
    public String name() {
        return "Parse";
    }

    @Override
    public IngestionContext execute(IngestionContext context) {
        String rawValue = context.raw().rawValue();
        if (rawValue == null || rawValue.isBlank()) {
            context.reject(RejectionReason.MALFORMED_VALUE, "rawValue 為空");
            return context;
        }
        String cleaned = normalizers.clean(rawValue);
        if (cleaned.isEmpty()) {
            context.reject(RejectionReason.MALFORMED_VALUE, "清理後為空值");
            return context;
        }
        context.cleanedValue(cleaned);
        IocType type = context.raw().declaredType() != null ? context.raw().declaredType() : normalizers.infer(cleaned);
        if (type == null) {
            context.reject(RejectionReason.UNKNOWN_TYPE, "無法推斷型別且來源未宣告:" + cleaned);
            return context;
        }
        context.type(type);
        context.hashType(context.raw().declaredHashType());
        context.retracted(context.raw().rawPayload() != null
                && Boolean.TRUE.equals(context.raw().rawPayload().get("revoked")));
        return context;
    }
}
