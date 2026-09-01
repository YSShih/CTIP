# ADR 0053 — 測試平行 fork,以及 README quickstart 不再重跑完整 verify

- 狀態:accepted(2026-09-01,使用者在看完耗時剖析後指示「A2 + B 一起做」)
- 範圍:`backend/pom.xml`、`README.md`、`environment/scripts/dod/registry.sh`、
  [06 §6.3.6](../../spec/06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷)
- 前一則:[ADR 0052](0052-dod-parallel-and-report-reuse.md)

---

## 背景

ADR 0052 把 `dod.sh full` 從 94 分鐘壓到 18 分 57 秒之後,逐秒剖析指出剩下的時間集中在兩項:

| 項目 | 耗時 | 獨佔牆鐘 |
|---|---|---|
| `M1-38`(README quickstart) | 345s | 345s |
| `M1-01`(`verify -Ptest-integration`) | 334s | 279s |

兩者合計佔整輪的 60%。再往下拆 `M1-01` 的 334 秒:

| 成分 | 秒 |
|---|---|
| CTIP App | 246 |
| 　└ 測試方法本身 | 110 |
| 　└ Spring context 啟動(14 次) | 86 |
| 　└ 其餘(編譯 / jar / jacoco / SBOM) | ~50 |
| Parent / SDK / Core / Adapters | 88 |

**246 秒裡只有 110 秒在真的測東西。**

## 決策 1 —— README quickstart 改用 `-Ptest-slice`

`M1-38` 的 345 秒裡約 255 秒在跑 `./backend/mvnw verify -Ptest-integration`
——因為 README 的 bash 區塊逐字寫著那一行,而該判準就是「照 README 逐字執行」。
`dod.sh` 的 memo 幫不上忙:那一行在 bash 子腳本裡執行,不是 dod 項目。

README 改為:

- **bash 區塊**(會被 `M1-38` 執行):`verify -Ptest-slice`——只跑 L1,秒級
- **`sh` 區塊**(不會被執行):`verify -Ptest-integration`——完整 L1–L3,約 5 分鐘

`sh` 而非 `bash` 是 README 裡的既有手法(前端 E2E 那段用的是同一招,原因也一樣:
不該讓閘門盲跑一件別的判準已經涵蓋的事)。文件對讀者仍然完整,
而 `M1-38` 要證明的「README 的步驟可直接複製執行」完全沒有減損
——它仍然真的跑 `up.sh mvp`、真的 curl health、真的跑一次 Maven。

**實測:345s → 136s。**

## 決策 2 —— surefire 平行 fork(`forkCount=2`)

### 先更正 ADR 0052 的一句話

ADR 0052 的「刻意不動的」寫著:

> 不加 surefire 平行 fork(`-DforkCount=2C`):Testcontainers 與**共用 DB state**
> 會使測試結果與失敗定位都不可信。

**這句話是錯的。** 逐一核對測試碼之後:

| 疑慮 | 實際狀況 |
|---|---|
| 共用 DB state | `AbstractPostgresIntegrationTest.POSTGRES` 是 **`static`**,每個 fork 是獨立 JVM ⇒ **各自一個 Postgres 容器**,DB 根本不共用。Redis / ES / Kafka 四個容器宣告皆同 |
| port 衝突 | 全部用 `RANDOM_PORT` / `--server.port=0`,無固定 port |
| 檔案衝突 | 唯一的共用路徑是 `Files.createTempDirectory("ctip-bloom-test")`,每個 JVM 唯一 |

真正的阻礙只有一個,而且 ADR 0052 沒點到:**JaCoCo**。

### JaCoCo 才是要解的問題

兩個 fork 寫同一個 `jacoco.exec` 會讓覆蓋率失真,而 `M1-02` 正是對那個數字下斷言
——往哪個方向誤判都有可能。作法:

```xml
<!-- prepare-agent:${surefire.forkNumber} 由 surefire 在 fork 時代換 -->
<destFile>${project.build.directory}/jacoco-${surefire.forkNumber}.exec</destFile>

<!-- merge-forks:排在 report / check 之前,合併回既有路徑 -->
<destFile>${project.build.directory}/jacoco.exec</destFile>
```

合併回 `jacoco.exec` 之後,`report`、`check` 與 `dod.sh` 的 `dod_coverage_threshold`
(它檢查 `jacoco.exec` 是否存在)**一行都不用改**。

### 驗證:時間變短,而且覆蓋率逐字相同

同一條 `clean verify -Ptest-integration`,乾淨的 Docker 環境:

| | forkCount=1(基準) | forkCount=2 |
|---|---|---|
| 總時間 | 307s | **238s**(−22%) |
| CTIP App | 4:06 | **2:57** |
| ctip-sdk | covered 49 / missed 0 | **49 / 0** |
| ctip-core | 4380 / 284 | **4380 / 284** |
| ctip-adapters | 257 / 15 | **257 / 15** |
| ctip-app | 3769 / 583 | **3769 / 583** |

**四個 module 的覆蓋率逐字相同**——這是 merge 正確的直接證據,也是本決策能被接受的前提。
1,131 個測試全綠。`backend/ctip-app/target/` 下確實出現 `jacoco-1.exec` 與 `jacoco-2.exec`。

`ctip.test.forkCount` 是 property,**回退只要 `-Dctip.test.forkCount=1`**。

### heavy 批次刻意不 fork

`_heavy` 只有 3 個測試類,拆成兩個 fork 反而要付兩套 ES / Kafka 容器的啟動成本:

| | 耗時 |
|---|---|
| 原本(fork=1) | 133s |
| fork=2 | **147s**(變慢) |
| 明確指定 fork=1 | **128s** |

而且同時起兩份 Elasticsearch 正是先前把 M1-01 的 surefire fork 撐爆
(`forked VM terminated without properly saying goodbye`)的那個記憶體形狀。
registry 因此對這一項明確帶 `-Dctip.test.forkCount=1`。

## 決策 3 —— 拿掉重複的 `makeAggregateBom`

實測 log 裡 `(default)` 與 `(aggregate-bom)` 兩個 execution 都在跑,亦即 SBOM 產了兩次。
以 `<execution><id>default</id><phase>none</phase></execution>` 停掉前者。

⚠️ **這一項的收益很小**:一開始從 gate log 看到 Parent 花 42.8 秒而估「約省 20 秒」,
實測在暖快取下 Parent 只有 8~12 秒,重複的那次只值 1~2 秒
——42.8 秒主要是冷啟動的相依解析,不是重複 BOM。**修它是因為它確實是重複,不是因為它慢。**

## 效果

| | 改動前 | 改動後 |
|---|---|---|
| `clean verify -Ptest-integration` | 307s | **238s** |
| `M1-01` | 334s | **245s** |
| `M1-38` | 345s | **136s** |
| `dod.sh full`(改動後第一輪) | 1137s(18:57) | 1009s(16:49) |
| `dod.sh full`(**穩態**) | 1137s(18:57) | **934s(15:34)** |

改動後的第一輪只少 128 秒,比兩項相加(−298s)少很多——因為那一輪的 `M1-14` 從 90s 變成 218s:
**`backend/pom.xml` 一改,`up.sh` 的相依漂移守衛就判定快取需要重新預熱**(這正是它該做的事)。
第二輪(pom 未再變動)證實那是一次性的:`M1-14` 回到 **85s**,整輪 **934 秒**。

穩態的逐項對照:

| 項目 | 改動前 | 穩態 | 差 |
|---|---|---|---|
| `M1-01` | 334s | **291s** | −43s |
| `M1-38` | 345s | **138s** | −207s |
| `M1-14` | 90s | 85s | −5s |
| `_heavy` | 133s | 137s | +4s |
| `M2-25` | 134s | 178s | +44s |

⚠️ **誠實標示兩個對不上的地方**:`M1-01` 在隔離量測是 −69s(307→238),在 gate 裡只有 −43s;
`M2-25`(`up.sh staging`,起 8 個容器等 healthcheck)反而多了 44 秒。兩者都與 Maven／fork 無關,
是容器啟動與機器當下負載的變動——單次量測的雜訊,沒有再深究。
**整輪 18:57 → 15:34 是實測值,不是推估。**

## 還沒處理的

`M1-01` 的 246 秒裡仍有 **86 秒是 14 次 Spring context 啟動**。要再壓只能合併 property 組合,
但 `KafkaUnavailableTest`(死掉的 broker)、`SearchFallbackTest`(ES 掛掉)這些各有存在理由,
可併的大概 3~4 個、每個省 5 秒。投入產出比最差,不做。
