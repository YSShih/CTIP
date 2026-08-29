package com.ctip.application.ingestion;

import com.ctip.domain.fingerprint.Fingerprint;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IocValue;
import com.ctip.domain.stix.StixProjection;
import com.ctip.sdk.IocHashType;
import com.ctip.sdk.IocType;
import com.ctip.sdk.RawThreatRecord;
import java.util.ArrayList;
import java.util.List;
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
    private IndicatorId searchIndexTarget;
    private final List<StixProjection> stixProjections = new ArrayList<>();
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

    /** 本筆記錄需要重新索引的 indicator(stage 11 標記,交易提交後才寫出;null = 不需索引)。 */
    public IndicatorId searchIndexTarget() {
        return searchIndexTarget;
    }

    public void searchIndexTarget(IndicatorId id) {
        this.searchIndexTarget = id;
    }

    /** 本筆記錄產生的 STIX 投影:indicator 一筆 + 每個來源記錄一筆 observed-data + 來源的 identity。 */
    public List<StixProjection> stixProjections() {
        return List.copyOf(stixProjections);
    }

    public void addStixProjection(StixProjection projection) {
        stixProjections.add(projection);
    }

    /** 投影建構失敗時只保留已成功的部分會產生半套物件,因此一律整筆丟棄(§7.8.6)。 */
    public void clearStixProjections() {
        stixProjections.clear();
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
