# ADR 0001 — Phase 3 的五項規格衝突處置

- **狀態**:accepted
- **日期**:2026-08-21(Phase 3)

## 決策 1:`trg_tenants_protect_public` 觸發器(V2)

### 背景

不變量 T2(`02-ddd-model.md` §2.3)要求 public system tenant「不可刪除、不可更名、不可變更 type」,
DoD M1-17 要求可執行地驗證「存在且不可刪除」。domain 層的強制(`Tenant.rename()` 拒絕等)屬 Phase 4,
且 domain 防護擋不住繞過 application 層的寫入(手動 SQL、未來的維運腳本、bug)。

### 決策

在 `V2__seed_system_tenant.sql` 建立 `BEFORE UPDATE OR DELETE` 觸發器 `trg_tenants_protect_public`,
拒絕對 `00000000-0000-0000-0000-000000000000` 的 DELETE 與 slug/name/type 變更。

### 理由與取捨

- 規格模糊處依 `00-master.md` §0.4 優先序:**安全性優先**——這是多租戶隔離的錨點資料列,
  DB 層深度防禦的成本(一個觸發器)遠低於錨點被誤刪的後果。
- 與 `04-data-dictionary.md` §4.0「`updated_at` 不使用 DB trigger」不衝突——該規則針對 `updated_at` 維護;
  本觸發器不碰 `updated_at`。
- Phase 4 的 domain 層仍須依 T2 實作同樣的拒絕(第一道防線);觸發器是最後一道。

## 決策 2:`stix_objects.threat_id` 的 FK 延後至 V25

### 背景

`04-data-dictionary.md` 表 8(M1,`V7__create_stix.sql`)定義了
`fk_so_threat FOREIGN KEY (threat_id) REFERENCES threats(id)`,但 `threats` 表屬 M2
(`V25__create_threats.sql`),M1 明令不得建立。規格自身衝突,無法照字面實作。

### 決策

V7 保留 `threat_id UUID` 欄位與 `ck_so_origin` 檢查,**不建 FK**;
FK 由 M2 的 `V25__create_threats.sql` 在建立 `threats` 後以
`ALTER TABLE stix_objects ADD CONSTRAINT fk_so_threat ...` 補上(已記入 progress 給 Phase 18 的注意事項)。

### 理由

- 唯一可建置的選項;欄位先行保留使 M1 的資料形狀與最終 schema 一致,M2 不需搬移資料。
- M1 期間 `threat_id` 恆為 null(`ck_so_origin` 允許),無孤兒風險。

## 決策 3:JDWP 從 `BACKEND_JAVA_OPTS` 移至 spring-boot:run 的 `jvmArguments`

### 背景

`05-environment.md` §5.5 要求 mvp/dev 的 `BACKEND_JAVA_OPTS` 含 JDWP,而 §5.6 把它注入
`JAVA_TOOL_OPTIONS`。dev 容器的 CMD 是 `./mvnw -o spring-boot:run`——`JAVA_TOOL_OPTIONS`
作用於**每一個** JVM:Maven JVM 先綁 5005,forked app JVM 再綁必然
`Address already in use` 而啟動失敗,backend 容器 crash-loop,DoD M1-14/15 必不通過。
且即便不失敗,debugger 附著到的會是 Maven JVM 而非應用。照 §5.5 字面實作 = dev 環境無法啟動。

### 決策

- mvp/dev 的 `.env` 樣板中 `BACKEND_JAVA_OPTS` 留空(附註解說明)。
- JDWP 改設定於 `ctip-app/pom.xml` 的 spring-boot-maven-plugin `<jvmArguments>`——
  只作用於 `spring-boot:run`(即 dev 容器)forked 出來的應用 JVM,debug port 仍為 5005。

### 影響

- prod/staging 不受影響(`java -jar` 不經 plugin;`BACKEND_JAVA_OPTS` 語意不變)。
- DoD M1-13(prod 無 JDWP)仍通過;沒有任何檢查要求 dev 的 compose config 含 jdwp 字串。
- 若日後在無 Docker 的 host 直接跑 `mvnw spring-boot:run`,5005 被占用時可用
  `-Dspring-boot.run.jvmArguments=` 覆寫。

## 決策 4:postgres volume 掛載點改為 `/var/lib/postgresql`

### 背景

`05-environment.md` §5.6 的 compose 骨架掛 `postgres-data:/var/lib/postgresql/data`,
但 `postgres:18`(§6.2.4 指定)自 18 起改用 major-version 子目錄佈局,
**掛載 `…/data` 會使容器直接拒絕啟動**(entrypoint 的資料保護檢查;上游建議掛載點為
`/var/lib/postgresql`)。照 §5.6 字面實作 = postgres 必不啟動,`depends_on` 卡死。

### 決策

compose 的 postgres volume 改掛 `postgres-data:/var/lib/postgresql`(上游 18+ 建議組態)。
其餘骨架維持逐字。

### 影響

- 全新環境無資料搬移問題;未來升 PG 19 可用 `pg_upgrade --link`(這正是上游改佈局的目的)。
- 這實質上是規格的第五項建置阻斷缺陷(§5.8 列了四項),已回報。

## 決策 5:frontend 的 node_modules 以 named volume 遮罩

### 背景

dev 模式把 `../frontend` 綁定掛載進容器,host(macOS/arm64)的 `node_modules`
含平台原生 binding(Vite 8 的 rolldown 等),在 Linux 容器內載入必然失敗
(`Cannot find module '@rolldown/binding-linux-*'`)。§5.6 骨架未處理此問題,
dev/mvp 的 frontend 容器照字面實作必 crash。

### 決策

- compose 的 frontend 增加一條與 `maven-cache` 同模式的變數化掛載:
  `${NODE_MODULES_SRC:-node-modules}:${NODE_MODULES_DST:-/opt/noop-node-modules}`,
  並在頂層宣告 `node-modules` volume。
- mvp/dev 設 `NODE_MODULES_DST=/workspace/node_modules`(遮罩 host 版本);
  staging/prod 不設定,依預設值退化為無害掛載。
- `up.sh` 於首次啟動時在容器內 `npm ci` 灌入 volume(與 backend 的 maven-cache
  預熱同一機制)。

### 影響

- 容器內的 node_modules 為 Linux 原生安裝,與 host 完全隔離;host 的開發流程不變。
- 沿用規格既有的「mount 變數化 + 頂層 named volume」模式(§5.2、maven-cache 前例),
  未引入新機制。
