package com.ctip.interfaces.rest.error;

import com.ctip.interfaces.rest.dto.common.ErrorResponse;
import java.util.List;
import java.util.Objects;

/** API 層的典型錯誤:錯誤碼 + 英文訊息(§9.4;UI 文案由前端依 code 對映)。 */
public class ApiException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final transient List<ErrorResponse.FieldIssue> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<ErrorResponse.FieldIssue> details) {
        super(message);
        this.code = Objects.requireNonNull(code);
        this.details = List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorResponse.FieldIssue> details() {
        return details;
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND, "Resource not found");
    }
}
