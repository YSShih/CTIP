#!/usr/bin/env bash
# DoD Gate 的複合檢查函式(docs/spec/15-dod-gates.md)。由 dod.sh source,不直接執行。
#
# 本檔的內容自 2026-08-31 的拆檔(ADR 0052)之前就在 dod.sh 裡,除下列三處外原樣搬移:
#   - dod_readme_quickstart 的暫存檔改用 run 目錄
#   - dod_ci_all_green 的九支 workflow 查詢改為並行
#   - 新增 dod_frontend_tests / dod_playwright_run(產生供 M1-35 / M3-05 斷言的 JSON 報告)

dod_compose_config_all() { # M1-11
    local e
    for e in mvp dev staging prod; do
        docker compose --env-file "environment/.env.${e}.example" \
            -f environment/docker-compose.yml config -q || {
            echo "config 失敗:${e}"
            return 1
        }
    done
}

dod_prod_no_source_mount() { # M1-12
    if docker compose --env-file environment/.env.prod.example \
        -f environment/docker-compose.yml config | grep -qE '\.\./(backend|frontend)'; then
        echo "prod 設定含原始碼掛載"
        return 1
    fi
}

dod_prod_no_jdwp() { # M1-13
    if docker compose --env-file environment/.env.prod.example \
        -f environment/docker-compose.yml config | grep -qi jdwp; then
        echo "prod 設定含 JDWP debug agent"
        return 1
    fi
}

dod_up_mvp_three_services() { # M1-14
    # gate 前段(M1-01 等)在 host 重建與 dev 容器共享的 target/classes,可能使容器內
    # DevTools 撞上半寫入 classpath 而 app 死亡(容器仍 Up);先自我修復再驗 up.sh。
    #
    # 註:排程改為資源感知之後,build 與 stack 由 maven 資源鎖互斥,容器起來之後不再有
    # host 端重建,本自我修復退居安全網(ADR 0052)。
    dod_ensure_backend_up || true
    ./environment/scripts/up.sh mvp || return 1
    local n
    n="$(docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml \
        ps --services | wc -l | tr -d ' ')"
    [ "$n" -eq 3 ] || {
        echo "預期 3 個服務,實際 ${n} 個"
        return 1
    }
}

dod_mvp_containers_healthy() { # M1-15
    command -v jq >/dev/null 2>&1 || {
        echo "本項需要 jq"
        return 1
    }
    docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml \
        ps --format json |
        jq -es 'length > 0 and all(.Health == "healthy" or .Health == "")' >/dev/null
}

dod_frontend_hmr() { # M1-36:寫入標記字串、輪詢 Vite dev server 內容
    local file="frontend/src/App.tsx" marker found i
    [ -f "$file" ] || {
        echo "找不到 ${file}"
        return 1
    }
    marker="DOD_HMR_MARKER_$(date +%s)"
    cp "$file" "${file}.dod-bak"
    printf '\nexport const dodHmrMarker = "%s";\n' "$marker" >>"$file"
    found=1
    for i in 1 2 3 4 5; do
        sleep 1
        if curl -fsS "http://127.0.0.1:5173/src/App.tsx" 2>/dev/null | grep -q "$marker"; then
            found=0
            break
        fi
    done
    mv "${file}.dod-bak" "$file"
    [ "$found" -eq 0 ] || echo "5 秒內未在 dev server 觀察到標記(HMR 失效?)"
    return "$found"
}

# M1-33 前的自我修復:見 dod_up_mvp_three_services 的註。
dod_ensure_backend_up() {
    local i
    if curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -E '"UP"' >/dev/null; then
        return 0
    fi
    # 容器不存在(全新環境)→ 交給 up.sh 正常啟動,不在這裡空等
    [ -n "$(docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml \
        ps -q backend 2>/dev/null)" ] || return 0
    echo "backend 無回應(host 端重建干擾 dev 容器 classpath),restart 後重試……"
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
    [ -n "$f" ] || {
        echo "ctip-app 無 Java 原始碼"
        return 1
    }
    cp "$f" "${f}.dod-bak"
    printf '\n// dod reload probe %s\n' "$(date +%s)" >>"$f"
    t0="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    rc=1
    if ./environment/scripts/reload.sh backend mvp; then
        for i in 1 2 3 4 5 6 7 8 9 10; do
            # 不用 grep -q:_common.sh 的 pipefail 下,-q 提早退出使 docker logs 吃 SIGPIPE 回非零,
            # 命中也被判失敗(2026-08-26 Phase 12 實測;logs 串流大、必觸發)
            if docker compose --env-file environment/.env.mvp -f environment/docker-compose.yml \
                logs --since "$t0" backend 2>/dev/null | grep -E 'restartedMain|Started .+ in .+ seconds' >/dev/null; then
                rc=0
                break
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

# M1-09:前端測試 + 覆蓋率。另輸出 JSON 報告供 M1-35 斷言——原本 M1-35 是
# `npm run test -- IocSearchPage`,等於把本項已經跑過的測試再挑一個檔跑一次(ADR 0052)。
dod_frontend_tests() {
    (cd frontend && npm run test -- --coverage \
        --reporter=default --reporter=json \
        --outputFile.json="${DOD_RUN_DIR}/${DOD_VITEST_JSON_NAME}")
}

# M2-26:Playwright E2E。同理輸出 JSON 報告供 M3-05 斷言。
dod_playwright_run() {
    (cd frontend && PLAYWRIGHT_JSON_OUTPUT_NAME="${DOD_RUN_DIR}/${DOD_PLAYWRIGHT_JSON_NAME}" \
        npx playwright test --reporter=list,json)
}

dod_readme_quickstart() { # M1-38:擷取 README 的 bash 區塊並執行
    awk '/^```bash/{f=1;next} /^```/{f=0} f' README.md >"${DOD_RUN_DIR}/readme-steps.sh"
    [ -s "${DOD_RUN_DIR}/readme-steps.sh" ] || {
        echo "README.md 無 bash 區塊"
        return 1
    }
    bash -e "${DOD_RUN_DIR}/readme-steps.sh"
}

dod_up_staging_no_mount() { # M2-25
    ./environment/scripts/up.sh staging || return 1
    if docker compose --env-file environment/.env.staging -f environment/docker-compose.yml \
        config | grep -qE '\.\./(backend|frontend)'; then
        echo "staging 設定含原始碼掛載"
        return 1
    fi
}

dod_grafana_provisioning() { # M3-13:驗證 provisioning JSON 有效
    command -v jq >/dev/null 2>&1 || {
        echo "本項需要 jq"
        return 1
    }
    local files f
    files="$(find environment/config/monitoring/grafana -type f -name '*.json' 2>/dev/null)"
    [ -n "$files" ] || {
        echo "無 Grafana provisioning JSON"
        return 1
    }
    for f in $files; do
        jq empty "$f" || {
            echo "無效 JSON:${f}"
            return 1
        }
    done
}

dod_prod_config_guard() { # M3-17:prod 設定驗證(對真實 .env.prod)
    local f=environment/.env.prod v
    [ -f "$f" ] || {
        echo "找不到 ${f}(本項驗證真實 prod 設定)"
        return 1
    }
    docker compose --env-file "$f" -f environment/docker-compose.yml config |
        grep -qE '\.\./(backend|frontend)' && {
        echo "含原始碼掛載"
        return 1
    }
    docker compose --env-file "$f" -f environment/docker-compose.yml config |
        grep -qi jdwp && {
        echo "含 JDWP"
        return 1
    }
    v="$(grep -E '^JWT_SECRET=' "$f" | tail -n 1 | cut -d= -f2-)"
    case "$v" in "" | *CHANGE_ME*)
        echo "JWT_SECRET 仍是樣板值"
        return 1
        ;;
    esac
    [ "$(printf '%s' "$v" | LC_ALL=C wc -c)" -ge 32 ] || {
        echo "JWT_SECRET < 32 bytes"
        return 1
    }
    v="$(grep -E '^CORS_ALLOWED_ORIGINS=' "$f" | tail -n 1 | cut -d= -f2-)"
    case "$v" in "" | *'*'*)
        echo "CORS_ALLOWED_ORIGINS 為空或含 *"
        return 1
        ;;
    esac
    v="$(grep -E '^SWAGGER_ENABLED=' "$f" | tail -n 1 | cut -d= -f2-)"
    [ "$v" = "false" ] || {
        echo "SWAGGER_ENABLED 必須為 false"
        return 1
    }
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
        [ -s ".github/workflows/${w}.yml" ] || {
            echo "缺少 workflow:.github/workflows/${w}.yml"
            rc=1
        }
    done
    [ "$rc" -eq 0 ] || return 1
    # deploy-prod 必須綁 protected environment(phase-23「不得做的事」);
    # required reviewers 存在 GitHub repo 設定,不是版控檔案能表達的部分,列於 15.5 人工確認
    grep -qE '^ +name: production$' .github/workflows/deploy-prod.yml ||
        {
            echo "deploy-prod.yml 未綁定 production environment"
            return 1
        }
    command -v gh >/dev/null 2>&1 || {
        echo "本項需要 gh CLI 並已推上 GitHub 跑過 CI(15 §15.3 註)"
        return 1
    }
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
#
# 九支查詢改為並行(ADR 0052):純網路、彼此無關,序列跑只是把九次往返加起來。
dod_ci_all_green() {
    local sha w result status conclusion rc=0 tmp
    sha="$(git rev-parse HEAD 2>/dev/null)" || {
        echo "取不到 HEAD"
        return 1
    }
    tmp="$(mktemp -d)"
    for w in ${DOD_PUSH_WORKFLOWS}; do
        (gh run list --workflow="${w}.yml" --commit "${sha}" --limit 1 --json status,conclusion \
            -q 'if length == 0 then "none -" else "\(.[0].status) \(.[0].conclusion // "-")" end' \
            >"${tmp}/${w}" 2>&1 || printf 'ERROR %s' "$(cat "${tmp}/${w}" 2>/dev/null)" >"${tmp}/${w}") &
    done
    wait
    for w in ${DOD_PUSH_WORKFLOWS}; do
        result="$(cat "${tmp}/${w}" 2>/dev/null)"
        status="${result%% *}"
        conclusion="${result##* }"
        case "$status" in
            ERROR | "")
                echo "${w}:查詢失敗——${result}"
                rc=1
                ;;
            none)
                echo "${w}:HEAD(${sha:0:7})沒有對應的 run——尚未推送?"
                rc=1
                ;;
            completed) [ "$conclusion" = success ] || {
                echo "${w}:${conclusion}"
                rc=1
            } ;;
            *)
                echo "${w}:${status}(尚未結束)"
                rc=1
                ;;
        esac
    done
    rm -rf "$tmp"
    [ "$rc" -eq 0 ] || echo "CI 未全綠(HEAD=${sha:0:7});M3-19 要求九支 push 觸發的 workflow 全部 success"
    return "$rc"
}

dod_sbom_present() { # M3-20
    find backend -path '*/target/bom.json' 2>/dev/null | grep -q . ||
        {
            echo "backend CycloneDX bom.json 不存在"
            echo "(bom.json 由 cyclonedx 綁在 package,隨 M1-01 的 verify 產生。"
            echo " 注意 makeAggregateBom 在**離線模式**會靜默 skip,故 dod.sh 的 Maven 呼叫不得加 -o。)"
            return 1
        }
    [ -f frontend/sbom.json ] || {
        echo "frontend/sbom.json 不存在"
        return 1
    }
}

dod_docs_present() { # M3-23:12 份必要文件皆存在且非空
    local f rc=0
    for f in README.md SECURITY.md CONTRIBUTING.md LICENSE \
        docs/architecture/overview.md docs/architecture/security.md \
        docs/deployment/licensing.md docs/deployment/privacy.md \
        docs/development/getting-started.md docs/development/plugin-sdk.md \
        docs/development/version-audit.md docs/api/openapi.json; do
        [ -s "$f" ] || {
            echo "缺少或為空:${f}"
            rc=1
        }
    done
    return "$rc"
}

dod_spec_xrefs() { # M3-24:docs/spec/** 相對連結與 anchor 皆指向存在的目標
    command -v python3 >/dev/null 2>&1 || {
        echo "本項需要 python3"
        return 1
    }
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
