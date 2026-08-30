package com.ctip.interfaces.rest.dto.admin;

import java.util.UUID;

/**
 * 手動觸發同步的結果({@code POST /api/v1/admin/sources/{id}/sync})。
 *
 * @param failureReason 失敗原因(已遮蔽憑證;S5),成功時為 null
 */
public record SourceSyncResultDto(
        UUID sourceId,
        boolean success,
        int recordsFetched,
        int recordsAccepted,
        int recordsRejected,
        int recordsMerged,
        String failureReason) {}
