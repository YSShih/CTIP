package com.ctip.sdk;

/**
 * 指紋演算法:對 IOC 正規化值計算去重指紋所用的演算法——這是「平台機制」。
 * 與 {@link IocHashType}(IOC 資料內容)是兩件不同的事,不得合併(docs/spec/07-domain-intel.md §7.1)。
 */
public enum FingerprintAlgorithm {
    SHA256,
    SHA512
}
