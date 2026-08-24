package com.ctip.application.port;

import com.ctip.domain.source.Source;
import com.ctip.domain.source.SourceId;
import com.ctip.sdk.SourceType;
import java.util.List;
import java.util.Optional;

/** Source 持久化 port。 */
public interface SourceRepository {

    Optional<Source> findById(SourceId id);

    Optional<Source> findBySourceType(SourceType sourceType);

    /** 排程掃描對象:enabled 且 syncable 的來源。 */
    List<Source> findEnabledSyncable();

    Source save(Source source);
}
