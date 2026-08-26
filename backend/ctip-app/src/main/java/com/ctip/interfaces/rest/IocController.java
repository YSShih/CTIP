package com.ctip.interfaces.rest;

import com.ctip.application.indicator.IndicatorFilter;
import com.ctip.application.indicator.IndicatorQueryService;
import com.ctip.config.CtipProperties;
import com.ctip.domain.indicator.Indicator;
import com.ctip.domain.indicator.IndicatorId;
import com.ctip.domain.indicator.IndicatorStatus;
import com.ctip.domain.shared.Cursor;
import com.ctip.infrastructure.security.TenantContext;
import com.ctip.interfaces.rest.dto.common.PageResponse;
import com.ctip.interfaces.rest.dto.ioc.IocDto;
import com.ctip.interfaces.rest.dto.ioc.IocListParams;
import com.ctip.interfaces.rest.dto.ioc.IocSourceDto;
import com.ctip.interfaces.rest.dto.ioc.LookupRequest;
import com.ctip.interfaces.rest.dto.ioc.LookupResponse;
import com.ctip.interfaces.rest.dto.ioc.SearchRequest;
import com.ctip.interfaces.rest.error.ApiException;
import com.ctip.interfaces.rest.error.ErrorCode;
import com.ctip.interfaces.rest.openapi.IocApi;
import com.ctip.sdk.IocType;
import com.ctip.sdk.Severity;
import com.ctip.sdk.Tlp;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IOC 讀取端點(docs/spec/09-api.md §9.1,皆匿名可用)。
 * 業務規則不在 controller(規則 10):可見度過濾在 Specification 層(不手動傳 tenantId)、
 * 再散布遮罩在 {@link IocResponseAssembler}(經 RedistributionFilter 單點)。
 * limit 超過上限夾到上限不報錯(§9.3);offset 僅供頁碼 UI,上限 10000。
 */
@RestController
@RequestMapping("/api/v1/iocs")
class IocController implements IocApi {

    private static final int MAX_OFFSET = 10_000;

    private final IndicatorQueryService query;
    private final TenantContext tenantContext;
    private final IocResponseAssembler assembler;
    private final CursorCodec cursorCodec;
    private final CtipProperties.Api api;

    IocController(
            IndicatorQueryService query,
            TenantContext tenantContext,
            IocResponseAssembler assembler,
            CursorCodec cursorCodec,
            CtipProperties properties) {
        this.query = query;
        this.tenantContext = tenantContext;
        this.assembler = assembler;
        this.cursorCodec = cursorCodec;
        this.api = properties.api();
    }

    @Override
    @GetMapping
    public PageResponse<IocDto> list(IocListParams params) {
        IndicatorFilter filter = new IndicatorFilter(
                params.type(), params.severity(), params.status(), params.tlp(), params.includeExpiredOrDefault());
        int pageSize = clampLimit(params.limit());
        if (params.offset() != null) {
            if (params.offset() > MAX_OFFSET) {
                throw new ApiException(ErrorCode.OFFSET_TOO_LARGE, "offset must be <= " + MAX_OFFSET);
            }
            List<Indicator> rows =
                    query.listOffset(filter, tenantContext.visibility(), Math.max(0, params.offset()), pageSize + 1);
            boolean hasMore = rows.size() > pageSize;
            return assembler.toPage(hasMore ? rows.subList(0, pageSize) : rows, hasMore, tenantContext.tenantId());
        }
        Cursor after = cursorCodec.decode(params.cursor());
        return assembler.toPage(
                query.list(filter, tenantContext.visibility(), after, pageSize), tenantContext.tenantId());
    }

    @Override
    @GetMapping("/{id}")
    public IocDto byId(@PathVariable UUID id) {
        return assembler.toDto(visibleIndicator(id), tenantContext.tenantId());
    }

    @Override
    @GetMapping("/{id}/sources")
    public List<IocSourceDto> sources(@PathVariable UUID id) {
        return assembler.toSourceDtos(visibleIndicator(id), tenantContext.tenantId());
    }

    @Override
    @PostMapping("/search")
    public PageResponse<IocDto> search(@Valid @RequestBody SearchRequest request) {
        IndicatorFilter filter = new IndicatorFilter(
                parseEnum(IocType.class, request.type(), "type"),
                parseEnum(Severity.class, request.severity(), "severity"),
                parseEnum(IndicatorStatus.class, request.status(), "status"),
                parseEnum(Tlp.class, request.tlp(), "tlp"),
                Boolean.TRUE.equals(request.includeExpired()));
        Cursor after = cursorCodec.decode(request.cursor());
        int pageSize = clampLimit(request.limit());
        return assembler.toPage(
                query.search(request.query(), filter, tenantContext.visibility(), after, pageSize),
                tenantContext.tenantId());
    }

    @Override
    @PostMapping("/lookup")
    public LookupResponse lookup(@Valid @RequestBody LookupRequest request) {
        if (request.values().size() > api.maxBatchLookup()) {
            throw new ApiException(
                    ErrorCode.PAYLOAD_TOO_LARGE, "Batch lookup exceeds limit of " + api.maxBatchLookup());
        }
        List<LookupResponse.Result> results = query.lookup(request.values(), tenantContext.visibility()).stream()
                .map(r -> new LookupResponse.Result(
                        r.value(),
                        r.found(),
                        r.found() ? assembler.toDto(r.indicator(), tenantContext.tenantId()) : null))
                .toList();
        return new LookupResponse(results);
    }

    /** 不存在與跨租戶不可見一律 404(§9.4),避免洩漏資源存在性。 */
    private Indicator visibleIndicator(UUID id) {
        return query.byId(new IndicatorId(id), tenantContext.visibility()).orElseThrow(ApiException::notFound);
    }

    /** §9.3:limit 超過上限夾到上限,不報錯;未給用預設。 */
    private int clampLimit(Integer limit) {
        if (limit == null) {
            return api.defaultPageSize();
        }
        return Math.max(1, Math.min(limit, api.maxPageSize()));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Invalid value for " + field,
                    List.of(new com.ctip.interfaces.rest.dto.common.ErrorResponse.FieldIssue(
                            field, "must be one of " + java.util.Arrays.toString(type.getEnumConstants()))));
        }
    }
}
