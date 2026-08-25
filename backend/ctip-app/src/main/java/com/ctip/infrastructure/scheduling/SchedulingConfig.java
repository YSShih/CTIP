package com.ctip.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 排程總開關(docs/spec/08-ingestion-sdk.md §8.7):SCHEDULER_ENABLED=false(測試環境)
 * 時整個排程機制不啟用。M1–M3 皆為單一實例;多實例需先引入 ShedLock(保留擴充點,不實作)。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "ctip.scheduler", name = "enabled", havingValue = "true")
public class SchedulingConfig {}
