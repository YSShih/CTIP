package com.ctip.application.identity;

import com.ctip.domain.user.RefreshToken;

/** 新簽發的 refresh token:實體(只含雜湊)+ 一次性原文。 */
public record IssuedRefreshToken(RefreshToken token, String plaintext) {}
