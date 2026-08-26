package com.ctip.interfaces.rest.error;

import com.ctip.application.port.ClockPort;
import com.ctip.application.stix.StixExportLimitExceededException;
import com.ctip.interfaces.rest.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
