package com.ctip.application.source;

import com.ctip.application.port.SourceRepository;
import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 來源讀取(docs/spec/09-api.md §9.1 /sources/*):REST 層不得直接依賴 Repository port
 * (ArchUnit 規則 4),讀取一律經 application service。sources 表無租戶歸屬,無需可見度過濾。
 */
@Service
public class SourceQueryService {

    private final SourceRepository sources;

    public SourceQueryService(SourceRepository sources) {
        this.sources = sources;
    }

    public List<Source> all() {
        return sources.findAll();
    }

    public Optional<Source> byId(SourceId id) {
        return sources.findById(id);
    }
}
