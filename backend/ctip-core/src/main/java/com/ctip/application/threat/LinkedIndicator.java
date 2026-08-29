package com.ctip.application.threat;

import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.threat.IndicatorRole;
import java.time.Instant;

/**
 * {@code GET /threats/{id}/indicators} 的一列:關聯屬性 + 對 viewer 可見的 Indicator。
 *
 * <p>只包含<strong>可見</strong>的 Indicator——關聯本身不是可見度的旁路。
 * 一個 CLEAR 的 Threat 可以關聯到租戶私有的 AMBER IOC,若照關聯全數回傳,
 * 匿名者就能經由威脅頁列舉別人的私有情資(§7.7 的可見度必須在這裡再走一次)。
 */
public record LinkedIndicator(Indicator indicator, IndicatorRole role, Instant addedAt) {}
