package com.ctip.interfaces.rest.dto.ioc;

import java.util.List;

/** 批次精確驗證結果:found=false 涵蓋查無/不可見/無法正規化,不洩漏存在性。 */
public record LookupResponse(List<Result> results) {

    public record Result(String value, boolean found, IocDto ioc) {}
}
