package com.ctip.application.stix;

import com.ctip.application.port.StixObjectPort;
import com.ctip.domain.stix.StixProjection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 批次交易提交後寫出 STIX 投影(§7.8.6):逐筆 try/catch,單筆寫出失敗只記錄,
 * 不影響其他投影,更不影響已提交的 ingestion。stix_objects 可隨時由 domain 重建
 * (M3 提供 rebuild 端點),失敗筆待下次同步重投影。
 */
@Service
public class StixProjectionWriter {

    private static final Logger log = LoggerFactory.getLogger(StixProjectionWriter.class);

    private final StixObjectPort stixObjects;

    public StixProjectionWriter(StixObjectPort stixObjects) {
        this.stixObjects = stixObjects;
    }

    public void writeAll(List<StixProjection> projections) {
        for (StixProjection projection : projections) {
            try {
                stixObjects.upsert(projection);
            } catch (RuntimeException e) {
                log.warn("STIX 投影寫出失敗,只記錄不影響 ingestion(§7.8.6):{}", projection.stixId(), e);
            }
        }
    }
}
