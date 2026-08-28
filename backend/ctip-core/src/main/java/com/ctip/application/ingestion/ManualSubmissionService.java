package com.ctip.application.ingestion;

import com.ctip.application.identity.AuthenticatedIdentity;
import com.ctip.application.plan.QuotaService;
import com.ctip.application.port.ClockPort;
import com.ctip.application.port.SourceRepository;
import com.ctip.domain.source.Source;
import com.ctip.domain.tenant.TenantId;
import com.ctip.sdk.RawThreatRecord;
import com.ctip.sdk.RedistributionPolicy;
import com.ctip.sdk.SourceType;
import com.ctip.sdk.Tlp;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 單筆手動提交(docs/spec/09-api.md §9.7 {@code POST /api/v1/iocs};08 §8.3)。
 *
 * <p>走<strong>完整 pipeline</strong>(驗證、正規化、去重、合併),不繞過任何 stage;
 * 來源固定為系統來源 {@code MANUAL}({@code redistribution_policy = INTERNAL_ONLY})。
 *
 * <p>三條規則集中在此,不散落於 controller(規則 10):
 * <ul>
 *   <li>歸屬:{@code owner_tenant_id} 一律取提交者的租戶,提交者不得指定</li>
 *   <li>TLP:預設 {@code AMBER}(私有);{@code CLEAR}/{@code GREEN} 需 {@code ioc:publish},
 *       且該權限的語意是<strong>擁有權轉移</strong>——owner 改為 public tenant(ADR 0019 第 2 節),
 *       否則那個權限不產生任何公開效果;{@code RED} 一律拒絕(07「RED 不進入平台」)</li>
 *   <li>配額:{@code plans.max_manual_submissions_per_day},超出回 429(§9.7)</li>
 * </ul>
 */
@Service
public class ManualSubmissionService {

    private final IngestionBatchExecutor executor;
    private final SourceRepository sources;
    private final QuotaService quotas;
    private final ClockPort clock;

    public ManualSubmissionService(
            IngestionBatchExecutor executor, SourceRepository sources, QuotaService quotas, ClockPort clock) {
        this.executor = executor;
        this.sources = sources;
        this.quotas = quotas;
        this.clock = clock;
    }

    /**
     * 配額在<strong>進 pipeline 之前</strong>扣減:被 pipeline 拒絕的那一筆也算用掉一次額度。
     * 反過來(只對成功的收費)會讓「一直送不合法的值」變成一條不受每日上限約束的路徑,
     * 而每一次嘗試都是一次完整的正規化與資料庫查詢。
     */
    public RecordOutcome submit(ManualSubmissionCommand command, AuthenticatedIdentity submitter) {
        Tlp tlp = resolveTlp(command.tlp(), submitter);
        quotas.consumeManualSubmissions(submitter.tenantId(), 1);
        SourceContext source = manualSource(ownerFor(tlp, submitter), tlp, redistributionFor(tlp));
        return executor.executeOne(source, IngestionRun.forManualSubmission(), toRecord(command));
    }

    /**
     * §9.7:預設 AMBER;CLEAR/GREEN 需 {@code ioc:publish};RED 不進入平台。
     * {@code AMBER_STRICT} 比預設更嚴格,不需額外權限。
     */
    private static Tlp resolveTlp(Tlp requested, AuthenticatedIdentity submitter) {
        Tlp tlp = requested == null ? Tlp.AMBER : requested;
        if (tlp == Tlp.RED) {
            throw new IllegalArgumentException("TLP:RED 不進入平台");
        }
        if ((tlp == Tlp.CLEAR || tlp == Tlp.GREEN) && !submitter.hasPermission("ioc:publish")) {
            throw new PublishNotPermittedException("Publishing to the public pool requires ioc:publish");
        }
        return tlp;
    }

    /**
     * 擁有權轉移(ADR 0019 第 2 節):公開的 TLP 一律落在 public tenant——
     * 「owner = 某租戶、tlp = CLEAR」的 Indicator 不符 §10.1 的公開情資定義、
     * 不進 public bloom、也不會被任何其他租戶看到。
     */
    private static TenantId ownerFor(Tlp tlp, AuthenticatedIdentity submitter) {
        return tlp == Tlp.CLEAR || tlp == Tlp.GREEN ? TenantId.PUBLIC : submitter.tenantId();
    }

    /**
     * 發布(CLEAR/GREEN)的來源記錄必須是可再散布的。
     *
     * <p>§9.7 的 {@code redistribution_policy = INTERNAL_ONLY} 說的是<strong>私有提交</strong>:
     * I14 規定「全來源皆 INTERNAL_ONLY 者不得出現在非擁有租戶的任何回應中」,而發布後的擁有者
     * 是 public tenant、擁有租戶豁免又刻意不適用於 public——結果是一筆<strong>誰都看不到</strong>
     * 的公開情資,{@code ioc:publish} 再一次變成沒有作用的權限(ADR 0019 第 2 節要消滅的正是這件事)。
     * 發布這個動作本身就是租戶對再散布的授權,故記為 {@code PUBLIC_REDISTRIBUTABLE}(ADR 0023)。
     */
    private static RedistributionPolicy redistributionFor(Tlp tlp) {
        return tlp == Tlp.CLEAR || tlp == Tlp.GREEN
                ? RedistributionPolicy.PUBLIC_REDISTRIBUTABLE
                : RedistributionPolicy.INTERNAL_ONLY;
    }

    SourceContext manualSource(TenantId owner, Tlp tlp, RedistributionPolicy policy) {
        Source manual = sources.findBySourceType(SourceType.MANUAL)
                .orElseThrow(() -> new IllegalStateException("sources 表缺少 MANUAL 來源;V4 種子未套用?"));
        return SourceContext.manualSubmission(manual, owner, tlp, policy);
    }

    private RawThreatRecord toRecord(ManualSubmissionCommand command) {
        return new RawThreatRecord(
                command.value(),
                command.type(),
                command.hashType(),
                clock.now(),
                command.confidence(),
                command.severity(),
                command.validUntil(),
                command.tags() == null ? Set.of() : command.tags(),
                // note 落 indicator_sources.raw_payload;平台沒有為它另開欄位(04 表 5)
                command.note() == null || command.note().isBlank() ? Map.of() : Map.of("note", command.note()));
    }
}
