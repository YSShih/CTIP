package com.ctip.interfaces.rest.dto.apikey;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 建立成功的回應。{@code key} 為完整金鑰原文,<strong>只在此回傳一次</strong>,
 * 之後永不可查(不變量 K1);遺失只能撤銷後重建。
 */
public record IssuedApiKeyDto(
        @Schema(example = "ctip_mvp_aB3xY9kQ7fLm2pR8sT4uV6wX0yZ1cD5e")
        String key,

        ApiKeyDto apiKey) {}
