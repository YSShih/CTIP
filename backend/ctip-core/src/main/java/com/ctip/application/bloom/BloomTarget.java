package com.ctip.application.bloom;

import com.ctip.domain.bloom.BloomParameters;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.tenant.TenantId;

/** 一份待生成的 Bloom:哪個 scope 的哪個租戶、要用什麼參數。 */
public record BloomTarget(BloomScope scope, TenantId tenantId, BloomParameters parameters) {}
