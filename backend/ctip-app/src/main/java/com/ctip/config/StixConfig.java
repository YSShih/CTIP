package com.ctip.config;

import com.ctip.application.stix.StixExportSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** STIX 匯出設定(07 §7.8.5):M1 property 承載上限,Phase 14 改查 plans 表。 */
@Configuration(proxyBeanMethods = false)
public class StixConfig {

    @Bean
    StixExportSettings stixExportSettings(CtipProperties properties) {
        return new StixExportSettings(properties.stix().exportMaxObjects());
    }
}
