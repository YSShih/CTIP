package com.ctip.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code webhook_deliveries} 的簡單查詢;佔位與結果回寫在
 * {@link WebhookDeliveryStatements}(參數個數超過 §1.8 規則 3 的上限)。
 */
interface WebhookDeliveryJpaRepository extends JpaRepository<WebhookDeliveryEntity, UUID> {

    /** 走部分索引 ix_wd_retry(status = 'FAILED')。 */
    @Query(value = """
                    SELECT * FROM webhook_deliveries
                     WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= :now
                     ORDER BY next_retry_at
                     LIMIT :maxRows
                    """, nativeQuery = true)
    List<WebhookDeliveryEntity> dueForRetry(@Param("now") Instant now, @Param("maxRows") int maxRows);

    @Modifying
    @Query(value = "UPDATE webhook_deliveries SET next_retry_at = NULL WHERE id = :id", nativeQuery = true)
    int clearRetrySchedule(@Param("id") UUID id);
}
