# ADR 0036 — 移除 Lombok

- 狀態:accepted(2026-08-30,Phase 23 補記;禁令自 v2.0 起即為
  [00 §0.4](../../spec/00-master.md#04-coding-llm-執行規則) 規則 21)
- 範圍:[06 §6.3.1](../../spec/06-tech-stack.md#631-不使用-lombok強制)、`backend/pom.xml` 的
  `maven-compiler-plugin` 設定

## 背景

v1.1 的規格已經把 Lombok 縮到只剩 `@Slf4j` 與 `@RequiredArgsConstructor` 兩個註解。
v2.0 進一步**完全移除**,連 dependency 都不加。

## 決策

**不使用 Lombok。** Logger 手寫、建構子手寫。
`maven-compiler-plugin` 的 `annotationProcessorPaths` 只留 MapStruct 一條。

## 理由

留下那兩個註解要換來四筆成本:

1. JDK 23 起 `javac` 預設 `-proc:none`,必須明確開啟 annotation processing
2. ⚠️ **Lombok + MapStruct 的 processor 順序若錯誤,會產生空的 mapper 實作**:
   編譯成功,runtime 全是 null。這是全 AI 實作幾乎不可能自行診斷的一類 bug——
   它會檢查的所有訊號(編譯、啟動、型別)都是綠的,只有跑到那一行才炸,
   而錯誤現象(欄位是 null)看起來完全像業務邏輯的問題
3. 額外的 `lombok-mapstruct-binding` 版本 pin
4. Lombok 使用 JDK 內部 API 且**沒有 LTS**,而本專案的版本政策要求 Java 走最新 LTS
   ([06 §6.1.1](../../spec/06-tech-stack.md#611-分級支援窗口政策))

而它省下的是:

```java
private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);
```

加上一個**本來就該讓人類看見**的建構子。
[01 §1.8](../../spec/01-architecture.md#18-可讀性硬性規則與執行機制) 要求一律建構子注入;
手寫建構子在「人類易讀」這條標準上是加分,不是減分——它是這個類別**到底依賴什麼**的唯一清單。

## 後果

- 每個 service 多兩三行樣板;這是刻意付的代價
- `ParameterNumber ≤ 5` 的 checkstyle 規則因此變成有意義的設計壓力:
  建構子參數多到寫不下,通常代表該拆([08 §8.2](../../spec/08-ingestion-sdk.md#82-攝取管線) 的 pipeline 裝配即為一例)
- 第 2 點那類「所有訊號都是綠的」失效模式在本專案是**最高優先的排除對象**;
  任何要引入類似 annotation processor 的提案,必須先說明它如何避免同一種靜默失敗
