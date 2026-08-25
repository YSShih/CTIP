package com.ctip.domain.indicator.normalization;

import com.ctip.sdk.IocType;

/**
 * 每個 IocType 一個實作(docs/spec/07-domain-intel.md §7.2),由 {@link IocNormalizers} 分派。
 * 輸入為已做共通清理(去空白/零寬/控制字元)的值;格式不合丟 {@link IocFormatException}。
 */
public interface IocNormalizer {

    IocType type();

    String normalize(String cleanedValue);
}
