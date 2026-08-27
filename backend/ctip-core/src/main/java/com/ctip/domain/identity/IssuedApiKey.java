package com.ctip.domain.identity;

/**
 * 建立當下的一次性回傳(不變量 K1)。{@code plaintext} 不得持久化、不得寫入日誌,
 * 只透過 API 回應交給呼叫端一次。
 */
public record IssuedApiKey(ApiKey apiKey, String plaintext) {}
