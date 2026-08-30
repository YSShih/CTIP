package com.ctip.application.admin;

import com.ctip.application.port.SourceRepository;
import com.ctip.application.source.SourceSyncOutcome;
import com.ctip.application.source.SourceSyncService;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 來源的管理操作(docs/spec/09-api.md §9.1「管理」):
 * {@code POST /admin/sources/{id}/sync}({@code source:sync})與
 * {@code PATCH /admin/sources/{id}}({@code source:manage})。
 *
 * <p>{@code PATCH} 目前唯一可調的是 enabled——那是 {@link Source} 上唯一由外部意志決定的狀態
 * (其餘欄位是同步結果與健康度,由 ingestion 自己寫)。不預留「未來可能可調」的欄位(執行規則 16)。
 */
@Service
public class SourceAdminService {

    private final SourceRepository sources;
    private final SourceSyncService sync;

    public SourceAdminService(SourceRepository sources, SourceSyncService sync) {
        this.sources = sources;
        this.sync = sync;
    }

    /** 手動觸發同步;抓取本身在交易外進行(見 {@link SourceSyncService})。 */
    public SourceSyncOutcome syncNow(SourceId sourceId) {
        return sync.syncNow(sourceId);
    }

    @Transactional
    public Source setEnabled(SourceId sourceId, boolean enabled) {
        Source source = sources.findById(sourceId)
                .orElseThrow(() -> new AdminResourceNotFoundException("No such source: " + sourceId.value()));
        if (enabled) {
            source.enable();
        } else {
            source.disable();
        }
        return sources.save(source);
    }
}
