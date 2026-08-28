package com.ctip.application.indicator;

/**
 * 對 public tenant 的公開情資做誤判回報(§9.7「誤判回報的作用域」)→ 403 FORBIDDEN。
 *
 * <p>全平台只有一列 {@code MANUAL} 來源、{@code indicator_sources} 又是
 * {@code UNIQUE (indicator_id, source_id)},因此對一筆公開 Indicator 建立 MANUAL 誤判列
 * 改到的是<strong>共用的公開資料</strong>,第二個租戶回報同一筆還會直接撞唯一約束。
 * 對公開情資的申訴屬於平台營運流程,不是 API 操作(ADR 0019 第 3 節)。
 */
public class PublicIntelNotReportableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PublicIntelNotReportableException(String message) {
        super(message);
    }
}
