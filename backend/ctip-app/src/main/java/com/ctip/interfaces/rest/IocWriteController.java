package com.ctip.interfaces.rest;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.indicator.FalsePositiveReportService;
import com.ctip.application.ingestion.ImportFormat;
import com.ctip.application.ingestion.ImportJob;
import com.ctip.application.ingestion.ImportJobId;
import com.ctip.application.ingestion.ImportService;
import com.ctip.application.ingestion.ManualSubmissionCommand;
import com.ctip.application.ingestion.ManualSubmissionService;
import com.ctip.application.ingestion.RecordOutcome;
import com.ctip.application.ingestion.RejectionReason;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.infrastructure.web.RequestBodySizeLimits;
import com.ctip.interfaces.rest.dto.ioc.FalsePositiveRequest;
import com.ctip.interfaces.rest.dto.ioc.ImportJobDto;
import com.ctip.interfaces.rest.dto.ioc.IocDto;
import com.ctip.interfaces.rest.dto.ioc.IocSubmitRequest;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.error.ErrorCode;
import com.ctip.interfaces.rest.openapi.IocWriteApi;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IOC 寫入端點(docs/spec/09-api.md §9.1「IOC — 寫入」、§9.7)。
 *
 * <p>與讀取端點分開的 controller:讀取全部匿名可用,寫入每一個都要權限與配額,
 * 兩者的前置條件沒有交集。業務規則不在此(規則 10)——歸屬、TLP、配額、狀態判定
 * 分別在 {@code ManualSubmissionService}、{@code ImportService}、{@code FalsePositiveReportService}。
 */
@RestController
@RequestMapping("/api/v1/iocs")
class IocWriteController implements IocWriteApi {

    /**
     * 匯入請求本文的位元組上限。<strong>真正的防線是 {@code RequestBodySizeLimitFilter}</strong>
     * ——它在 Spring 把整包讀成 byte 陣列<em>之前</em>就中止;這裡的檢查只是 filter 若未註冊時的兜底。
     */
    private static final int MAX_IMPORT_BYTES = RequestBodySizeLimits.MAX_IMPORT_BYTES;

    private static final MediaType TEXT_CSV = MediaType.valueOf("text/csv");

    private final ManualSubmissionService submissions;
    private final ImportService imports;
    private final FalsePositiveReportService falsePositives;
    private final IocResponseAssembler assembler;
    private final TenantContext tenantContext;

    IocWriteController(
            ManualSubmissionService submissions,
            ImportService imports,
            FalsePositiveReportService falsePositives,
            IocResponseAssembler assembler,
            TenantContext tenantContext) {
        this.submissions = submissions;
        this.imports = imports;
        this.falsePositives = falsePositives;
        this.assembler = assembler;
        this.tenantContext = tenantContext;
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('ioc:submit')")
    public ResponseEntity<IocDto> submitIoc(@Valid @RequestBody IocSubmitRequest request) {
        AuthenticatedIdentity submitter = tenantContext.requireIdentity();
        RecordOutcome outcome = submissions.submit(
                new ManualSubmissionCommand(
                        request.type(),
                        request.value(),
                        request.hashType(),
                        request.confidence(),
                        request.severity(),
                        request.tlp(),
                        request.validUntil(),
                        request.tags(),
                        request.note()),
                submitter);
        if (outcome.rejected()) {
            throw rejected(outcome);
        }
        // 提交者一定看得到自己的資料;再散布過濾不作用於自家租戶(I14 的擁有租戶豁免)
        IocDto dto = assembler.toDto(outcome.indicator(), submitter.tenantId());
        return ResponseEntity.status(outcome.merged() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(dto);
    }

    @Override
    @PostMapping(
            value = "/import",
            consumes = {"text/csv", MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasAuthority('ioc:import')")
    public ResponseEntity<ImportJobDto> importIocs(
            @RequestBody byte[] payload, @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType) {
        if (payload.length > MAX_IMPORT_BYTES) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "Import body exceeds " + MAX_IMPORT_BYTES + " bytes");
        }
        ImportJob job = imports.submit(
                formatOf(contentType), new String(payload, StandardCharsets.UTF_8), tenantContext.requireIdentity());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toDto(job));
    }

    @Override
    @GetMapping("/import/{jobId}")
    @PreAuthorize("hasAuthority('ioc:import')")
    public ImportJobDto importStatus(@PathVariable UUID jobId) {
        return imports.find(new ImportJobId(jobId), tenantContext.tenantId())
                .map(IocWriteController::toDto)
                .orElseThrow(ApiException::notFound);
    }

    @Override
    @PostMapping("/{id}/report-false-positive")
    @PreAuthorize("hasAuthority('ioc:report-fp')")
    public IocDto reportFalsePositive(@PathVariable UUID id, @Valid @RequestBody FalsePositiveRequest request) {
        AuthenticatedIdentity reporter = tenantContext.requireIdentity();
        return falsePositives
                .report(new IndicatorId(id), request.reason(), request.evidenceUrl(), reporter)
                .map(indicator -> assembler.toDto(indicator, reporter.tenantId()))
                .orElseThrow(ApiException::notFound);
    }

    /** §9.7:{@code text/csv} 或 STIX 2.1 bundle;其餘由 {@code consumes} 擋成 415。 */
    private static ImportFormat formatOf(String contentType) {
        return TEXT_CSV.isCompatibleWith(MediaType.parseMediaType(contentType))
                ? ImportFormat.CSV
                : ImportFormat.STIX_BUNDLE;
    }

    /**
     * pipeline 拒絕單筆提交時的映射。回 201 再讓使用者自己去發現 IOC 不存在,
     * 是 §7.3「不得靜默接受」在 API 層的反面。
     */
    private static ApiException rejected(RecordOutcome outcome) {
        ErrorCode code = outcome.rejectionReason() == RejectionReason.QUOTA_EXCEEDED
                ? ErrorCode.RATE_LIMIT_EXCEEDED
                : ErrorCode.INVALID_IOC_FORMAT;
        return new ApiException(code, outcome.rejectionReason().name() + ": " + outcome.rejectionDetail());
    }

    private static ImportJobDto toDto(ImportJob job) {
        return new ImportJobDto(
                job.id().value(),
                job.status().name(),
                job.format().name(),
                job.totalRows(),
                job.acceptedCount(),
                job.mergedCount(),
                job.rejectedCount(),
                job.errorMessage(),
                job.startedAt(),
                job.finishedAt(),
                job.createdAt());
    }
}
