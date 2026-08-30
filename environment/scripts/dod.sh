#!/usr/bin/env bash
# Definition of Done Gate 檢查(docs/spec/15-dod-gates.md)。
#
# 用法:
#   ./environment/scripts/dod.sh mvp                # DoD-MVP 全部 38 項
#   ./environment/scripts/dod.sh phase2             # DoD-Phase2 全部 27 項
#   ./environment/scripts/dod.sh full               # DoD-Full 全部 25 項
#   ./environment/scripts/dod.sh mvp M1-14          # 只執行單一項目
#   ./environment/scripts/dod.sh mvp --only M1-11 --skip M1-01
#
# 契約(§15.0):逐項印 [PASS]/[FAIL] 與失敗輸出;任一失敗不中止後續;
# 全過 exit 0,否則 exit 1 並列出失敗清單;結尾印出「需人工確認」清單。

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_common.sh"
set +e  # 覆寫 _common.sh 的 -e:gate 必須跑完全部檢查,不因單項失敗中止

# ---------------------------------------------------------------------------
# 參數解析
# ---------------------------------------------------------------------------
GATE=""
ONLY_IDS=""
SKIP_IDS=""

while [ $# -gt 0 ]; do
  case "$1" in
    --only)
      [ -n "${2:-}" ] || die "--only 需要一個項目 ID"
      ONLY_IDS="${ONLY_IDS} $2"; shift 2 ;;
    --skip)
      [ -n "${2:-}" ] || die "--skip 需要一個項目 ID"
      SKIP_IDS="${SKIP_IDS} $2"; shift 2 ;;
    -*)
      die "未知選項:$1" ;;
    *)
      if [ -z "$GATE" ]; then GATE="$1"; else ONLY_IDS="${ONLY_IDS} $1"; fi
      shift ;;
  esac
done

case "$GATE" in
  mvp|phase2|full) ;;
  *) die "用法:dod.sh <mvp|phase2|full> [id] [--only <id>] [--skip <id>]" ;;
esac

in_list() { # in_list <item> <space-separated-list>
  case " $2 " in *" $1 "*) return 0 ;; *) return 1 ;; esac
}

should_run() {
  local id="$1"
  if [ -n "$ONLY_IDS" ]; then in_list "$id" "$ONLY_IDS" || return 1; fi
  if in_list "$id" "$SKIP_IDS"; then return 1; fi
  return 0
}

# ---------------------------------------------------------------------------
# 互斥鎖(§15.0):同一個 repo 同時只能有一個 gate 在跑
#
# 兩個 gate 並行會共用 backend/*/target,一邊 clean 就把另一邊的 classes 抽掉,
# 症狀是 `cannot find symbol` 之類完全不像併發問題的編譯錯誤;容器與 Testcontainers
# 也會互搶記憶體(「Timed out waiting for log output」)。這種失敗的分數完全不可信,
# 因此寧可拒絕啟動也不要產生一份看似有結論的報告(2026-08-29 Phase 19 實測)。
#
# M2-01 會巢狀呼叫 `dod.sh mvp`:子行程繼承 CTIP_DOD_LOCK,不再取鎖也不刪鎖。
# ---------------------------------------------------------------------------
if [ -z "${CTIP_DOD_LOCK:-}" ]; then
  LOCK_DIR="${TMPDIR:-/tmp}/ctip-dod-$(printf '%s' "$REPO_ROOT" | cksum | tr -d ' ' | tr -s ' ' '_')"
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    LOCK_PID="$(cat "${LOCK_DIR}/pid" 2>/dev/null || true)"
    if [ -n "$LOCK_PID" ] && kill -0 "$LOCK_PID" 2>/dev/null; then
      die "已有另一個 dod.sh 在執行(pid ${LOCK_PID})。gate 會跑數十分鐘,並行執行的分數不可信;
     請等它結束(它的退出碼才是結論),或 kill 後重跑。鎖:${LOCK_DIR}"
    fi
    # 上一次被 kill 而沒清掉的殘鎖:接手
    warn "接手殘留的 dod.sh 鎖(pid ${LOCK_PID:-未知} 已不存在)"
    rm -rf "$LOCK_DIR" && mkdir "$LOCK_DIR"
  fi
  printf '%s' "$$" > "${LOCK_DIR}/pid"
  export CTIP_DOD_LOCK="$LOCK_DIR"
  CTIP_DOD_LOCK_OWNER=1
fi

# ---------------------------------------------------------------------------
# 檢查執行框架(含記憶化:規格中「同上」的項目共用同一指令,不重跑)
# ---------------------------------------------------------------------------
WORKDIR="$(mktemp -d)"
cleanup() {
  rm -rf "$WORKDIR"
  [ -n "${CTIP_DOD_LOCK_OWNER:-}" ] && rm -rf "${CTIP_DOD_LOCK}"
  return 0
}
trap cleanup EXIT

TOTAL=0
PASS_COUNT=0
FAILED_IDS=""

check() { # check <id> <描述> <指令字串或函式名>
  local id="$1" desc="$2" cmd="$3"
  should_run "$id" || return 0
  TOTAL=$((TOTAL + 1))

  local key status
  key="$(printf '%s' "$cmd" | cksum | tr ' ' '_')"
  if [ -f "${WORKDIR}/memo-${key}.status" ]; then
    status="$(cat "${WORKDIR}/memo-${key}.status")"
  else
    (cd "$REPO_ROOT" && eval "$cmd") >"${WORKDIR}/memo-${key}.out" 2>&1
    status=$?
    printf '%s' "$status" > "${WORKDIR}/memo-${key}.status"
  fi

  if [ "$status" -eq 0 ]; then
    printf '%s[PASS]%s %s %s\n' "${C_GREEN}" "${C_RESET}" "$id" "$desc"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    printf '%s[FAIL]%s %s %s\n' "${C_RED}" "${C_RESET}" "$id" "$desc"
    sed 's/^/       | /' "${WORKDIR}/memo-${key}.out" | tail -n 60
    FAILED_IDS="${FAILED_IDS} ${id}"
  fi
}

# Maven 縮寫:單一測試類以 test-all profile 執行(不受預設 unit tag 過濾),
# 並容忍其他 module 無符合的測試類
MVN='./backend/mvnw -f backend/pom.xml'

# surefire 的 failIfNoSpecifiedTests=false(06 §6.3.6 第 4 條)是必要的——判準的 -Dtest=<類名>
# 會對 reactor 每個 module 執行,沒有該測試類的 module 不得因此失敗。
# 但它的副作用是:**測試類根本不存在時,surefire 跑 0 個測試,build 仍然成功**。
# 於是尚未實作的 phase 的 DoD 項目會一路 [PASS]——閘門量不到它該量的東西。
# 實測:Phase 14/15/16 一行程式都沒有時,dod.sh phase2 仍回報 27/27 全綠。
# 因此先驗證每個測試類確實存在,再交給 Maven(ADR 0017)。
mvn_test() { # mvn_test <逗號分隔的測試類名>
  local classes="$1" missing="" name
  local IFS=','
  for name in $classes; do
    name="${name%%#*}"          # 去掉 -Dtest=Class#method 的方法段
    [ -n "$name" ] || continue
    if ! find "${REPO_ROOT}/backend" -path '*/src/test/java/*' -name "${name}.java" \
         -not -path '*/target/*' 2>/dev/null | grep -q .; then
      missing="${missing} ${name}"
    fi
  done
  unset IFS
  if [ -n "$missing" ]; then
    echo "找不到測試類:${missing}"
    echo "(surefire 的 failIfNoSpecifiedTests=false 會讓不存在的測試類靜默通過,"
    echo " 因此本檢查先確認檔案存在。若該 phase 尚未實作,這一項本來就應該是 FAIL。)"
    return 1
  fi
  ${MVN} test -Ptest-all -Dsurefire.failIfNoSpecifiedTests=false -Dtest="${classes}"
}
MVNT="mvn_test "

# ---------------------------------------------------------------------------
# 複合檢查函式
# ---------------------------------------------------------------------------

dod_compose_config_all() { # M1-11
  local e
  for e in mvp dev staging prod; do
    docker compose --env-file "environment/.env.${e}.example" \
      -f environment/docker-compose.yml config -q || { echo "config 失敗:${e}"; return 1; }
  done
}

dod_prod_no_source_mount() { # M1-12
  if docker compose --env-file environment/.env.prod.example \
      -f environment/docker-compose.yml config | grep -qE '\.\./(backend|frontend)'; then
    echo "prod 設定含原始碼掛載"; return 1
  fi
}

dod_prod_no_jdwp() { # M1-13
  if docker compose --env-file environment/.env.prod.example \
      -f environment/docker-compose.yml config | grep -qi jdwp; then
    echo "prod 設定含 JDWP debug agent"; return 1
  fi
}

dod_up_mvp_three_services() { # M1-14
  # gate 前段(M1-01 等)在 host 重建與 dev 容器共享的 target/classes,可能使容器內
  # DevTools 撞上半寫入 classpath 而 app 死亡(容器仍 Up);先自我修復再驗 up.sh
  dod_ensure_backend_up || true
  ./environment/scripts/up.sh mvp || return 1
  local n
  n="$(docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml \
        ps --services | wc -l | tr -d ' ')"
  [ "$n" -eq 3 ] || { echo "預期 3 個服務,實際 ${n} 個"; return 1; }
}

dod_mvp_containers_healthy() { # M1-15
  command -v jq >/dev/null 2>&1 || { echo "本項需要 jq"; return 1; }
  docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml \
    ps --format json \
    | jq -es 'length > 0 and all(.Health == "healthy" or .Health == "")' >/dev/null
}

dod_frontend_hmr() { # M1-36:寫入標記字串、輪詢 Vite dev server 內容
  local file="frontend/src/App.tsx" marker found i
  [ -f "$file" ] || { echo "找不到 ${file}"; return 1; }
  marker="DOD_HMR_MARKER_$(date +%s)"
  cp "$file" "${file}.dod-bak"
  printf '\nexport const dodHmrMarker = "%s";\n' "$marker" >> "$file"
  found=1
  for i in 1 2 3 4 5; do
    sleep 1
    if curl -fsS "http://127.0.0.1:5173/src/App.tsx" 2>/dev/null | grep -q "$marker"; then
      found=0; break
    fi
  done
  mv "${file}.dod-bak" "$file"
  [ "$found" -eq 0 ] || echo "5 秒內未在 dev server 觀察到標記(HMR 失效?)"
  return "$found"
}

# M1-33 前的自我修復:gate 的 M1-16~32 在 host 反覆 mvnw 重建「與 dev 容器共享」的
# target/classes,容器內 DevTools 可能撞上半寫入的 classpath 而重啟失敗、app 死亡但容器仍
# 顯示 Up(2026-08-26 Phase 12 實測)。stack 檢查前先確保 backend 活著,死了就 restart。
dod_ensure_backend_up() {
  local i
  if curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -E '"UP"' >/dev/null; then
    return 0
  fi
  # 容器不存在(全新環境)→ 交給 up.sh 正常啟動,不在這裡空等
  [ -n "$(docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml \
        ps -q backend 2>/dev/null)" ] || return 0
  echo "backend 無回應(gate 的 host 端重建干擾 dev 容器 classpath),restart 後重試……"
  docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml \
    restart backend >/dev/null 2>&1
  for i in $(seq 1 30); do
    if curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -E '"UP"' >/dev/null; then
      return 0
    fi
    sleep 5
  done
  echo "restart 後 150 秒內仍未恢復"
  return 1
}

dod_backend_reload() { # M1-37:修改 Java 檔 → reload.sh → 10 秒內重啟生效
  local f t0 rc i
  f="$(find backend/ctip-app/src/main/java -name '*.java' 2>/dev/null | head -n 1)"
  [ -n "$f" ] || { echo "ctip-app 無 Java 原始碼"; return 1; }
  cp "$f" "${f}.dod-bak"
  printf '\n// dod reload probe %s\n' "$(date +%s)" >> "$f"
  t0="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  rc=1
  if ./environment/scripts/reload.sh backend mvp; then
    for i in 1 2 3 4 5 6 7 8 9 10; do
      # 不用 grep -q:_common.sh 的 pipefail 下,-q 提早退出使 docker logs 吃 SIGPIPE 回非零,
      # 命中也被判失敗(2026-08-26 Phase 12 實測;logs 串流大、必觸發)
      if docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml \
          logs --since "$t0" backend 2>/dev/null | grep -E 'restartedMain|Started .+ in .+ seconds' >/dev/null; then
        rc=0; break
      fi
      sleep 1
    done
  fi
  mv "${f}.dod-bak" "$f"
  ./environment/scripts/reload.sh backend mvp >/dev/null 2>&1
  [ "$rc" -eq 0 ] || echo "10 秒內未觀察到 DevTools 重啟"
  return "$rc"
}

dod_coverage_threshold() { # M1-02:對 M1-01 產生的覆蓋率資料執行同一組 JaCoCo 規則
  # 15 §15.1 的指令欄寫「同上(JaCoCo check 綁在 verify)」——語意是**一次執行同時證明兩件事**,
  # 而不是把同一個完整建置再跑一次。原本照字面重跑,每個 mvp gate 因此多花約 7 分鐘,
  # 而 M3-01 底下 mvp 會跑兩次(M2-01 是巢狀回歸),整輪多約 14 分鐘,卻沒有多驗到任何東西。
  #
  # `jacoco:check@check` 綁到 pom 裡那個 `check` execution,用的是**完全相同的 rules**
  # (parent: BUNDLE LINE >= ${ctip.coverage.line-minimum};ctip-core override: PACKAGE
  # com.ctip.domain.* >= 0.85,即本項要驗的 domain 門檻),不是 plugin 預設值。
  #
  # 假綠守衛:`jacoco.exec` 不存在時 jacoco:check 會直接 skip 並**通過**。
  # 本項因此先確認覆蓋率資料存在——沒有資料就是「這一項沒被驗到」,必須是 FAIL。
  local missing="" m
  for m in ctip-sdk ctip-core ctip-adapters ctip-app; do
    [ -f "backend/${m}/target/jacoco.exec" ] || missing="${missing} ${m}"
  done
  if [ -n "$missing" ]; then
    echo "找不到覆蓋率資料(jacoco.exec):${missing}"
    echo "(本項驗的是 M1-01 那次 verify 產生的覆蓋率;單獨執行本項前請先跑 M1-01,"
    echo " 或 ${MVN} verify -Ptest-integration。jacoco:check 在沒有資料時會靜默通過,故此處先擋。)"
    return 1
  fi
  ${MVN} jacoco:check@check
}

dod_readme_quickstart() { # M1-38:擷取 README 的 bash 區塊並執行
  awk '/^```bash/{f=1;next} /^```/{f=0} f' README.md > "${WORKDIR}/readme-steps.sh"
  [ -s "${WORKDIR}/readme-steps.sh" ] || { echo "README.md 無 bash 區塊"; return 1; }
  bash -e "${WORKDIR}/readme-steps.sh"
}

dod_up_staging_no_mount() { # M2-25
  ./environment/scripts/up.sh staging || return 1
  if docker compose --env-file environment/.env.staging -f environment/docker-compose.yml \
      config | grep -qE '\.\./(backend|frontend)'; then
    echo "staging 設定含原始碼掛載"; return 1
  fi
}

dod_grafana_provisioning() { # M3-13:驗證 provisioning JSON 有效
  command -v jq >/dev/null 2>&1 || { echo "本項需要 jq"; return 1; }
  local files f
  files="$(find environment/config/monitoring/grafana -type f -name '*.json' 2>/dev/null)"
  [ -n "$files" ] || { echo "無 Grafana provisioning JSON"; return 1; }
  for f in $files; do
    jq empty "$f" || { echo "無效 JSON:${f}"; return 1; }
  done
}

dod_prod_config_guard() { # M3-17:prod 設定驗證(對真實 .env.prod)
  local f=environment/.env.prod v
  [ -f "$f" ] || { echo "找不到 ${f}(本項驗證真實 prod 設定)"; return 1; }
  docker compose --env-file "$f" -f environment/docker-compose.yml config \
    | grep -qE '\.\./(backend|frontend)' && { echo "含原始碼掛載"; return 1; }
  docker compose --env-file "$f" -f environment/docker-compose.yml config \
    | grep -qi jdwp && { echo "含 JDWP"; return 1; }
  v="$(grep -E '^JWT_SECRET=' "$f" | tail -n 1 | cut -d= -f2-)"
  case "$v" in ""|*CHANGE_ME*) echo "JWT_SECRET 仍是樣板值"; return 1 ;; esac
  [ "$(printf '%s' "$v" | LC_ALL=C wc -c)" -ge 32 ] || { echo "JWT_SECRET < 32 bytes"; return 1; }
  v="$(grep -E '^CORS_ALLOWED_ORIGINS=' "$f" | tail -n 1 | cut -d= -f2-)"
  case "$v" in ""|*'*'*) echo "CORS_ALLOWED_ORIGINS 為空或含 *"; return 1 ;; esac
  v="$(grep -E '^SWAGGER_ENABLED=' "$f" | tail -n 1 | cut -d= -f2-)"
  [ "$v" = "false" ] || { echo "SWAGGER_ENABLED 必須為 false"; return 1; }
}

# M3-19 的三段:11 支 workflow 皆存在 → deploy-prod 綁 protected environment → HEAD 的 CI 全綠。
# 只有 push 會觸發的九支列入「全綠」的必檢集合(見 dod_ci_all_green 的註)。
DOD_PUSH_WORKFLOWS='backend-test backend-lint frontend-test build compose-validate
                    openapi-check docker-build security deploy-staging'

dod_ci_green() { # M3-19
  # 檔案存在性先於 run 結論:只看 run 結論時,「只有兩支 workflow 且都綠」
  # 也會通過——13 §13.8 的六支 M1/M2 workflow 逾期到 Phase 23 才被發現就是這樣來的
  # (ADR 0016 Z3、ADR 0022)。清單即 13 §13.8 的 11 支。
  local w rc=0
  for w in backend-test backend-lint frontend-test build compose-validate openapi-check \
           docker-build security heavy-test deploy-staging deploy-prod; do
    [ -s ".github/workflows/${w}.yml" ] || { echo "缺少 workflow:.github/workflows/${w}.yml"; rc=1; }
  done
  [ "$rc" -eq 0 ] || return 1
  # deploy-prod 必須綁 protected environment(phase-23「不得做的事」);
  # required reviewers 存在 GitHub repo 設定,不是版控檔案能表達的部分,列於 15.5 人工確認
  grep -qE '^ +name: production$' .github/workflows/deploy-prod.yml \
    || { echo "deploy-prod.yml 未綁定 production environment"; return 1; }
  command -v gh >/dev/null 2>&1 || { echo "本項需要 gh CLI 並已推上 GitHub 跑過 CI(15 §15.3 註)"; return 1; }
  dod_ci_all_green
}

# 「CI 全綠」= HEAD 這個 commit 上,每一支 push 觸發的 workflow 都 completed + success。
#
# 原本是 `gh run list --limit 1`,只看**最近一次 run**。但九支 workflow 在同一次 push
# 同時觸發,「最近一次」是它們之中的哪一支基本上是任意的:實測抽到 build(success),
# 而同一個 commit 上 security 與 backend-test 都是 failure,該項照樣 PASS
# ——與 ADR 0022「只有兩支且都綠也會通過」是同一個形狀,只是換到 run 結論這一半(ADR 0048)。
#
# heavy-test(schedule + dispatch)與 deploy-prod(只有 dispatch)對 HEAD 不會有 run,
# 列入必檢會永遠 FAIL,故不在集合內——它們的存在性已由上面的檔案檢查涵蓋。
dod_ci_all_green() {
  local sha w result status conclusion rc=0
  sha="$(git rev-parse HEAD 2>/dev/null)" || { echo "取不到 HEAD"; return 1; }
  for w in ${DOD_PUSH_WORKFLOWS}; do
    result="$(gh run list --workflow="${w}.yml" --commit "${sha}" --limit 1 --json status,conclusion \
              -q 'if length == 0 then "none -" else "\(.[0].status) \(.[0].conclusion // "-")" end' 2>&1)" \
      || { echo "${w}:查詢失敗——${result}"; rc=1; continue; }
    status="${result%% *}"; conclusion="${result##* }"
    case "$status" in
      none) echo "${w}:HEAD(${sha:0:7})沒有對應的 run——尚未推送?"; rc=1 ;;
      completed) [ "$conclusion" = success ] || { echo "${w}:${conclusion}"; rc=1; } ;;
      *) echo "${w}:${status}(尚未結束)"; rc=1 ;;
    esac
  done
  [ "$rc" -eq 0 ] || echo "CI 未全綠(HEAD=${sha:0:7});M3-19 要求九支 push 觸發的 workflow 全部 success"
  return "$rc"
}

dod_sbom_present() { # M3-20
  find backend -path '*/target/bom.json' 2>/dev/null | grep -q . \
    || { echo "backend CycloneDX bom.json 不存在"; return 1; }
  [ -f frontend/sbom.json ] || { echo "frontend/sbom.json 不存在"; return 1; }
}

dod_docs_present() { # M3-23:12 份必要文件皆存在且非空
  local f rc=0
  for f in README.md SECURITY.md CONTRIBUTING.md LICENSE \
           docs/architecture/overview.md docs/architecture/security.md \
           docs/deployment/licensing.md docs/deployment/privacy.md \
           docs/development/getting-started.md docs/development/plugin-sdk.md \
           docs/development/version-audit.md docs/api/openapi.json; do
    [ -s "$f" ] || { echo "缺少或為空:${f}"; rc=1; }
  done
  return "$rc"
}

dod_spec_xrefs() { # M3-24:docs/spec/** 相對連結與 anchor 皆指向存在的目標
  command -v python3 >/dev/null 2>&1 || { echo "本項需要 python3"; return 1; }
  python3 - <<'PYEOF'
import pathlib, re, sys

root = pathlib.Path("docs/spec")
link_re = re.compile(r'\]\(([^)\s]+)\)')
errors = []

def slugs(md: pathlib.Path):
    out = set()
    in_code = False
    content = md.read_text(encoding="utf-8")
    # 明確的 HTML anchor(<a id="...">)也算有效目標
    out.update(re.findall(r'<a\s+id="([^"]+)"', content))
    for line in content.splitlines():
        if line.strip().startswith("```"):
            in_code = not in_code
            continue
        if in_code or not line.startswith("#"):
            continue
        text = re.sub(r"<[^>]+>", "", line.lstrip("#")).strip().replace("`", "")
        s = "".join(c for c in text.lower() if c.isalnum() or c in " -")
        out.add(s.replace(" ", "-"))
    return out

for md in sorted(root.rglob("*.md")):
    for target in link_re.findall(md.read_text(encoding="utf-8")):
        if target.startswith(("http://", "https://", "mailto:")):
            continue
        path_part, _, anchor = target.partition("#")
        dest = md if not path_part else (md.parent / path_part).resolve()
        if path_part and not dest.is_file():
            errors.append(f"{md}: 連結目標不存在: {target}")
            continue
        if anchor and dest.suffix == ".md" and anchor not in slugs(dest):
            errors.append(f"{md}: anchor 不存在: {target}")

for e in errors:
    print(e)
sys.exit(1 if errors else 0)
PYEOF
}

# ---------------------------------------------------------------------------
# Gate 定義(逐項對應 docs/spec/15-dod-gates.md §15.1–15.3)
# ---------------------------------------------------------------------------

gate_mvp() {
  check M1-01 "四個 module 皆編譯,L1–L3 測試通過" "${MVN} verify -Ptest-integration"
  check M1-02 "覆蓋率門檻達標(domain >= 85%);對 M1-01 的覆蓋率資料執行同一組規則" dod_coverage_threshold
  check M1-03 "ArchUnit 規則全數通過" "${MVNT}ArchitectureTest"
  check M1-04 "Spotless 格式一致" "${MVN} spotless:check"
  check M1-05 "Checkstyle 五條可讀性規則通過" "${MVN} checkstyle:check"
  check M1-06 "前端 type check 通過" "cd frontend && npx tsc --noEmit"
  check M1-07 "前端 lint 通過(含 feature 依賴規則)" "cd frontend && npx eslint . --max-warnings 0"
  check M1-08 "前端 build 通過" "cd frontend && npm run build"
  check M1-09 "前端測試通過且覆蓋率達標" "cd frontend && npm run test -- --coverage"
  check M1-10 "前端型別與 OpenAPI 一致(非手寫)" "cd frontend && npm run api:check"
  check M1-11 "四種 env 的 compose config 皆有效" dod_compose_config_all
  check M1-12 "prod 設定不含原始碼掛載" dod_prod_no_source_mount
  check M1-13 "prod 設定不含 JDWP debug agent" dod_prod_no_jdwp
  check M1-14 "up.sh mvp 成功,且只有 frontend/backend/postgres 三個容器" dod_up_mvp_three_services
  check M1-15 "三個容器皆 healthy" dod_mvp_containers_healthy
  check M1-16 "Flyway 從空資料庫執行至最新版本成功" "${MVNT}MigrationIntegrationTest"
  check M1-17 "public system tenant 存在且不可刪除" "${MVNT}PublicTenantIntegrationTest"
  check M1-18 "樣本資料寫入成功(>=1000 IOC,涵蓋所有型別與四種 TLP)" "${MVNT}SampleDataIntegrationTest"
  check M1-19 "資料庫中無 TLP:RED 資料" "${MVNT}TlpRedAbsenceTest"
  check M1-20 "所有必要索引存在" "${MVNT}RequiredIndexTest"
  check M1-21 "MockOpenPhishAdapter 端對端:抓取→驗證→正規化→去重→合併→落庫" "${MVNT}IngestionEndToEndTest"
  check M1-22 "髒資料被拒絕並記入 ingestion_rejections,八種 reason 皆覆蓋" "${MVNT}RejectionRuleTest"
  check M1-23 "多來源重疊 IOC 依 IndicatorMergePolicy 正確合併" "${MVNT}IndicatorMergePolicyTest"
  check M1-24 "三步 valid_until 計算正確(含來源未明示與 FILE_HASH 分支)" "${MVNT}ValidityPeriodTest"
  check M1-25 "正規化規則七種型別全數正確" "${MVNT}NormalizationTest"
  check M1-26 "GET /api/v1/iocs 回傳正確,cursor 分頁可連續翻至最後一頁" "${MVNT}CursorPaginationIntegrationTest"
  check M1-27 "POST /api/v1/iocs/search 篩選正確" "${MVNT}IocSearchIntegrationTest"
  check M1-28 "安全測試 1、3、7、9 通過(匿名 TLP、跨租戶 404、限流 429、再散布作用域)" "${MVNT}SecurityTest"
  check M1-29 "STIX 匯出以 STIX 2.1 JSON Schema 驗證通過" "${MVNT}StixSchemaValidationTest"
  check M1-30 "五個 TLP marking UUID 與 OASIS 定義完全相符" "${MVNT}StixTlpMarkingsTest"
  check M1-31 "六種 IocType 的 pattern 模板與四種 hash 演算法對應正確" "${MVNT}StixPatternTest"
  check M1-32 "錯誤回應符合統一結構,含 traceId" "${MVNT}ErrorResponseTest"
  check M1-33 "Swagger UI 可開啟" "dod_ensure_backend_up && curl -fsS http://localhost:8080/swagger-ui/index.html >/dev/null"
  check M1-34 "所有端點皆有 summary、response schema 與至少一個範例" "${MVNT}OpenApiCompletenessTest"
  check M1-35 "前端 IOC 搜尋頁能查到後端資料,四種狀態皆呈現" "cd frontend && npm run test -- IocSearchPage"
  check M1-36 "前端 HMR 生效(修改 tsx 後 5 秒內更新)" dod_frontend_hmr
  check M1-37 "後端 reload 生效(reload.sh 後 10 秒內新行為生效)" dod_backend_reload
  check M1-38 "README 的啟動步驟可直接複製執行" dod_readme_quickstart
}

gate_phase2() {
  check M2-01 "DoD-MVP 全部仍通過(回歸)" "./environment/scripts/dod.sh mvp"
  check M2-02 "註冊/登入/refresh/登出全流程" "${MVNT}AuthFlowIntegrationTest"
  check M2-03 "Refresh token 輪替與重用偵測(重用觸發 family 全撤)" "${MVNT}RefreshTokenRotationTest"
  check M2-04 "五種角色的權限矩陣正確,@PreAuthorize 生效" "${MVNT}RbacMatrixTest"
  check M2-05 "API Key 建立(僅回傳一次)、撤銷、scope 檢查、不可提權" "${MVNT}ApiKeyTest"
  check M2-06 "跨租戶測試:每一個 tenant-scoped 端點皆回 404" "${MVNT}CrossTenantIsolationTest"
  check M2-07 "安全測試 1–9 全數通過" "${MVNT}SecurityTest"
  check M2-08 "方案配額生效,超限回 429 且帶 X-RateLimit-*" "${MVNT}QuotaEnforcementTest"
  check M2-09 "Redis 限流在兩個 app 實例下正確" "${MVNT}DistributedRateLimitTest"
  check M2-10 "Public bloom 與 tenant bloom 皆可生成" "${MVNT}BloomGenerationTest"
  check M2-11 "Bloom 位元序與雙雜湊索引符合 11.4 規格" "${MVNT}BloomBitLayoutTest"
  check M2-12 "Bloom checksum 驗證通過" "${MVNT}BloomGenerationTest"
  check M2-13 "TLP:GREEN 不進入 public bloom" "${MVNT}BloomCoverageTest"
  check M2-14 "Delta 生成與套用正確,resultingChecksum 相符" "${MVNT}BloomDeltaTest"
  check M2-15 "Delta 鏈超過上限時回 409 SNAPSHOT_REQUIRED" "${MVNT}SyncEndToEndTest"
  check M2-16 "完整同步流程端對端(manifest → delta → 套用 → 更新版本)" "${MVNT}SyncEndToEndTest"
  check M2-17 "manifest 含 coverage 與 notCovered 欄位" "${MVNT}SyncEndToEndTest"
  check M2-18 "手動提交 IOC 走完整 pipeline,預設 TLP:AMBER" "${MVNT}ManualSubmissionTest"
  check M2-19 "匯入超出方案上限回 413" "${MVNT}ManualSubmissionTest"
  check M2-20 "誤判回報後 status 由合併規則決定(非呼叫端指定)" "${MVNT}FalsePositiveReportTest"
  check M2-21 "Threat 實體與 threat_indicators、threat_external_references 可用" "${MVNT}ThreatIntegrationTest"
  check M2-22 "Elasticsearch 索引建立、搜尋正確" "${MVNT}ElasticsearchSearchTest"
  check M2-23 "ES 掛掉時 API 降級為 PostgreSQL(200 + X-Search-Backend: postgres)" "${MVNT}SearchFallbackTest"
  check M2-24 "Reconciliation 能偵測並修正 DB 與 ES 差異" "${MVNT}SearchReconciliationTest"
  check M2-25 "up.sh staging 成功且未掛載原始碼" dod_up_staging_no_mount
  check M2-26 "Playwright E2E:匿名搜尋、登入、建立 API key、提交 IOC" "cd frontend && npx playwright test"
  check M2-27 "L1–L3 全通過,覆蓋率門檻達標" "${MVN} verify -Ptest-integration"
}

gate_full() {
  check M3-01 "DoD-MVP 與 DoD-Phase2 全部仍通過" "./environment/scripts/dod.sh mvp && ./environment/scripts/dod.sh phase2"
  check M3-02 "Kafka(KRaft)啟動,事件正確發佈與消費" "${MVN} verify -Ptest-all -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KafkaEventTest"
  check M3-03 "消費端冪等(重複 eventId 不產生重複副作用)" "${MVNT}EventIdempotencyTest"
  check M3-04 "Kafka 不可用時業務操作不失敗" "${MVNT}KafkaUnavailableTest"
  check M3-05 "WebSocket 通知端對端,斷線自動重連" "cd frontend && npx playwright test websocket"
  check M3-06 "Webhook 送達、HMAC 簽章正確(含 timestamp 防重放)" "${MVNT}WebhookDeliveryTest"
  check M3-07 "Webhook 失敗重試與連續 5 次後停用" "${MVNT}WebhookDeliveryTest"
  check M3-08 "訂閱過濾在伺服器端執行" "${MVNT}WebhookFilterTest"
  check M3-09 "Audit log append-only:應用角色的 UPDATE/DELETE 被 DB 拒絕" "${MVNT}AuditAppendOnlyTest"
  check M3-10 "稽核寫入失敗不影響主要業務操作" "${MVNT}AuditFailureIsolationTest"
  check M3-11 "六項資料保留任務正確清理" "${MVNT}RetentionTaskTest"
  check M3-11b "26 種稽核行為皆有實際寫入路徑(無永不可達行為)" "${MVNT}AuditCompletenessTest"
  check M3-12 "Prometheus 指標齊全(含每個 ingestion stage 的耗時)" "${MVNT}MetricsCompletenessTest"
  check M3-13 "Grafana dashboard 可載入(provisioning JSON 有效)" dod_grafana_provisioning
  check M3-14 "OpenTelemetry trace 從 API 串到 DB / Kafka / ES" "${MVNT}TracePropagationTest"
  check M3-15 "日誌不含敏感欄位" "${MVNT}SensitiveLogTest"
  check M3-16 "traceId 同時出現在錯誤回應與日誌" "${MVNT}TracePropagationTest"
  check M3-17 "prod 設定驗證:不掛原始碼、無明文 secret、CORS 非 *、Swagger 關閉" dod_prod_config_guard
  check M3-18 "prod 啟動守衛生效(樣板 JWT_SECRET 與 CORS=* 皆拒絕啟動)" "${MVNT}StartupValidatorTest"
  check M3-19 "11 支 workflow 皆存在且 HEAD 的 CI 全綠(九支 push 觸發者)" dod_ci_green
  check M3-20 "SBOM 產出(backend CycloneDX + frontend npm sbom)" dod_sbom_present
  check M3-21 "ctip-sdk 可獨立打包" "${MVN} -pl ctip-sdk package"
  check M3-22 "ExampleThreatSourceAdapter 可編譯並通過測試" "${MVN} -pl ctip-sdk test -Ptest-all -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ExampleAdapterTest"
  check M3-23 "文件齊備(12 份必要文件皆存在且非空)" dod_docs_present
  check M3-24 "規格內部交叉引用皆指向存在的目標" dod_spec_xrefs
}

# ---------------------------------------------------------------------------
# 執行
# ---------------------------------------------------------------------------
info "=== DoD Gate:${GATE} ==="
case "$GATE" in
  mvp)    gate_mvp ;;
  phase2) gate_phase2 ;;
  full)   gate_full ;;
esac

info ""
# 帶 gate 名稱:M2-01 會巢狀印出自己的結果行,不標名的話用 log 內容判斷完成必然誤判
# ——外層還在跑就以為結束、於是啟動第二輪(§15.0)。判斷完成請一律用退出碼。
info "=== 結果(${GATE}):${PASS_COUNT}/${TOTAL} 通過 ==="
if [ -n "$FAILED_IDS" ]; then
  printf '%s失敗項目:%s%s\n' "${C_RED}" "${FAILED_IDS}" "${C_RESET}"
fi

info ""
info "=== 需人工確認(以下項目未被自動驗證,見 15-dod-gates.md §15.5)==="
info "  P-01 聚合圖(03-diagrams.md §3.2)與實際 domain 類別的方法一致"
info "  P-02 Ubiquitous Language 詞彙表被遵守(可自動化部分已列為 ArchUnit 擴充)"
info "  P-03 程式碼「人類易讀」"
info "  P-04 Grafana dashboard 的圖表確實有意義"
info "  P-05 docs/architecture/decisions/ 的 ADR 內容正確"
info "  P-06 版本表的「推估」支援終止日(需上網查證,見 06-tech-stack.md §6.4)"
info "  P-07 deploy-prod 的 production environment 已設定 required reviewers(GitHub repo 設定,非版控檔案)"

[ -z "$FAILED_IDS" ]
