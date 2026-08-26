# ADR 0010 — DevTools trigger file:根治 host 建置打死 dev 容器的問題

- 狀態:accepted(2026-08-26,M1 閘門後的環境維護;使用者要求根治)
- 範圍:環境契約(05 §5.11 hot reload)、ctip-app pom、mvp/dev profile、reload.sh

## 問題

dev 容器(`mvnw spring-boot:run`)與 host **共享 bind mount 的 `backend/*/target/classes`**。
Spring DevTools 預設「看到 classpath 任何變更就熱重啟」;host 端跑 `mvnw verify`/`clean`
會歷時數十秒重寫數百個 class 檔,DevTools 的輪詢(1s + 400ms quiet)幾乎必然在「半寫入」
狀態觸發重啟 → application context 啟動失敗(`NoClassDefFoundError`、bean not found),
**JVM 存活、容器顯示 Up,但 8080 無人監聽**,且不會自癒。Phase 12 的 DoD 閘門(host 端
M1-01/M1-16~32 的 maven 步驟)就多次觸發,dod.sh 曾因此需要 M1-14/M1-33 前的自我修復。

## 決策:restart 只由 trigger file 觸發

Spring DevTools 官方機制 `spring.devtools.restart.trigger-file`:設定後 classpath 變更
**不再**直接觸發重啟,只有 trigger file 本身變更才重啟。配合本專案「reload 一律走
reload.sh」的既有契約(05 §5.11),語意完全吻合:

1. **`application-mvp.yml` / `application-dev.yml`**:`spring.devtools.restart.trigger-file: .reloadtrigger`
   (staging/prod 跑 packaged jar,DevTools 本來就 inert,不需設定)。
2. **trigger file 放 `backend/ctip-app/.devtools/.reloadtrigger`(進版控)**——刻意**不放**
   `target/classes`(host clean 會刪、rebuild 會重寫 → 又變成 host 觸發源)也不放
   `src/main/resources`(會被 process-resources 複製進 target/classes,同樣被 host 建置改到)。
3. **ctip-app pom 的 spring-boot-maven-plugin 加 `additionalClasspathElements`** 把
   `.devtools/` 掛進 spring-boot:run 的 classpath(DevTools 只監看 classpath 上的目錄)。
   ⚠️ Boot 4 plugin 將 run mojo 的 `directories` 參數**更名為 `additionalClasspathElements`**
   (3.2.0 起)——舊名不報錯但靜默無效(已回寫 06 §6.3.6)。
4. **reload.sh**:容器內編譯**成功後**才 `touch .devtools/.reloadtrigger` → 重啟時 classpath
   保證完整一致。

## 實測

- reload.sh → DevTools 重啟於 10 秒內生效(dod M1-37 PASS)。
- host `mvnw -pl ctip-app -am compile`(重寫全部共享 classes):容器 **0 重啟**,app 保持 UP。
- host `mvnw clean`(刪除全部 classes):容器 **0 重啟**,app 保持 UP(已載入的類別在 JVM 內,
  不受檔案刪除影響),隨後 host recompile 亦無影響。

## 保留的防線

dod.sh 的 M1-14/M1-33 前自我修復(ADR 0009)保留為縱深防禦;README 疑難排解的手動
restart 指令保留為最後手段(理論上不再需要)。
