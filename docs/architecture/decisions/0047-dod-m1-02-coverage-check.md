# ADR 0047 — M1-02 不再重跑完整建置

- 狀態:accepted(2026-08-30,使用者指示「優化一下,同時確保結果正確」)
- 範圍:`environment/scripts/dod.sh`、[15 §15.1](../../spec/15-dod-gates.md)
- 前一則:[ADR 0046](0046-readme-restructure.md)

---

## 背景

使用者在 M3-01 完整重跑期間問「為什麼會等這麼久,哪裡有問題嗎」。實測:

```
17:11:36  開始
17:37     dod.sh mvp 38/38          ← 26 分鐘
18:27:57  dod.sh phase2 27/27       ← 76 分鐘(全程)
```

沒有卡住,但三個結構性原因疊起來:

| # | 原因 | 性質 |
|---|---|---|
| 1 | **mvp gate 跑兩次** —— M3-01 = `dod.sh mvp && dod.sh phase2`,而 phase2 的 M2-01 又是 `dod.sh mvp` | [15 §15.2](../../spec/15-dod-gates.md) 明文的**回歸**設計,正確 |
| 2 | 每個 `-Dtest=<類名>` 是一次獨立的 maven 呼叫(mvp 21 次、phase2 24 次),各自付 JVM + reactor 掃描 + Spring context + Testcontainers | §15.0 要求逐項獨立回報 PASS/FAIL 的必然代價,正確 |
| 3 | **M1-01 與 M1-02 是逐字相同的指令**,各 **5:00**(實測) | ⚠️ **純粹浪費** |

第 3 點是本 ADR 要處理的。規格的指令欄原文:

| ID | 檢查 | 指令 |
|---|---|---|
| M1-01 | 四個 module 皆編譯,L1–L3 測試通過 | `./mvnw -f backend/pom.xml verify -Ptest-integration` |
| M1-02 | 覆蓋率門檻達標(domain ≥ 85%) | **同上**(JaCoCo `check` 綁在 `verify`) |

「同上」的語意是**一次執行同時證明兩件事** —— JaCoCo 的 `check` execution 綁在 `verify` 上,
M1-01 跑完的那一刻覆蓋率門檻就已經被強制過了。但 `dod.sh` 把它照字面又跑了一次完整建置。

實測 `dod.sh mvp M1-01` = **5 分 00 秒**;M1-02 是逐字相同的指令,因此也是 5 分。
mvp 在 M3-01 底下跑兩次(M2-01 是巢狀回歸)→ 4 次完整建置,其中 **2 次是重複的**。

---

## 決策

M1-02 改為對 **M1-01 產生的覆蓋率資料**執行同一組規則:

```bash
./backend/mvnw -f backend/pom.xml jacoco:check@check
```

`@check` 是 execution id 的綁定(Maven 3.3.1+),因此套用的是 pom 裡那個 `check` execution 的
**完全相同的 rules**,而不是 plugin 預設值:

- parent:`BUNDLE` LINE ≥ `${ctip.coverage.line-minimum}`(0.60)
- `ctip-core` override(`combine.self="override"`):`PACKAGE` `com.ctip.domain.*` ≥ **0.85**
  ——正是本項要驗的 domain 門檻

**這比原本驗得多。** 原本只是「同一個指令再退出 0 一次」;現在是真的對覆蓋率數字下斷言。

### 假綠守衛(必要,非裝飾)

`jacoco.exec` 不存在時 `jacoco:check` 會**靜默通過**。這一點是實測確認的,不是推測:

```
$ mv backend/ctip-core/target/jacoco.exec /tmp/
$ ./backend/mvnw -q -f backend/pom.xml -pl ctip-core jacoco:check@check
$ echo $?
0        ← 沒有任何覆蓋率資料,卻通過
```

`dod_coverage_threshold` 因此先確認四個 module 的 `target/jacoco.exec` 都存在,缺任一即 FAIL
並提示先跑 M1-01。**沒有資料就是「這一項沒被驗到」,必須是 FAIL 而不是 PASS**
——這與 [ADR 0017](0017-gate-credibility.md) 對 `failIfNoSpecifiedTests=false` 的處置同一個道理。

---

## 驗證(三項,全部實測)

| 驗證 | 結果 |
|---|---|
| **正向** ——`dod.sh mvp M1-02` | `[PASS]`,**4.216 秒**(原 5 分 00 秒) |
| **否定 1** —— 把 `ctip-core` 的 domain 門檻由 `0.85` 改為 `0.999` | `[FAIL]`,並逐套件印出實際值:`com.ctip.domain.notification` 0.858、`stix` 0.891、`indicator` 0.971、`indicator.normalization` 0.943 ——證明套用的是 PACKAGE override 而非 plugin 預設 |
| **否定 2** —— 移走 `ctip-core/target/jacoco.exec` | `[FAIL]`「找不到覆蓋率資料(jacoco.exec): ctip-core」;同一情況下裸 `jacoco:check@check` 是 **exit 0** |
| **回歸** —— 完整 `dod.sh mvp` | 見 `docs/progress.md` 本輪紀錄 |

否定 1 順帶留下一個有用的事實:**domain 各套件的實際行覆蓋率是 0.858–0.971**,
`com.ctip.domain.notification` 的 0.858 距離 0.85 的門檻只有 0.008。

---

## 刻意不動的兩件事

1. **M2-01 的巢狀 mvp gate** —— 那是規格明文的回歸檢查(「DoD-MVP 全部仍通過(**回歸**)」)。
   M3-01 因此跑兩次 mvp 是**設計**,不是缺陷:M2/M3 的程式碼可能打破 M1 的判準,
   而那正是這一項要抓的。
2. **21 + 24 次獨立的 `-Dtest=` 呼叫** —— 這些測試在 M1-01 的完整 verify 裡確實已經跑過,
   但 §15.0 的契約是「逐項印 `[PASS]`/`[FAIL]`,任一失敗不中止後續」。
   合併成一次執行會失去逐項歸因,那是閘門的核心價值。

**「90 項」不變**,M1-02 仍是獨立的一項、仍可單獨執行。

---

## 效果 —— 以及一個不能宣稱的數字

**確定的**(單項實測,同一台機器連續量測):

| | 前 | 後 |
|---|---|---|
| M1-02 | **5 分 00 秒**(= M1-01 實測值,兩者同指令) | **4.216 秒** |

**不能宣稱的**:整輪 `dod.sh mvp` 的牆鐘時間是 **26:08(優化前)→ 25:08(優化後)**,
只少 1 分鐘,**與單項省下的 5 分鐘對不上**。

兩次執行不是受控比較——優化前那一輪起始時環境已是 mvp,優化後那一輪緊接在
`dod.sh phase2` 之後,而 M2-25 把環境留在 **staging**(Kafka／Elasticsearch／
Prometheus／Grafana 五個額外容器),M1-14 的 `up.sh mvp` 因此要先把它們收掉再起 mvp。
`dod.sh` 的 `check` 只在失敗時印出被捕捉的輸出,所以**無法從日誌證實**這個推測;
要分離變因得在相同起始狀態下各再跑一輪(每輪約 25 分鐘),代價高於這個數字的價值。

**因此本 ADR 只主張單項的 5 分鐘 → 4.2 秒,不主張整輪的節省幅度。**
真正的效果會在下一次乾淨的 `dod.sh full` 實跑時顯現。
