#!/usr/bin/env bash
# DoD Gate 檢查清單(docs/spec/15-dod-gates.md §15.1–15.3)。
# 由 dod.sh source,不直接執行。
#
# 每一項是一筆資料而不是一行指令,排程器才有辦法知道「誰跟誰能同時跑」:
#
#   reg <id> <lane> <resources> <after> <kind> <描述> <spec>
#
#   lane       build | frontend | static | stack | ci | aggregate
#              ——只是**給 agent 分工用的標籤**,不決定執行順序(順序由 resources + after 決定)
#   resources  逗號分隔,或 `-`。搶同一個資源的項目一定序列化,其餘一律並行
#   after      逗號分隔的相依:項目 ID 或 lane 名(該 lane 全部結束),或 `-`
#   kind       cmd     ——eval spec(在 repo 根)
#              func    ——同 cmd,但 spec 是 checks.sh 的函式名(僅為可讀性區分)
#              report  ——對 surefire 報告下斷言,spec 是測試類名(見 reports.sh)
#              vitest / playwright ——對前端測試報告下斷言,spec 是 suite 比對字串
#              aggregate ——所有指定字首的項目皆 PASS 才 PASS(不執行任何東西)
#   spec       依 kind 而定
#
# ID 以 `_` 開頭者為**前置步驟**,不是 DoD 項目:會執行、失敗會讓相依項目 FAIL,
# 但不計入 `N/M`、不印 [PASS]/[FAIL]。gate 歸屬由 ID 字首決定:
# mvp = M1-*;phase2 = M1-* ∪ M2-*;full = M1-* ∪ M2-* ∪ M3-*。

# Maven 縮寫。**不得加 `-o`**:離線模式會讓 cyclonedx 的 makeAggregateBom 靜默 skip,
# M3-20 於是找不到 bom.json(2026-08-31 實測)。
MVN='./backend/mvnw -f backend/pom.xml'

DOD_ID=()
DOD_LANE=()
DOD_RES=()
DOD_AFTER=()
DOD_KIND=()
DOD_DESC=()
DOD_SPEC=()

reg() {
    DOD_ID+=("$1")
    DOD_LANE+=("$2")
    DOD_RES+=("$3")
    DOD_AFTER+=("$4")
    DOD_KIND+=("$5")
    DOD_DESC+=("$6")
    DOD_SPEC+=("$7")
}

# ---------------------------------------------------------------------------
# 前置步驟(非 DoD 項目)
# ---------------------------------------------------------------------------

# `-Ptest-integration` 排除 @Tag("heavy")。全專案只有三個 heavy 類,一次批次跑完,
# 供 M2-22 / M2-24 / M3-02 三項的報告斷言使用——比各自起一輪 reactor 省兩次啟動。
reg _heavy build maven M1-01 cmd "heavy 測試批次(供 M2-22 / M2-24 / M3-02)" \
    "${MVN} test -Ptest-all -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ElasticsearchSearchTest,KafkaEventTest,SearchReconciliationTest"

# ---------------------------------------------------------------------------
# DoD-MVP(15 §15.1)
# ---------------------------------------------------------------------------

reg M1-01 build maven - cmd \
    "四個 module 皆編譯,L1–L3 測試通過" "${MVN} verify -Ptest-integration"
reg M1-02 build maven M1-01 func \
    "覆蓋率門檻達標(domain >= 85%);對 M1-01 的覆蓋率資料執行同一組規則" dod_coverage_threshold
reg M1-03 build - M1-01 report \
    "ArchUnit 規則全數通過" ArchitectureTest
reg M1-04 build maven M1-01 cmd \
    "Spotless 格式一致" "${MVN} spotless:check"
reg M1-05 build maven M1-01 cmd \
    "Checkstyle 五條可讀性規則通過" "${MVN} checkstyle:check"

# 前端:M1-10 的 api:check 會**寫入** src/api/generated/,因此獨佔 frontend-src 並排在最前;
# 其餘三項彼此無衝突,同時跑。
reg M1-10 frontend frontend-src - cmd \
    "前端型別與 OpenAPI 一致(非手寫)" "cd frontend && npm run api:check"
reg M1-06 frontend - M1-10 cmd \
    "前端 type check 通過" "cd frontend && npx tsc --noEmit"
reg M1-07 frontend - M1-10 cmd \
    "前端 lint 通過(含 feature 依賴規則)" "cd frontend && npx eslint . --max-warnings 0"
reg M1-08 frontend frontend-dist M1-10 cmd \
    "前端 build 通過" "cd frontend && npm run build"
reg M1-09 frontend - M1-10 cmd \
    "前端測試通過且覆蓋率達標" "dod_frontend_tests"

reg M1-11 static - - func \
    "四種 env 的 compose config 皆有效" dod_compose_config_all
reg M1-12 static - - func \
    "prod 設定不含原始碼掛載" dod_prod_no_source_mount
reg M1-13 static - - func \
    "prod 設定不含 JDWP debug agent" dod_prod_no_jdwp

# stack:與 build 互斥(host 端 mvn 寫的 target/classes 正是 mvp dev 容器掛載的目錄)。
reg M1-14 stack maven,docker-env build func \
    "up.sh mvp 成功,且只有 frontend/backend/postgres 三個容器" dod_up_mvp_three_services
reg M1-15 stack docker-env M1-14 func \
    "三個容器皆 healthy" dod_mvp_containers_healthy

reg M1-16 build - M1-01 report "Flyway 從空資料庫執行至最新版本成功" MigrationIntegrationTest
reg M1-17 build - M1-01 report "public system tenant 存在且不可刪除" PublicTenantIntegrationTest
reg M1-18 build - M1-01 report "樣本資料寫入成功(>=1000 IOC,涵蓋所有型別與四種 TLP)" SampleDataIntegrationTest
reg M1-19 build - M1-01 report "資料庫中無 TLP:RED 資料" TlpRedAbsenceTest
reg M1-20 build - M1-01 report "所有必要索引存在" RequiredIndexTest
reg M1-21 build - M1-01 report "MockOpenPhishAdapter 端對端:抓取→驗證→正規化→去重→合併→落庫" IngestionEndToEndTest
reg M1-22 build - M1-01 report "髒資料被拒絕並記入 ingestion_rejections,八種 reason 皆覆蓋" RejectionRuleTest
reg M1-23 build - M1-01 report "多來源重疊 IOC 依 IndicatorMergePolicy 正確合併" IndicatorMergePolicyTest
reg M1-24 build - M1-01 report "三步 valid_until 計算正確(含來源未明示與 FILE_HASH 分支)" ValidityPeriodTest
reg M1-25 build - M1-01 report "正規化規則七種型別全數正確" NormalizationTest
reg M1-26 build - M1-01 report "GET /api/v1/iocs 回傳正確,cursor 分頁可連續翻至最後一頁" CursorPaginationIntegrationTest
reg M1-27 build - M1-01 report "POST /api/v1/iocs/search 篩選正確" IocSearchIntegrationTest
reg M1-28 build - M1-01 report "安全測試 1、3、7、9 通過(匿名 TLP、跨租戶 404、限流 429、再散布作用域)" SecurityTest
reg M1-29 build - M1-01 report "STIX 匯出以 STIX 2.1 JSON Schema 驗證通過" StixSchemaValidationTest
reg M1-30 build - M1-01 report "五個 TLP marking UUID 與 OASIS 定義完全相符" StixTlpMarkingsTest
reg M1-31 build - M1-01 report "六種 IocType 的 pattern 模板與四種 hash 演算法對應正確" StixPatternTest
reg M1-32 build - M1-01 report "錯誤回應符合統一結構,含 traceId" ErrorResponseTest

reg M1-33 stack docker-env M1-14 cmd \
    "Swagger UI 可開啟" "dod_ensure_backend_up && curl -fsS http://localhost:8080/swagger-ui/index.html >/dev/null"
reg M1-34 build - M1-01 report \
    "所有端點皆有 summary、response schema 與至少一個範例" OpenApiCompletenessTest
reg M1-35 frontend - M1-09 vitest \
    "前端 IOC 搜尋頁能查到後端資料,四種狀態皆呈現" IocSearchPage
reg M1-36 stack docker-env M1-14 func \
    "前端 HMR 生效(修改 tsx 後 5 秒內更新)" dod_frontend_hmr
reg M1-37 stack maven,docker-env M1-36 func \
    "後端 reload 生效(reload.sh 後 10 秒內新行為生效)" dod_backend_reload
reg M1-38 stack maven,docker-env M1-37 func \
    "README 的啟動步驟可直接複製執行" dod_readme_quickstart

# ---------------------------------------------------------------------------
# DoD-Phase2(15 §15.2)
# ---------------------------------------------------------------------------

# 「DoD-MVP 全部仍通過」= 所有 M1-* 皆 PASS。原本是巢狀執行整個 `dod.sh mvp`,
# 語意相同但每一項要跑兩次(ADR 0052)。
reg M2-01 aggregate - - aggregate "DoD-MVP 全部仍通過(回歸)" M1

reg M2-02 build - M1-01 report "註冊/登入/refresh/登出全流程" AuthFlowIntegrationTest
reg M2-03 build - M1-01 report "Refresh token 輪替與重用偵測(重用觸發 family 全撤)" RefreshTokenRotationTest
reg M2-04 build - M1-01 report "五種角色的權限矩陣正確,@PreAuthorize 生效" RbacMatrixTest
reg M2-05 build - M1-01 report "API Key 建立(僅回傳一次)、撤銷、scope 檢查、不可提權" ApiKeyTest
reg M2-06 build - M1-01 report "跨租戶測試:每一個 tenant-scoped 端點皆回 404" CrossTenantIsolationTest
reg M2-07 build - M1-01 report "安全測試 1–9 全數通過" SecurityTest
reg M2-08 build - M1-01 report "方案配額生效,超限回 429 且帶 X-RateLimit-*" QuotaEnforcementTest
reg M2-09 build - M1-01 report "Redis 限流在兩個 app 實例下正確" DistributedRateLimitTest
reg M2-10 build - M1-01 report "Public bloom 與 tenant bloom 皆可生成" BloomGenerationTest
reg M2-11 build - M1-01 report "Bloom 位元序與雙雜湊索引符合 11.4 規格" BloomBitLayoutTest
reg M2-12 build - M1-01 report "Bloom checksum 驗證通過" BloomGenerationTest
reg M2-13 build - M1-01 report "TLP:GREEN 不進入 public bloom" BloomCoverageTest
reg M2-14 build - M1-01 report "Delta 生成與套用正確,resultingChecksum 相符" BloomDeltaTest
reg M2-15 build - M1-01 report "Delta 鏈超過上限時回 409 SNAPSHOT_REQUIRED" SyncEndToEndTest
reg M2-16 build - M1-01 report "完整同步流程端對端(manifest → delta → 套用 → 更新版本)" SyncEndToEndTest
reg M2-17 build - M1-01 report "manifest 含 coverage 與 notCovered 欄位" SyncEndToEndTest
reg M2-18 build - M1-01 report "手動提交 IOC 走完整 pipeline,預設 TLP:AMBER" ManualSubmissionTest
reg M2-19 build - M1-01 report "匯入超出方案上限回 413" ManualSubmissionTest
reg M2-20 build - M1-01 report "誤判回報後 status 由合併規則決定(非呼叫端指定)" FalsePositiveReportTest
reg M2-21 build - M1-01 report "Threat 實體與 threat_indicators、threat_external_references 可用" ThreatIntegrationTest
reg M2-22 build - _heavy report "Elasticsearch 索引建立、搜尋正確" ElasticsearchSearchTest
reg M2-23 build - M1-01 report "ES 掛掉時 API 降級為 PostgreSQL(200 + X-Search-Backend: postgres)" SearchFallbackTest
reg M2-24 build - _heavy report "Reconciliation 能偵測並修正 DB 與 ES 差異" SearchReconciliationTest

reg M2-25 stack maven,docker-env M1-38 func \
    "up.sh staging 成功且未掛載原始碼" dod_up_staging_no_mount
reg M2-26 stack docker-env M2-25 cmd \
    "Playwright E2E:匿名搜尋、登入、建立 API key、提交 IOC" dod_playwright_run
# 與 M1-01 逐字相同的指令 → memo 命中,不會真的再跑一次。
reg M2-27 build maven - cmd \
    "L1–L3 全通過,覆蓋率門檻達標" "${MVN} verify -Ptest-integration"

# ---------------------------------------------------------------------------
# DoD-Full(15 §15.3)
# ---------------------------------------------------------------------------

reg M3-01 aggregate - - aggregate "DoD-MVP 與 DoD-Phase2 全部仍通過" M1,M2

reg M3-02 build - _heavy report "Kafka(KRaft)啟動,事件正確發佈與消費" KafkaEventTest
reg M3-03 build - M1-01 report "消費端冪等(重複 eventId 不產生重複副作用)" EventIdempotencyTest
reg M3-04 build - M1-01 report "Kafka 不可用時業務操作不失敗" KafkaUnavailableTest
reg M3-05 stack - M2-26 playwright "WebSocket 通知端對端,斷線自動重連" websocket
reg M3-06 build - M1-01 report "Webhook 送達、HMAC 簽章正確(含 timestamp 防重放)" WebhookDeliveryTest
reg M3-07 build - M1-01 report "Webhook 失敗重試與連續 5 次後停用" WebhookDeliveryTest
reg M3-08 build - M1-01 report "訂閱過濾在伺服器端執行" WebhookFilterTest
reg M3-09 build - M1-01 report "Audit log append-only:應用角色的 UPDATE/DELETE 被 DB 拒絕" AuditAppendOnlyTest
reg M3-10 build - M1-01 report "稽核寫入失敗不影響主要業務操作" AuditFailureIsolationTest
reg M3-11 build - M1-01 report "六項資料保留任務正確清理" RetentionTaskTest
reg M3-11b build - M1-01 report "26 種稽核行為皆有實際寫入路徑(無永不可達行為)" AuditCompletenessTest
reg M3-12 build - M1-01 report "Prometheus 指標齊全(含每個 ingestion stage 的耗時)" MetricsCompletenessTest
reg M3-13 static - - func "Grafana dashboard 可載入(provisioning JSON 有效)" dod_grafana_provisioning
reg M3-14 build - M1-01 report "OpenTelemetry trace 從 API 串到 DB / Kafka / ES" TracePropagationTest
reg M3-15 build - M1-01 report "日誌不含敏感欄位" SensitiveLogTest
reg M3-16 build - M1-01 report "traceId 同時出現在錯誤回應與日誌" TracePropagationTest
reg M3-17 static - - func "prod 設定驗證:不掛原始碼、無明文 secret、CORS 非 *、Swagger 關閉" dod_prod_config_guard
reg M3-18 build - M1-01 report "prod 啟動守衛生效(樣板 JWT_SECRET 與 CORS=* 皆拒絕啟動)" StartupValidatorTest
reg M3-19 ci - - func "11 支 workflow 皆存在且 HEAD 的 CI 全綠(九支 push 觸發者)" dod_ci_green
# bom.json 由 cyclonedx 綁在 package,隨 M1-01 的 verify 產生。
reg M3-20 static - M1-01 func "SBOM 產出(backend CycloneDX + frontend npm sbom)" dod_sbom_present
reg M3-21 build maven M1-01 cmd "ctip-sdk 可獨立打包" "${MVN} -pl ctip-sdk package"
reg M3-22 build - M1-01 report "ExampleThreatSourceAdapter 可編譯並通過測試" ExampleAdapterTest
reg M3-23 static - - func "文件齊備(12 份必要文件皆存在且非空)" dod_docs_present
reg M3-24 static - - func "規格內部交叉引用皆指向存在的目標" dod_spec_xrefs

# ---------------------------------------------------------------------------
# 查詢
# ---------------------------------------------------------------------------

DOD_COUNT=${#DOD_ID[@]}

# dod_index_of <id> → 印出索引,找不到回 1
dod_index_of() {
    local i=0
    while [ "$i" -lt "$DOD_COUNT" ]; do
        if [ "${DOD_ID[$i]}" = "$1" ]; then
            printf '%s' "$i"
            return 0
        fi
        i=$((i + 1))
    done
    return 1
}

# dod_in_gate <id> <gate> → 該項目是否屬於此 gate(前置步驟一律屬於所有 gate)
dod_in_gate() {
    case "$1" in
        _*) return 0 ;;
    esac
    case "$2" in
        mvp) case "$1" in M1-*) return 0 ;; esac ;;
        phase2) case "$1" in M1-* | M2-*) return 0 ;; esac ;;
        full) case "$1" in M1-* | M2-* | M3-*) return 0 ;; esac ;;
    esac
    return 1
}
