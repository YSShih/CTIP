package com.ctip.application.bloom;

import com.ctip.application.port.BloomStoragePort;
import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomDeltaCodec;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.Checksum;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 由已寫出的 artifact 重建「現行位元陣列」:full snapshot 的陣列 + 依序套用該 dataset 的每一段 delta。
 *
 * <p>刻意<strong>不在記憶體常駐</strong>:delta 每小時才生成一次,重讀 18MB 的成本遠低於
 * 「記憶體狀態與檔案不一致」的風險——後者會讓 {@code resultingChecksum} 對不上,
 * client 依 §11.6 丟棄後重下 full,整個 delta 機制形同虛設。
 *
 * <p>載入時走的是<strong>與 client 相同的驗證路徑</strong>(§11.6):驗 full 的 checksum、
 * 驗每段 delta payload 的 checksum、每套用一段就比對 {@code resultingChecksum}。
 * 伺服器端若跳過這些檢查,損壞會被寫進下一段 delta 的 {@code resultingChecksum},
 * 讓所有 client 一起失敗而查不出原因。
 */
@Component
public class BloomArrayLoader {

    private final BloomStoragePort storage;

    public BloomArrayLoader(BloomStoragePort storage) {
        this.storage = storage;
    }

    public BloomBitArray load(BloomVersion fullSnapshot, List<BloomVersion> deltaChain) {
        BloomBitArray array = BloomBitArray.of(fullSnapshot.parameters(), payloadOf(fullSnapshot));
        require(array.checksum(), fullSnapshot.artifact().checksum(), fullSnapshot, "位元陣列");
        for (BloomVersion delta : deltaChain) {
            byte[] payload = payloadOf(delta);
            require(Checksum.sha256(payload), delta.artifact().checksum(), delta, "delta payload");
            BloomDeltaCodec.decode(payload).forEach(array::set);
            require(array.checksum(), delta.artifact().resultingChecksum(), delta, "套用後的陣列");
        }
        return array;
    }

    private byte[] payloadOf(BloomVersion version) {
        return storage.read(version.artifact().storagePath(), version.artifact().compression());
    }

    private static void require(Checksum actual, Checksum expected, BloomVersion version, String what) {
        if (!actual.matches(expected)) {
            throw new BloomArtifactCorruptedException("%s 的 %s checksum 不符(dataset %d / version %d):%s"
                    .formatted(
                            version.artifact().storagePath(),
                            what,
                            version.datasetVersion(),
                            version.bloomVersion(),
                            actual.hex()));
        }
    }
}
