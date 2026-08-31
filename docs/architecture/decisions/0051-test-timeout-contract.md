# ADR 0051 — 全域測試逾時契約(30 秒)

- 狀態:accepted(2026-08-31,使用者在 `dod.sh` 加速的討論中指示「全域限定 30 秒,超過的請提出讓我判斷怎麼處理」)
- 範圍:`backend/pom.xml`、`backend/ctip-app/src/test/java/com/ctip/KafkaUnavailableTest.java`、
  [14 §14.8](../../spec/14-testing.md#148-測試逾時契約強制)、[06 §6.3.6](../../spec/06-tech-stack.md#636-spring-boot-4-模組化與-testcontainers-2x編譯地雷)
- 前一則:[ADR 0050](0050-version-table-catchup.md)

---

## 背景

`dod.sh full` 的一次過夜執行**無聲卡死 8 小時 51 分**(`docs/progress.md`,2026-08-31)。
電腦進入睡眠後 Testcontainers 的連線壞掉,測試永遠等下去——**不會逾時、不會報錯、也不會結束**,
外觀上與「跑得比較慢」完全無法區分,因此連續數次被回報成「進度正常」。

清點當時的防護:**全專案沒有任何一個 `@Timeout`,也沒有 `junit-platform.properties`**。
一個卡住的測試在 JVM 這一層完全沒有上限。

同一輪討論中,`docs/progress.md:1830` 記著「`KafkaUnavailableTest` 的連線逾時就佔掉約 10 分鐘
(既有現象)」,而它在 `-Ptest-integration` 內,是閘門的臨界路徑。

## 決策 1 —— 逾時設定放 surefire,不放 `junit-platform.properties`

parent 的 surefire `<systemPropertyVariables>` 設四個 JUnit 設定參數:

| 參數 | 值 |
|---|---|
| `junit.jupiter.execution.timeout.testable.method.default` | `${ctip.test.timeout}` = `30s` |
| `junit.jupiter.execution.timeout.lifecycle.method.default` | `${ctip.test.lifecycle.timeout}` = `5m` |
| `junit.jupiter.execution.timeout.thread.mode.default` | `SEPARATE_THREAD` |
| `junit.jupiter.execution.timeout.mode` | `disabled_on_debug` |

四個 module 只有 `ctip-app` 有 `src/test/resources`,散成四份 `junit-platform.properties`
必然漂移;JUnit Platform 同樣從 system property 讀設定參數,而放在 pom 還多一個好處:
`-Dctip.test.timeout=60s` 就能覆寫。

**測試方法與 lifecycle 方法分開計**:`@BeforeAll` / `@BeforeEach` 可能觸發 Testcontainers 與
Spring context 啟動,套 30 秒必然誤殺。

### `SEPARATE_THREAD` 不是可選項

JUnit 預設的 `SAME_THREAD` **不會中斷卡住的呼叫**,只在方法自己回來之後才判定超時。
以它設定的「30 秒上限」對本 ADR 的起因(卡在壞掉的連線上)**完全無效**——測試照樣卡滿,
只是最後多印一行失敗。實測(見下方驗證)顯示 `SEPARATE_THREAD` 會在 30.12 秒搶先中斷一個
`sleep(35s)` 的測試,`SAME_THREAD` 則會等滿 35 秒。

代價是測試主體跑在另一條執行緒上,會與執行緒繫結的狀態衝突。
**本專案的測試碼零個 `@Transactional`**(2026-08-31 全檔核對),因此安全;
日後若引入 `@Transactional` 測試,必須為該類別另標 `@Timeout(threadMode = SAME_THREAD)`。

## 決策 2 —— 30 秒是實測後訂的門檻,不是拍腦袋

訂定當時的實測:**1,145 個測試方法沒有任何一個超過 30 秒**,最慢的是 3.89 秒
(`AuthHardeningTest#lockedAccountIsIndistinguishableFromAnUnknownOne`),約有 8 倍餘裕。

⚠️ 中途差點誤判:類別層級確實有 15 個 ≥27 秒,一度以為 30 秒會弄紅一片。
那是 **Spring context 與 Testcontainers 啟動**被計進類別總時間,不是方法本身
——`<testcase>` 的 `time` 屬性才是本設定管的東西。

## 決策 3 —— `KafkaUnavailableTest` 補上 consumer / admin 的逾時

原本只有 producer 那半被收緊(`max.block.ms` / `delivery.timeout.ms` / `request.timeout.ms` / `retries`)。
`NotificationEventConsumer` 的 `@KafkaListener` 容器與 admin client 一樣會對著不存在的 broker
反覆重連,各自吃掉預設的數十秒。補上兩邊的
`socket.connection.setup.timeout{,.max}.ms` / `default.api.timeout.ms` / `request.timeout.ms` / `reconnect.backoff.max.ms`。

bootstrap 位址一併參數化(`${ctip.test.kafka.unreachable-broker:192.0.2.1:9092}`),
但**預設維持 TEST-NET-1**——理由見下。

### 一個被實測推翻的假設

原本的假設是:70 秒的大宗是 TEST-NET-1 這個**不可路由**位址造成的
——封包被靜默丟棄,每次 connect 都要等滿 OS 的 TCP 逾時(macOS 約 75 秒)。
依此改指向關閉的本機 port(`127.0.0.1:1`,拿的是即時的 `ECONNREFUSED`)。

實測(單獨執行該類別,同一台機器連續三次):

| 版本 | 耗時 |
|---|---|
| 完全原始 | **70.99 s** |
| TEST-NET-1 + 新的 consumer/admin 逾時 | **34.04 s** |
| `127.0.0.1:1` + 新的 consumer/admin 逾時 | 36.30 s |

**位址不是原因**(34 vs 36 秒在雜訊範圍內),省下的 37 秒全部來自逾時參數。
既然如此就沒有理由放棄原作者「保留位址保證不會意外連到任何東西」這個性質,
預設改回 TEST-NET-1,只保留參數化(`-Dctip.test.kafka.unreachable-broker=127.0.0.1:1` 可切換)。

> 這一則連帶更正 `docs/progress.md:1830` 的「約 10 分鐘」:該現象在本 ADR 之前就已不存在
> (產生該筆記錄之後補上的那組 producer 參數修掉的),只是沒有回寫。

## 驗證(全部實測)

| # | 動作 | 結果 |
|---|---|---|
| T1 | `clean verify -Ptest-all` | **BUILD SUCCESS,5:37,1,145 tests 全綠**,零個因 30 秒上限被誤殺 |
| T2 | 暫時加一個 `Thread.sleep(35_000)` 的測試 | `Time elapsed: **30.12 s**` + `PreemptiveTimeoutUtils$ExecutionTimeoutException`——**在 30 秒被搶先中斷**,不是等滿 35 秒。探針測試用完即刪 |
| T3 | `-Dctip.test.timeout=1ms` 重跑 `ctip-sdk` | 全數 `TimeoutException: … timed out after 1 millisecond`——參數化確實生效,不是寫死 |
| T4 | `KafkaUnavailableTest` 改前/改後(單獨執行) | **70.99 s → 34.04 s**;在完整 reactor 執行下為 **6.04 s**(context 與容器已暖,排名第 12,已不在最慢之列) |

## 這一層擋不住什麼

本設定在 JVM 內,管不到「JVM 整個卡死」或「Maven 呼叫本身不回來」。
過夜那次 8 小時 51 分的卡死要靠 `dod.sh` 的**逐項逾時**(下一則 ADR)才擋得住。
兩層分工寫在 [14 §14.8](../../spec/14-testing.md#148-測試逾時契約強制) 表末。

## 刻意不動的

- **不加 surefire 平行 fork(`-DforkCount=2C`)**:Testcontainers 與共用 DB state 會使測試結果
  與失敗定位都不可信。加速閘門有別的路可走(下一則 ADR)。
- **不為現有測試加任何 `@Timeout` 豁免**:實測沒有一個超過,加了就是預留給未來的破窗。
