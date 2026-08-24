package com.ctip.sdk;

/**
 * IOC 雜湊型別:當 IOC 本身是某檔案的雜湊時的演算法——這是「資料內容」。
 * 與 {@link FingerprintAlgorithm}(平台去重機制)是兩件不同的事,不得合併(docs/spec/07-domain-intel.md §7.1)。
 */
public enum IocHashType {
    MD5,
    SHA1,
    SHA256,
    SHA512
}
