package com.ctip.interfaces.rest.openapi;

import com.ctip.interfaces.rest.dto.admin.DataSubjectErasureDto;
import com.ctip.interfaces.rest.dto.admin.DataSubjectReportDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;

/** 資料主體端點的 OpenAPI 文件(13 §13.4);controller 實作本介面以繼承註解。 */
@Tag(name = "Admin", description = "Platform administration. Every call is audited as ADMIN_ACTION.")
public interface AdminDataSubjectApi {

    String REPORT_EXAMPLE = """
            {"userId":"a2f1c0d4-9b8e-4a71-8c33-0e1d2f3a4b5c","email":"analyst@example.org",\
            "displayName":"Alice Analyst","status":"ACTIVE","lastLoginAt":"2026-08-30T09:15:04Z",\
            "activeRefreshTokens":2,"auditEntries":417,"earliestAuditEntry":"2026-03-04T11:02:00Z",\
            "latestAuditEntry":"2026-08-30T09:15:04Z"}""";

    String ERASURE_EXAMPLE = """
            {"userId":"a2f1c0d4-9b8e-4a71-8c33-0e1d2f3a4b5c","deletedRefreshTokens":2,\
            "retainedAuditEntries":417}""";

    @Operation(
            summary = "Report the personal data held about one user",
            description = "GDPR subject access (13 §13.4). Returns the user record, how many refresh tokens "
                    + "still carry that person's IP and user agent, and the size and time span of their audit "
                    + "trail. Audit contents are not returned — they may concern other people's operations. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 system:admin。")
    @ApiResponse(
            responseCode = "200",
            description = "What the platform holds about this data subject",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DataSubjectReportDto.class),
                            examples = @ExampleObject(value = REPORT_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Missing system:admin permission (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "No such user (NOT_FOUND)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    DataSubjectReportDto report(UUID userId);

    @Operation(
            summary = "Erase the personal data held about one user",
            description = "GDPR erasure (13 §13.4). Deletes every refresh token of that user (their rows carry "
                    + "IP and user agent) and replaces the user's identifying fields with a placeholder, "
                    + "suspending the account. The user row itself is kept because other tenant records "
                    + "reference it. Audit entries are append-only and are NOT deleted: they expire under "
                    + "AUDIT_RETENTION_DAYS, and what remains of the subject there is a pseudonymous actor id. "
                    + "認證:需要 Bearer JWT 或 X-API-Key,權限 system:admin。")
    @ApiResponse(
            responseCode = "200",
            description = "What was erased and what is retained",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DataSubjectErasureDto.class),
                            examples = @ExampleObject(value = ERASURE_EXAMPLE)))
    @ApiResponse(responseCode = "403", description = "Missing system:admin permission (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "No such user (NOT_FOUND)")
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "apiKeyAuth")
    DataSubjectErasureDto erase(UUID userId);
}
