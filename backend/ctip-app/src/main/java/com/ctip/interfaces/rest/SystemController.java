package com.ctip.interfaces.rest;

import com.ctip.CtipApplication;
import com.ctip.interfaces.rest.dto.system.HealthDto;
import com.ctip.interfaces.rest.dto.system.VersionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系統端點(docs/spec/09-api.md §9.1,匿名)。/health 為 liveness 語意
 * (能回應即 UP;依賴健康與 compose healthcheck 走 /actuator/health)。
 */
@RestController
@RequestMapping("/api/v1")
class SystemController {

    @GetMapping("/health")
    HealthDto health() {
        return new HealthDto("UP");
    }

    @GetMapping("/version")
    VersionDto version() {
        String implementation = CtipApplication.class.getPackage().getImplementationVersion();
        return new VersionDto("v1", implementation == null ? "dev" : implementation);
    }
}
