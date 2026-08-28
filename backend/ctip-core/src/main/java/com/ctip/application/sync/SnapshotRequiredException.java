package com.ctip.application.sync;

/**
 * Client 必須改下載 full snapshot(docs/spec/11-sync-bloom.md §11.3
 * → {@code 409 SNAPSHOT_REQUIRED},09 §9.4 錯誤碼表)。
 *
 * <p>三種情形共用這一個出口,因為 client 的動作完全相同(§11.6 第 4 步):
 * delta 鏈過長或累計過大、這個 scope 還沒有任何 snapshot、
 * client 送來的 {@code base} 不在現行 dataset 的鏈上(通常是它的本地版本屬於舊 dataset)。
 */
public class SnapshotRequiredException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public SnapshotRequiredException(String message) {
        super(message);
    }
}
