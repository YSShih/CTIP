package com.ctip.testing;

import com.ctip.application.port.BloomStoragePort;
import com.ctip.domain.bloom.BloomArtifactLocation;
import com.ctip.domain.bloom.BloomCompression;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 測試用 artifact 儲存;壓縮不影響 checksum,故此處一律原樣保存。 */
public final class InMemoryBloomStorage implements BloomStoragePort {

    private final Map<String, byte[]> files = new HashMap<>();

    @Override
    public StoredArtifact write(BloomArtifactLocation location, byte[] uncompressed, BloomCompression compression) {
        String path = "%s/%s/%d/%d-%s.bin"
                .formatted(
                        location.scope().name().toLowerCase(Locale.ROOT),
                        location.tenantId().value(),
                        location.datasetVersion(),
                        location.bloomVersion(),
                        location.fullSnapshot() ? "full" : "delta");
        files.put(path, uncompressed.clone());
        return new StoredArtifact(path, uncompressed.length, uncompressed.length);
    }

    @Override
    public byte[] read(String storagePath, BloomCompression compression) {
        byte[] content = files.get(storagePath);
        if (content == null) {
            throw new IllegalStateException("artifact 不存在:" + storagePath);
        }
        return content.clone();
    }

    /** 此實作不壓縮,因此「儲存時的原始位元組」與未壓縮內容相同。 */
    @Override
    public byte[] readStored(String storagePath) {
        return read(storagePath, BloomCompression.NONE);
    }

    @Override
    public void delete(String storagePath) {
        files.remove(storagePath);
    }

    public int size() {
        return files.size();
    }

    /** 測試用:模擬 artifact 在儲存層被損壞(位元被改寫)。 */
    public void corrupt(String storagePath) {
        byte[] content = files.get(storagePath);
        content[0] = (byte) ~content[0];
    }
}
