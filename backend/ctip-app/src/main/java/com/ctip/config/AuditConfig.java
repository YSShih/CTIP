package com.ctip.config;

import com.ctip.application.port.AuditLogPort;
import com.ctip.application.port.AuditPort;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import com.ctip.infrastructure.audit.AuditAccessFilter;
import com.ctip.infrastructure.audit.AuditContext;
import com.ctip.infrastructure.audit.AuditEventListener;
import com.ctip.infrastructure.audit.AuditSampler;
import com.ctip.infrastructure.audit.AuditWriter;
import com.ctip.infrastructure.security.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 稽核的裝配(docs/spec/13-platform-ops.md §13.5)。
 *
 * <p>26 種行為由兩個消費端承擔:{@link AuditAccessFilter}(17 種以請求為觸發點)與
 * {@link AuditEventListener}(9 種以 domain event 為觸發點)。<strong>業務服務本身不知道稽核存在</strong>
 * ——這是刻意的:§13.1「發佈端程式碼永不修改」的同一個原則,也讓稽核寫入的失敗
 * 在結構上不可能傳回業務路徑。
 */
@Configuration(proxyBeanMethods = false)
public class AuditConfig {

    @Bean
    AuditSampler auditSampler(CtipProperties properties) {
        return new AuditSampler(properties.audit().sampleReadRate());
    }

    /**
     * 回傳型別刻意是實作而不是 {@code AuditPort}:測試要等佇列排空
     * ({@code awaitQuiescence}),而以介面宣告的 bean 依型別注入不到實作。
     */
    @Bean
    AuditWriter auditWriter(AuditLogPort auditLogs, AuditContext context, ClockPort clock, IdGeneratorPort ids) {
        return new AuditWriter(auditLogs, context, clock, ids);
    }

    @Bean
    AuditEventListener auditEventListener(AuditPort audit) {
        return new AuditEventListener(audit);
    }

    @Bean
    AuditAccessFilter auditAccessFilter(
            AuditPort audit, AuditSampler sampler, ObjectProvider<TenantContext> tenantContext) {
        return new AuditAccessFilter(audit, sampler, tenantContext);
    }

    /** 只能經 security chain 執行一次;比照 {@code CtipAuthenticationFilter} 關閉自動註冊。 */
    @Bean
    FilterRegistrationBean<AuditAccessFilter> auditAccessFilterRegistration(AuditAccessFilter filter) {
        FilterRegistrationBean<AuditAccessFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
