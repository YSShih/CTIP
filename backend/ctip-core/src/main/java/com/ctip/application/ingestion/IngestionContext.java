package com.ctip.application.ingestion;

import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IocValue;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import java.util.Objects;

/**
 * 單筆記錄流經 pipeline 的可變狀態(docs/spec/08-ingestion-sdk.md §8.2)。
 * 任一 stage 呼叫 {@link #reject} 後 pipeline 短路,批次處理器把拒絕寫入 ingestion_rejections。
 */
public final class IngestionContext {

    private final RawThreatRecord raw;
    private final SourceContext source;
    private final BatchState batch;

    private String cleanedValue;
    private IocType type;
    private IocHashType hashType;
    private boolean retracted;
    private String normalizedValue;
    private Fingerprint fingerprint;
    private IocValue iocValue;
    private Indicator indicator;
    private boolean merged;
    private RejectionReason rejectionReason;
    private String rejectionDetail;

    public IngestionContext(RawThreatRecord raw, SourceContext source, BatchState batch) {
        this.raw = Objects.requireNonNull(raw);
        this.source = Objects.requireNonNull(source);
        this.batch = Objects.requireNonNull(batch);
    }

    public void reject(RejectionReason reason, String detail) {
        this.rejectionReason = reason;
        this.rejectionDetail = detail;
    }

    public boolean rejected() {
        return rejectionReason != null;
    }

    public RawThreatRecord raw() {
        return raw;
    }

    public SourceContext source() {
        return source;
    }

    public BatchState batch() {
        return batch;
    }

    public String cleanedValue() {
        return cleanedValue;
    }

    public void cleanedValue(String value) {
        this.cleanedValue = value;
    }

    public IocType type() {
        return type;
    }

    public void type(IocType type) {
        this.type = type;
    }

    public IocHashType hashType() {
        return hashType;
    }

    public void hashType(IocHashType hashType) {
        this.hashType = hashType;
    }

    public boolean retracted() {
        return retracted;
    }

    public void retracted(boolean retracted) {
        this.retracted = retracted;
    }

    public String normalizedValue() {
        return normalizedValue;
    }

    public void normalizedValue(String value) {
        this.normalizedValue = value;
    }

    public Fingerprint fingerprint() {
        return fingerprint;
    }

    public void fingerprint(Fingerprint fingerprint) {
        this.fingerprint = fingerprint;
    }

    public IocValue iocValue() {
        return iocValue;
    }

    public void iocValue(IocValue iocValue) {
        this.iocValue = iocValue;
    }

    public Indicator indicator() {
        return indicator;
    }

    public void indicator(Indicator indicator) {
        this.indicator = indicator;
    }

    public boolean merged() {
        return merged;
    }

    public void merged(boolean merged) {
        this.merged = merged;
    }

    public RejectionReason rejectionReason() {
        return rejectionReason;
    }

    public String rejectionDetail() {
        return rejectionDetail;
    }
}
