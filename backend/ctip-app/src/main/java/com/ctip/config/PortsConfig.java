package com.ctip.config;

import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ClockPort / IdGeneratorPort 的系統實作;domain 一律經 port 取得時間與識別碼(ArchUnit 規則 9)。 */
@Configuration(proxyBeanMethods = false)
public class PortsConfig {

    @Bean
    ClockPort clockPort() {
        return Instant::now;
    }

    @Bean
    IdGeneratorPort idGeneratorPort() {
        return UUID::randomUUID;
    }
}
