package com.ctip.infrastructure.bloom;

import com.ctip.application.port.BloomStoragePort;
import java.nio.file.Path;

/** 儲存實作的建構入口:實作本身維持 package-private,由設定層以根目錄建立。 */
public final class BloomStorageFactory {

    private BloomStorageFactory() {}

    public static BloomStoragePort filesystem(Path root) {
        return new FilesystemBloomStorage(root);
    }
}
