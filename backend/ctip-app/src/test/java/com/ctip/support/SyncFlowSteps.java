package com.ctip.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctip.domain.bloom.BloomBitArray;
import com.ctip.domain.bloom.BloomDeltaCodec;
import com.ctip.domain.bloom.BloomVersion;
import com.ctip.domain.bloom.Checksum;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/**
 * §11.6 的 client 流程逐步斷言(TENANT scope)。
 *
 * <p>抽成一組步驟而不是寫在測試方法裡,是為了讓那條流程讀起來就是規格的四個步驟:
 * manifest → 下載 full 並驗 checksum → 取 delta 套用並驗 resultingChecksum → 更新版本。
 * 新成員的名稱固定為 {@code sync-flow-added}(與 {@code SyncEndToEndTest} 的 fixture 一致)。
 */
public final class SyncFlowSteps {

    private static final String ADDED_MEMBER = "sync-flow-added";

    private SyncFlowSteps() {}

    /** §11.6 第 1–2 步:manifest 的參數與「完全同步後應有的 checksum」。 */
    public static JsonNode assertManifestDescribes(SyncTestClient client, BloomVersion full) throws Exception {
        JsonNode tenant = client.manifest().get("tenant");
        assertThat(tenant.get("scope").asString()).isEqualTo("TENANT");
        assertThat(tenant.get("datasetVersion").asLong()).isEqualTo(full.datasetVersion());
        assertThat(tenant.get("bloomVersion").asLong()).isZero();
        assertThat(tenant.get("fingerprintAlgorithm").asString()).isEqualTo("SHA256");
        assertThat(tenant.get("hashFunctionCount").asInt())
                .isEqualTo(full.parameters().hashFunctionCount());
        assertThat(tenant.get("bitSize").asLong()).isEqualTo(full.parameters().bitSize());
        assertThat(tenant.get("sizeBytes").asLong()).isEqualTo(full.parameters().byteLength());
        assertThat(tenant.get("compression").asString()).isEqualTo("ZSTD");
        assertThat(tenant.get("checksum").asString())
                .isEqualTo(full.artifact().checksum().hex());
        return tenant;
    }

    /** §11.6 第 4 步:解壓、驗 checksum、記下 artifact 自己的版本。 */
    public static BloomBitArray downloadAndVerify(SyncTestClient client, BloomVersion full, JsonNode advertised)
            throws Exception {
        MockHttpServletResponse download = client.bloom("TENANT");
        byte[] array = SyncTestClient.inflateZstd(download.getContentAsByteArray());

        assertThat(Checksum.sha256(array).hex())
                .isEqualTo(download.getHeader("X-Bloom-Checksum"))
                .isEqualTo(advertised.get("checksum").asString());
        assertThat(array).hasSize((int) advertised.get("sizeBytes").asLong());
        assertThat(download.getHeader("X-Bloom-Version"))
                .as("回應體是 full snapshot,client 的本地版本必須記成它而不是 manifest 的最新版")
                .isEqualTo("0");
        assertThat(download.getHeader("X-Bloom-Dataset-Version")).isEqualTo(Long.toString(full.datasetVersion()));
        return BloomBitArray.of(full.parameters(), array);
    }

    /** §11.6 第 3 步:取 delta、依 §11.5 解碼、套用、驗 {@code resultingChecksum}。 */
    public static void applyDeltaAndVerify(SyncTestClient client, BloomVersion full, BloomBitArray local)
            throws Exception {
        JsonNode delta = client.delta(0, "TENANT");
        assertThat(delta.get("datasetVersion").asLong()).isEqualTo(full.datasetVersion());
        assertThat(delta.get("baseVersion").asLong()).isZero();
        assertThat(delta.get("targetVersion").asLong()).isEqualTo(1);
        assertThat(delta.get("addedMemberCount").asLong()).isEqualTo(1);

        byte[] payload = SyncTestClient.decodeAddedBits(delta.get("addedBits").asString());
        assertThat(Checksum.sha256(payload).hex())
                .isEqualTo(delta.get("checksum").asString());
        BloomDeltaCodec.decode(payload).forEach(local::set);

        assertThat(local.checksum().hex())
                .isEqualTo(delta.get("resultingChecksum").asString());
        assertThat(BloomFixtures.mightContain(local, full.parameters(), BloomFixtures.fingerprintOf(ADDED_MEMBER)))
                .isTrue();
    }

    /** 「更新版本」之後:manifest 指向剛套用到的版本,再取 delta 得到可驗證的空區間。 */
    public static void assertServerAgreesWithTheUpdatedVersion(SyncTestClient client, BloomBitArray local)
            throws Exception {
        JsonNode updated = client.manifest().get("tenant");
        assertThat(updated.get("bloomVersion").asLong()).isEqualTo(1);
        assertThat(updated.get("checksum").asString())
                .isEqualTo(local.checksum().hex());

        JsonNode idle = client.delta(1, "TENANT");
        assertThat(idle.get("addedBits").asString()).isEmpty();
        assertThat(idle.get("targetVersion").asLong()).isEqualTo(1);
        assertThat(idle.get("addedMemberCount").asLong()).isZero();
        assertThat(idle.get("resultingChecksum").asString())
                .as("空區間也必須給得出可驗的值,否則 client 無從確認自己還正確")
                .isEqualTo(local.checksum().hex());
    }
}
