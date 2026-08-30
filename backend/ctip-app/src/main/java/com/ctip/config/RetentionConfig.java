package com.ctip.config;

import com.ctip.application.bloom.BloomRetentionService;
import com.ctip.application.port.ClockPort;
import com.ctip.infrastructure.retention.RetentionConnection;
import com.ctip.infrastructure.retention.RetentionPolicy;
import com.ctip.infrastructure.retention.RetentionService;
import com.ctip.infrastructure.retention.RetentionTasks;
import com.ctip.infrastructure.scheduling.RetentionSchedulers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 資料保留清理的裝配(docs/spec/13-platform-ops.md §13.4、§13.5 規則 2)。
 *
 * <p>清理走<strong>第二條連線</strong>,身分是 {@code ctip_retention}:應用角色對
 * {@code audit_logs} 沒有 DELETE 權限(V33 已 REVOKE),用它跑清理會直接被資料庫拒絕。
 *
 * <p>連線刻意<strong>不</strong>宣告成 {@code DataSource} 型別的 bean:Boot 的
 * {@code DataSourceAutoConfiguration} 是 {@code @ConditionalOnMissingBean(DataSource.class)},
 * 多一個 DataSource bean 會讓主資料源整個不建立。故以 {@link RetentionConnection} 包起來。
 */
@Configuration(proxyBeanMethods = false)
public class RetentionConfig {

    @Bean(destroyMethod = "close")
    RetentionConnection retentionConnection(CtipProperties properties, @Value("${spring.datasource.url}") String url) {
        CtipProperties.Retention retention = properties.retention();
        return new RetentionConnection(url, retention.username(), retention.password());
    }

    @Bean
    RetentionTasks retentionTasks(RetentionConnection connection, ClockPort clock, CtipProperties properties) {
        CtipProperties.Retention retention = properties.retention();
        return new RetentionTasks(
                connection.jdbc(),
                clock,
                new RetentionPolicy(
                        retention.auditDays(),
                        retention.rawPayloadDays(),
                        retention.rejectionDays(),
                        retention.deliveryDays(),
                        retention.indicatorDays()));
    }

    @Bean
    RetentionService retentionService(RetentionTasks tasks, BloomRetentionService bloom) {
        return new RetentionService(tasks, bloom);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ctip.scheduler", name = "enabled", havingValue = "true")
    RetentionSchedulers retentionSchedulers(RetentionService retention) {
        return new RetentionSchedulers(retention);
    }
}
