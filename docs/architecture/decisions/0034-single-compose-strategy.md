# ADR 0034 — 單一 Compose 檔策略

- 狀態:accepted(2026-08-30,Phase 23 補記;決策自 Phase 2 起即以結構契約強制)
- 範圍:[05 §5.1–§5.3](../../spec/05-environment.md#52-唯一的-compose-檔)、
  `environment/docker-compose.yml`、`environment/docker/*/Dockerfile`、
  `.github/workflows/compose-validate.yml`

## 背景

四個環境(mvp / dev / staging / prod)的差異很大:要不要掛原始碼、要不要開 JDWP、
要啟動哪些服務(Redis、Kafka、Elasticsearch、Prometheus、Grafana)、用哪個 build target。
慣例作法是每個環境一份 compose 檔或一份 override 檔。

## 決策

**只有一份 `environment/docker-compose.yml`,也只有兩份 Dockerfile(backend / frontend)。**
禁止 `docker-compose.<env>.yml`、`docker-compose.override.yml`、`Dockerfile.<env>`。
環境差異只用兩種機制表達:

| 要控制的事 | 機制 |
|---|---|
| 這個服務要不要啟動 | `profiles:` + `COMPOSE_PROFILES` |
| 這個服務怎麼跑(build target、mount、port、JVM 參數、restart policy) | 環境變數 |

`volumes:` 無法用環境變數整段消失,因此 source/target/mode 三者都變數化,
staging/prod 掛 `environment/.noop` 這個唯讀空目錄
(`${BACKEND_MOUNT_SRC:-./.noop}:${BACKEND_MOUNT_DST:-/opt/noop}:${BACKEND_MOUNT_MODE:-ro}`)。

## 理由

1. **多檔案策略的失效模式是「靜默不一致」**:override 檔漏改一行,prod 就多掛了原始碼或多開了
   debug port,而 `docker compose config` 仍然成功。單一檔案讓「prod 到底長什麼樣」
   可以用一道指令印出來並用 grep 檢查——這正是 `compose-validate.yml` 與 DoD **M3-17** 的作法。
2. **可驗證性 > 便利性**:四個環境共用同一份拓樸,代表 CI 的
   `docker compose --env-file … config -q` 對四種環境跑同一件事;
   任何一個環境的變數缺漏都會在 CI 而不是在佈署當下被發現。
3. **與 Dockerfile 的 multi-stage 對稱**:`development` / `build` / `production` 三個 target
   放同一份 Dockerfile,環境只選 target。行為差異因此集中在**兩個**可讀的地方
   (compose 的變數表 + Dockerfile 的 target),而不是散在 N 份檔案的 N 份差異裡。

## 後果

- 新增環境變數時必須**同時**改 compose、五份 `.env*.example` 與
  [05 §5.4](../../spec/05-environment.md) 的變數表——`ConfigSymmetryTest` 會擋(Phase 22 實測有效)
- 新增服務必須指定 `profiles:`,否則四個環境都會啟動它
- 代價是 compose 檔較長且變數多;這是刻意的取捨:**長但可驗證**勝過短但會漂移
