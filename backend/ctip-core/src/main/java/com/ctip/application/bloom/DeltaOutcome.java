package com.ctip.application.bloom;

import com.ctip.domain.bloom.BloomVersion;

/** 一次 delta 生成的結果;{@code FULL_SNAPSHOT_REQUIRED} 與 {@code NO_BASELINE} 由編排者改跑 full snapshot。 */
public record DeltaOutcome(Status status, BloomVersion version) {

    public enum Status {
        /** 產生了一段新的 delta。 */
        CREATED,
        /** 這個 scope 自上次生成以來沒有新成員——不產生空 delta,以免白白吃掉 chain 預算。 */
        NO_CHANGES,
        /** 鏈太長 / 累計太大(§11.3),或參數已與現行 dataset 不相容(不變量 L4)。 */
        FULL_SNAPSHOT_REQUIRED,
        /** 這個 scope 還沒有任何 full snapshot,delta 無所依附。 */
        NO_BASELINE
    }

    public static DeltaOutcome created(BloomVersion version) {
        return new DeltaOutcome(Status.CREATED, version);
    }

    public static DeltaOutcome of(Status status) {
        return new DeltaOutcome(status, null);
    }

    public boolean needsFullSnapshot() {
        return status == Status.FULL_SNAPSHOT_REQUIRED || status == Status.NO_BASELINE;
    }
}
