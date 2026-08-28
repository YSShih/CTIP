package com.ctip.application.ingestion;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.IdGeneratorPort;
import org.springframework.stereotype.Component;

/**
 * 新匯入 job 的建立(domain 不得自行產生 id 或時間,一律經 port 注入)。
 * 與 {@code ApiKeyFactory} / {@code RefreshTokenFactory} 同一個模式。
 */
@Component
public class ImportJobFactory {

    private final IdGeneratorPort idGenerator;
    private final ClockPort clock;

    public ImportJobFactory(IdGeneratorPort idGenerator, ClockPort clock) {
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public ImportJob pending(AuthenticatedIdentity submitter, ImportFormat format, int totalRows) {
        return new ImportJob(
                new ImportJobId(idGenerator.nextId()),
                submitter.tenantId(),
                submitter.userId(),
                ImportJobStatus.PENDING,
                format,
                totalRows,
                0,
                0,
                0,
                null,
                null,
                null,
                clock.now());
    }
}
