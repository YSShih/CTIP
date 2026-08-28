package com.ctip.application.port;

import com.ctip.application.ingestion.ImportFormat;
import com.ctip.sdk.RawThreatRecord;
import java.util.List;

/**
 * 匯入檔的解碼(docs/spec/09-api.md §9.7:{@code text/csv} 或 STIX 2.1 bundle)。
 *
 * <p>只負責「位元組 → {@link RawThreatRecord}」。逐筆的資料品質(型別推斷、正規化、
 * 私有 IP、長度、allowlist、去重、合併)一律由既有 pipeline 負責——
 * §8.3 明文「不需要第二套資料品質邏輯」。
 *
 * <p>之所以是 port:CSV 由 {@code ManualSubmissionAdapter} 解(SDK 契約,無外部相依),
 * 而 STIX bundle 需要 JSON 解析器,那只存在於 ctip-app(ctip-core 無 JSON 相依)。
 */
public interface ImportPayloadParserPort {

    /**
     * 記錄未指定觀測時間時,實作以 {@code ClockPort} 取提交時間作為預設值
     * ——時間來源留在實作端,adapter 本身仍只吃傳入的值、保持確定性。
     *
     * @throws IllegalArgumentException 格式錯誤(整批失敗,job 記為 FAILURE)
     */
    List<RawThreatRecord> parse(ImportFormat format, String payload);
}
