package com.ctip.interfaces.rest.error;

import com.ctip.application.identity.ApiKeyNotFoundException;
import com.ctip.application.identity.AuthenticationFailedException;
import com.ctip.application.identity.EmailAlreadyRegisteredException;
import com.ctip.application.identity.InvalidRefreshTokenException;
import com.ctip.application.indicator.PublicIntelNotReportableException;
import com.ctip.application.ingestion.PublishNotPermittedException;
import com.ctip.application.plan.PlanLimitExceededException;
import com.ctip.application.plan.QuotaExhaustedException;
import com.ctip.application.plan.RequestSizeLimitExceededException;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.RateLimitResult;
import com.ctip.application.stix.StixExportLimitExceededException;
import com.ctip.application.sync.SnapshotRequiredException;
import com.ctip.application.sync.SyncTooFrequentException;
import com.ctip.infrastructure.ratelimit.RateLimitHeaders;
import com.ctip.interfaces.rest.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 統一錯誤回應(docs/spec/09-api.md §9.4):16 個錯誤碼、traceId 與日誌可對應(MDC)、
 * 絕不將 stack trace 洩漏給 client。message 為英文,UI 文案由前端依 code 對映。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ClockPort clock;

    ApiExceptionHandler(ClockPort clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> apiException(ApiException e, HttpServletRequest request) {
        return respond(e.code(), e.getMessage(), e.details(), request);
    }

    @ExceptionHandler(StixExportLimitExceededException.class)
    ResponseEntity<ErrorResponse> planLimitExceeded(StixExportLimitExceededException e, HttpServletRequest request) {
        return respond(ErrorCode.PLAN_LIMIT_EXCEEDED, "Bundle exceeds plan object limit", List.of(), request);
    }

    /**
     * 非時間窗的方案能力上限(§9.7 三種語意之一):API key 數量、webhook 數量、
     * bundle 物件數、方案未開放的能力。403——不會自己恢復,等待無用。
     */
    @ExceptionHandler(PlanLimitExceededException.class)
    ResponseEntity<ErrorResponse> planLimitExceeded(PlanLimitExceededException e, HttpServletRequest request) {
        return respond(ErrorCode.PLAN_LIMIT_EXCEEDED, e.getMessage(), List.of(), request);
    }

    /** 單次請求的尺寸上限(§9.7):413——拆小就能過。 */
    @ExceptionHandler(RequestSizeLimitExceededException.class)
    ResponseEntity<ErrorResponse> requestTooLarge(RequestSizeLimitExceededException e, HttpServletRequest request) {
        return respond(ErrorCode.PAYLOAD_TOO_LARGE, e.getMessage(), List.of(), request);
    }

    /**
     * 時間窗內的計數用罄(§9.7):429 + X-RateLimit-* + Retry-After。
     * 標頭與 {@code RateLimitFilter} 同一套語意——client 得知何時可再試。
     */
    @ExceptionHandler(QuotaExhaustedException.class)
    ResponseEntity<ErrorResponse> quotaExhausted(QuotaExhaustedException e, HttpServletRequest request) {
        RateLimitResult result = e.result();
        long retryAfter = Math.max(
                1, java.time.Duration.between(clock.now(), result.resetAt()).getSeconds());
        return ResponseEntity.status(ErrorCode.RATE_LIMIT_EXCEEDED.status())
                .header("X-RateLimit-Limit", RateLimitHeaders.value(result.limit()))
                .header("X-RateLimit-Remaining", RateLimitHeaders.remaining(result))
                .header("X-RateLimit-Reset", Long.toString(result.resetAt().getEpochSecond()))
                .header("Retry-After", Long.toString(retryAfter))
                .body(body(
                        ErrorCode.RATE_LIMIT_EXCEEDED.status().value(),
                        ErrorCode.RATE_LIMIT_EXCEEDED.name(),
                        e.getMessage(),
                        List.of(),
                        request));
    }

    /**
     * §11.3:delta 鏈過長、尚無 snapshot、或 client 的 base 不在現行 dataset 上。
     * client 的動作三者相同——改下載 full snapshot(§11.6 第 4 步)。
     */
    @ExceptionHandler(SnapshotRequiredException.class)
    ResponseEntity<ErrorResponse> snapshotRequired(SnapshotRequiredException e, HttpServletRequest request) {
        return respond(ErrorCode.SNAPSHOT_REQUIRED, e.getMessage(), List.of(), request);
    }

    /**
     * §11.6:同步頻率超過 {@code plans.min_sync_interval_seconds} → 429 + {@code Retry-After}。
     *
     * <p>{@code X-RateLimit-*} 三個標頭由 {@code RateLimitFilter} 在更外層寫入(§10.7 要求
     * 所有回應都要有),此處只補 {@code Retry-After}——它反映的是同步間隔而非限流視窗。
     */
    @ExceptionHandler(SyncTooFrequentException.class)
    ResponseEntity<ErrorResponse> syncTooFrequent(SyncTooFrequentException e, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.RATE_LIMIT_EXCEEDED.status())
                .header("Retry-After", Long.toString(e.retryAfter().toSeconds()))
                .body(body(
                        ErrorCode.RATE_LIMIT_EXCEEDED.status().value(),
                        ErrorCode.RATE_LIMIT_EXCEEDED.name(),
                        e.getMessage(),
                        List.of(),
                        request));
    }

    /**
     * 寫入端點的兩種「權限不足」(§9.7):要求 CLEAR/GREEN 但沒有 {@code ioc:publish}、
     * 對公開情資做誤判回報。都是授權問題而非方案能力上限,故 FORBIDDEN 而非 PLAN_LIMIT_EXCEEDED。
     */
    @ExceptionHandler({PublishNotPermittedException.class, PublicIntelNotReportableException.class})
    ResponseEntity<ErrorResponse> writeForbidden(RuntimeException e, HttpServletRequest request) {
        return respond(ErrorCode.FORBIDDEN, e.getMessage(), List.of(), request);
    }

    /** 認證失敗、無效／重用的 refresh token:一律 401,不揭露細節(避免帳號列舉)。 */
    @ExceptionHandler({AuthenticationFailedException.class, InvalidRefreshTokenException.class})
    ResponseEntity<ErrorResponse> unauthenticated(RuntimeException e, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, e.getMessage(), List.of(), request);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ErrorResponse> emailTaken(EmailAlreadyRegisteredException e, HttpServletRequest request) {
        return respond(ErrorCode.CONFLICT, "Email already registered", List.of(), request);
    }

    @ExceptionHandler(ApiKeyNotFoundException.class)
    ResponseEntity<ErrorResponse> apiKeyNotFound(ApiKeyNotFoundException e, HttpServletRequest request) {
        return respond(ErrorCode.NOT_FOUND, "Resource not found", List.of(), request);
    }

    /** {@code @PreAuthorize} 未通過:已認證但權限不足,或匿名身分不具該權限(§10.2)。 */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException e, HttpServletRequest request) {
        return respond(ErrorCode.FORBIDDEN, "Insufficient permission", List.of(), request);
    }

    /**
     * 唯一約束衝突。註冊的 email 檢查(existsByEmail → insert)與 tenant slug 檢查都是 TOCTOU,
     * 併發同 email／同 tenantName 註冊時輸家會撞 ux_users_email / ux_tenants_slug;
     * 原本落到兜底成 500,而語意上這是衝突(ADR 0013)。訊息固定,不揭露是哪個約束。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflictingWrite(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("唯一約束衝突:{} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(ErrorCode.CONFLICT, "Conflicting request", List.of(), request);
    }

    /**
     * domain / 值物件的不變量違反(使用者輸入導致)。訊息為中文且屬內部細節,
     * 一律代換為固定英文訊息(§9.4:message 為英文、不洩漏內部資訊),詳情只記伺服器端。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> invalidDomainInput(IllegalArgumentException e, HttpServletRequest request) {
        log.debug("請求違反 domain 不變量:{} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(ErrorCode.INVALID_REQUEST, "Invalid request", List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<ErrorResponse.FieldIssue> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldIssue(f.getField(), String.valueOf(f.getDefaultMessage())))
                .toList();
        return respond(ErrorCode.INVALID_REQUEST, "Request validation failed", details, request);
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        HttpMessageNotReadableException.class,
        org.springframework.validation.BindException.class
    })
    ResponseEntity<ErrorResponse> invalidParameter(Exception e, HttpServletRequest request) {
        return respond(ErrorCode.INVALID_REQUEST, "Invalid request parameter", List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupportedMediaType(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", List.of(), request);
    }

    @ExceptionHandler({NoResourceFoundException.class})
    ResponseEntity<ErrorResponse> noResource(Exception e, HttpServletRequest request) {
        return respond(ErrorCode.NOT_FOUND, "Resource not found", List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> methodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        ErrorResponse body = body(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                ErrorCode.INVALID_REQUEST.name(),
                "HTTP method not supported",
                List.of(),
                request);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> responseStatus(ResponseStatusException e, HttpServletRequest request) {
        ErrorCode code =
                switch (HttpStatus.valueOf(e.getStatusCode().value())) {
                    case NOT_FOUND -> ErrorCode.NOT_FOUND;
                    case FORBIDDEN -> ErrorCode.FORBIDDEN;
                    case UNAUTHORIZED -> ErrorCode.UNAUTHENTICATED;
                    case CONFLICT -> ErrorCode.CONFLICT;
                    default -> ErrorCode.INVALID_REQUEST;
                };
        ErrorResponse body = body(e.getStatusCode().value(), code.name(), "Request failed", List.of(), request);
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    /** 兜底:記完整例外於伺服器端,client 只拿到 INTERNAL_ERROR(不含 stack trace)。 */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest request) {
        log.error("未預期錯誤:{} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(ErrorCode.INTERNAL_ERROR, "Internal error", List.of(), request);
    }

    private ResponseEntity<ErrorResponse> respond(
            ErrorCode code, String message, List<ErrorResponse.FieldIssue> details, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .body(body(code.status().value(), code.name(), message, details, request));
    }

    private ErrorResponse body(
            int status,
            String code,
            String message,
            List<ErrorResponse.FieldIssue> details,
            HttpServletRequest request) {
        return new ErrorResponse(
                clock.now(), status, code, message, request.getRequestURI(), MDC.get("traceId"), details);
    }
}
