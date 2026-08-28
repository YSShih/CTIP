package com.ctip.infrastructure.bloom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctip.application.port.BloomStoragePort;
import com.ctip.domain.bloom.BloomArtifactLocation;
import com.ctip.domain.bloom.BloomCompression;
import com.ctip.domain.bloom.BloomScope;
import com.ctip.domain.tenant.TenantId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Artifact 的檔案系統儲存:三種壓縮的往返、相對路徑、以及目錄跳脫的拒絕。 */
@Tag("unit")
class FilesystemBloomStorageTest {

    @TempDir
    Path root;

    private static final BloomArtifactLocation PUBLIC_FULL =
            new BloomArtifactLocation(BloomScope.PUBLIC, TenantId.PUBLIC, 7, 0, true);

    @ParameterizedTest
    @EnumSource(BloomCompression.class)
    void everyCompressionRoundTripsToTheExactSameBytes(BloomCompression compression) {
        BloomStoragePort storage = BloomStorageFactory.filesystem(root);
        byte[] content = content();

        var stored = storage.write(PUBLIC_FULL, content, compression);

        assertThat(storage.read(stored.storagePath(), compression)).isEqualTo(content);
        assertThat(stored.uncompressedSizeBytes()).isEqualTo(content.length);
        assertThat(stored.sizeBytes()).isPositive();
    }

    @Test
    void theStoredPathIsRelativeToTheRootAndEncodesTheVersion() {
        BloomStoragePort storage = BloomStorageFactory.filesystem(root);

        var stored = storage.write(PUBLIC_FULL, content(), BloomCompression.ZSTD);

        assertThat(stored.storagePath()).isEqualTo("public/00000000-0000-0000-0000-000000000000/7/0-full.bin.zst");
        assertThat(root.resolve(stored.storagePath())).exists();
    }

    @Test
    void writingTwiceLeavesNoTemporaryFilesBehind() throws IOException {
        BloomStoragePort storage = BloomStorageFactory.filesystem(root);

        storage.write(PUBLIC_FULL, content(), BloomCompression.NONE);
        var stored = storage.write(PUBLIC_FULL, content(), BloomCompression.NONE);

        try (var files = Files.list(root.resolve("public/00000000-0000-0000-0000-000000000000/7"))) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("0-full.bin");
        }
        storage.delete(stored.storagePath());
        assertThat(root.resolve(stored.storagePath())).doesNotExist();
    }

    @Test
    void deltaArtifactsAreNamedApartFromFullSnapshots() {
        BloomStoragePort storage = BloomStorageFactory.filesystem(root);
        UUID tenant = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var delta = new BloomArtifactLocation(BloomScope.TENANT, new TenantId(tenant), 3, 5, false);

        var stored = storage.write(delta, content(), BloomCompression.GZIP);

        assertThat(stored.storagePath()).isEqualTo("tenant/" + tenant + "/3/5-delta.bin.gz");
    }

    @Test
    void pathsThatEscapeTheStorageRootAreRejected() {
        BloomStoragePort storage = BloomStorageFactory.filesystem(root);

        assertThatThrownBy(() -> storage.read("../../etc/passwd", BloomCompression.NONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("逸出");
    }

    private static byte[] content() {
        byte[] content = new byte[512];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 7);
        }
        return content;
    }
}
