package com.ctip.infrastructure.bloom;

import com.ctip.application.port.BloomStoragePort;
import com.ctip.domain.bloom.BloomArtifactLocation;
import com.ctip.domain.bloom.BloomCompression;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Artifact 的檔案系統儲存(04 表 23 的 {@code storage_kind = FILESYSTEM};
 * 根目錄由 {@code BLOOM_STORAGE_DIR} 指定,容器內由具名 volume {@code bloom-data} 掛載)。
 *
 * <p>資料庫存的是<strong>相對於根目錄</strong>的路徑——換掛載點不會使既有列失效。
 * 寫入走「暫存檔 + ATOMIC_MOVE」:排程與下載併發時,讀到的一定是完整檔案。
 */
class FilesystemBloomStorage implements BloomStoragePort {

    private final Path root;

    FilesystemBloomStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public StoredArtifact write(BloomArtifactLocation location, byte[] uncompressed, BloomCompression compression) {
        String relative = relativePath(location, compression);
        Path target = resolve(relative);
        byte[] payload = compress(uncompressed, compression);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".bloom-", ".tmp");
            Files.write(temp, payload);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("無法寫出 bloom artifact:" + relative, e);
        }
        return new StoredArtifact(relative, payload.length, uncompressed.length);
    }

    @Override
    public byte[] read(String storagePath, BloomCompression compression) {
        try {
            return decompress(Files.readAllBytes(resolve(storagePath)), compression);
        } catch (IOException e) {
            throw new UncheckedIOException("無法讀取 bloom artifact:" + storagePath, e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("無法刪除 bloom artifact:" + storagePath, e);
        }
    }

    /** {@code <scope>/<tenantId>/<datasetVersion>/<bloomVersion>-<full|delta>.bin[.zst|.gz]} */
    private static String relativePath(BloomArtifactLocation location, BloomCompression compression) {
        return "%s/%s/%d/%d-%s.bin%s"
                .formatted(
                        location.scope().name().toLowerCase(Locale.ROOT),
                        location.tenantId().value(),
                        location.datasetVersion(),
                        location.bloomVersion(),
                        location.fullSnapshot() ? "full" : "delta",
                        extension(compression));
    }

    private static String extension(BloomCompression compression) {
        return switch (compression) {
            case ZSTD -> ".zst";
            case GZIP -> ".gz";
            case NONE -> "";
        };
    }

    /** 路徑一律解析後驗證仍在根目錄之下:storage_path 來自資料庫,不得成為目錄跳脫的入口。 */
    private Path resolve(String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("artifact 路徑逸出儲存根目錄:" + relative);
        }
        return resolved;
    }

    private static byte[] compress(byte[] content, BloomCompression compression) {
        if (compression == BloomCompression.NONE) {
            return content;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (OutputStream out =
                compression == BloomCompression.ZSTD ? new ZstdOutputStream(buffer) : new GZIPOutputStream(buffer)) {
            out.write(content);
        } catch (IOException e) {
            throw new UncheckedIOException("壓縮 bloom artifact 失敗", e);
        }
        return buffer.toByteArray();
    }

    private static byte[] decompress(byte[] payload, BloomCompression compression) throws IOException {
        if (compression == BloomCompression.NONE) {
            return payload;
        }
        try (InputStream in = compression == BloomCompression.ZSTD
                ? new ZstdInputStream(new ByteArrayInputStream(payload))
                : new GZIPInputStream(new ByteArrayInputStream(payload))) {
            return in.readAllBytes();
        }
    }
}
