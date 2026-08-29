package com.ctip.interfaces.rest.dto.notification;

/**
 * 建立回應:設定 + <strong>只此一次</strong>的簽章密鑰原文(不變量 W2 的對外契約)。
 * 之後任何端點都不再吐出它;遺失只能重建一個 webhook。
 */
public record IssuedWebhookDto(String secret, WebhookDto webhook) {}
