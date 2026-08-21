package com.ctip.config;

import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.ApplicationDataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.autoconfigure.init.SqlInitializationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

/**
 * 開發樣本資料載入(docs/spec/05-environment.md §5.9):db/seed/ 以 spring.sql.init
 * 僅於 mvp / dev profile 載入。
 *
 * <p>Boot 預設的 script initializer 在 Flyway 之前執行,種子會落在尚未建立的表上;
 * 因此自定義同型別的 initializer(autoconfig 因 marker 介面退讓)並宣告依賴
 * {@code flywayInitializer},確保 migration 先行。設定值仍取自 spring.sql.init.*。
 */
@Configuration(proxyBeanMethods = false)
@Profile({"mvp", "dev"})
@EnableConfigurationProperties(SqlInitializationProperties.class)
class SeedDataConfig {

    @Bean
    @DependsOn("flywayInitializer")
    ApplicationDataSourceScriptDatabaseInitializer seedDataInitializer(
            DataSource dataSource, SqlInitializationProperties properties) {
        return new ApplicationDataSourceScriptDatabaseInitializer(dataSource, properties);
    }
}
