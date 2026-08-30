package com.ctip.interfaces.rest.dto.auth;

/**
 * 變更密碼的結果。{@code revokedSessions} 是被一併撤銷的 refresh token 數
 * (ADR 0015:改密碼必須撤銷該使用者全部 token family)——呼叫端據此告知使用者
 * 「其他裝置已被登出」,包含發出這次請求的那一個。
 */
public record ChangePasswordResponse(int revokedSessions) {}
