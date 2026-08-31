#!/usr/bin/env bash
# 測試報告斷言(docs/spec/15-dod-gates.md §15.0)。由 dod.sh source,不直接執行。
#
# 為什麼是「讀報告」而不是「再跑一次」:M1-01 的 `verify -Ptest-integration` 已經把
# 1,145 個測試全部跑完,而 M1-16~M1-34 原本又用 `mvn test -Dtest=<類名>` 把其中 17 個類
# 逐一重跑,每次付一整輪 reactor + JVM + Testcontainers 啟動。三個 gate 合計約 64 次
# Maven 呼叫,真正需要獨立執行的只有 3 個 @Tag("heavy") 的類。與 ADR 0047 修 M1-02
# 是同一個形狀(ADR 0052)。
#
# ⚠️ 這麼做的風險是**假綠**:報告檔留在 target/ 裡,不驗新鮮度的話,一份幾天前的舊報告
# 會讓一個根本沒跑過的判準一路 PASS——與 ADR 0047 記的 `jacoco:check` 在沒有 jacoco.exec
# 時靜默通過完全同型。因此每一個報告斷言都要過四道守衛,缺一不可:
#
#   1. 測試類的**原始碼存在**       ——ADR 0017 的守衛,不得繞過
#   2. 報告檔存在
#   3. 報告**比基準新**            ——基準見 dod_report_stale
#   4. tests > 0 且 failures == 0 且 errors == 0
#
# 第 4 道另擋「全部 skipped」:整個類別被 @Disabled 時 surefire 仍記 tests=N、skipped=N,
# 只看 tests>0 與零失敗會讓它通過——那是沒被驗到,不是通過。

# ---------------------------------------------------------------------------
# 新鮮度基準
# ---------------------------------------------------------------------------
#
# 兩種情況:
#   a) 本輪跑過建置(M1-01 / _heavy)→ 基準是該次建置開始時寫下的 stamp。最強的判準:
#      報告必須是**這一輪**產生的。
#   b) 本輪沒跑建置(例如 `dod.sh mvp M1-21` 單獨執行)→ 退而求其次,拿**原始碼**當基準:
#      只要有任何一個 src 檔比報告新,就代表報告不反映現在的程式碼,判 FAIL。
#      這一項於是仍然可以單獨執行(維持既有用法),但不會對著過期的結果給分。

# dod_report_stale <報告檔> <stamp 種類:backend|frontend> <來源根目錄...>
#   → 報告過期則回 0(是,過期)
dod_report_stale() {
    local report="$1" floor="${DOD_RUN_DIR}/stamp-$2"
    shift 2
    local newer root
    if [ -f "$floor" ]; then
        # 報告必須不比 stamp 舊。`-nt` 只比較 mtime,同秒視為不新——建置動輒數分鐘,
        # 報告與 stamp 同秒代表建置根本沒開始寫報告,判過期是對的。
        [ "$report" -nt "$floor" ] && return 1
        return 0
    fi
    for root in "$@"; do
        [ -d "$root" ] || continue
        newer="$(find "$root" -type f -not -path '*/node_modules/*' -not -path '*/target/*' \
            -newer "$report" -print 2>/dev/null | head -n 1)"
        [ -n "$newer" ] && return 0
    done
    return 1
}

# 從 <testsuite …> 取單一屬性
_dod_xml_attr() { printf '%s' "$1" | sed -n "s/.*[ ]$2=\"\([^\"]*\)\".*/\1/p"; }

# ---------------------------------------------------------------------------
# surefire
# ---------------------------------------------------------------------------

# assert_test_report <測試類名>
assert_test_report() {
    local cls="$1" reports f attrs tests failures errors skipped rc=0

    # 守衛 1:原始碼存在(ADR 0017)。surefire 的 failIfNoSpecifiedTests=false 會讓不存在的
    # 測試類靜默通過;報告式判準同樣不能因為「沒有報告也沒有原始碼」就放過。
    if ! find "${REPO_ROOT}/backend" -path '*/src/test/java/*' -name "${cls}.java" \
        -not -path '*/target/*' 2>/dev/null | grep -q .; then
        echo "找不到測試類原始碼:${cls}.java"
        echo "(若該 phase 尚未實作,這一項本來就應該是 FAIL。)"
        return 1
    fi

    # 守衛 2:報告存在
    reports="$(find "${REPO_ROOT}/backend" -path '*/target/surefire-reports/*' \
        \( -name "TEST-*.${cls}.xml" -o -name "TEST-${cls}.xml" \) 2>/dev/null)"
    if [ -z "$reports" ]; then
        echo "找不到 ${cls} 的 surefire 報告。"
        echo "(本項驗的是 M1-01 那次 verify 產生的測試結果;單獨執行本項前請先跑 M1-01,"
        echo " 或 ${MVN} verify -Ptest-integration。)"
        return 1
    fi

    while IFS= read -r f; do
        [ -n "$f" ] || continue

        # 守衛 3:新鮮度
        if dod_report_stale "$f" backend "${REPO_ROOT}/backend"; then
            echo "報告已過期(不是本輪建置產生的,或原始碼比它新):${f#"${REPO_ROOT}/"}"
            echo "(報告檔留在 target/ 裡,不驗新鮮度就會拿舊結果給分——與 ADR 0047 的"
            echo " 「jacoco.exec 不存在時 jacoco:check 靜默通過」同型。)"
            rc=1
            continue
        fi

        # 守衛 4:內容
        attrs="$(grep -m 1 -o '<testsuite [^>]*>' "$f" 2>/dev/null)"
        if [ -z "$attrs" ]; then
            echo "報告格式無法解析:${f#"${REPO_ROOT}/"}"
            rc=1
            continue
        fi
        tests="$(_dod_xml_attr "$attrs" tests)"
        failures="$(_dod_xml_attr "$attrs" failures)"
        errors="$(_dod_xml_attr "$attrs" errors)"
        skipped="$(_dod_xml_attr "$attrs" skipped)"
        tests="${tests:-0}"
        failures="${failures:-0}"
        errors="${errors:-0}"
        skipped="${skipped:-0}"

        if [ "$tests" -eq 0 ]; then
            echo "${cls} 跑了 0 個測試——沒有被驗到,不是通過。"
            rc=1
        elif [ "$tests" -eq "$skipped" ]; then
            echo "${cls} 的 ${tests} 個測試**全部被 skip**——沒有被驗到,不是通過。"
            rc=1
        elif [ "$failures" -ne 0 ] || [ "$errors" -ne 0 ]; then
            echo "${cls}:${failures} failures / ${errors} errors(共 ${tests} 個測試)"
            # 印出**是哪幾個測試方法**掛掉與它們的訊息。
            # (原本用 `sed -n 's/.*<\(failure\|error\)…'`——BSD 的 BRE 不支援 `\|`,
            #  那一行從來沒印出過任何東西,失敗輸出等於空的。)
            awk '
                /<testcase / {
                    name = $0
                    # 先把 classname= 換掉再抓 name=:awk 沒有惰性量詞,
                    # 直接 sub(/.*name="/) 會貪婪地抓到 classname 的那一個。
                    gsub(/classname="/, "cn=\"", name)
                    sub(/.*name="/, "", name)
                    sub(/".*/, "", name)
                }
                /<(failure|error)[ >]/ {
                    msg = $0
                    if (msg ~ /message="/) {
                        sub(/.*message="/, "", msg)
                        sub(/".*/, "", msg)
                    } else {
                        msg = "(訊息見下方 stack trace)"
                    }
                    gsub(/&#10;/, " ", msg)
                    gsub(/&quot;/, "\"", msg)
                    gsub(/&lt;/, "<", msg)
                    gsub(/&gt;/, ">", msg)
                    gsub(/&amp;/, "\&", msg)
                    print "  - " name ": " msg
                }
            ' "$f" | head -n 10
            rc=1
        fi
    done <<EOF
$reports
EOF
    return "$rc"
}

# ---------------------------------------------------------------------------
# vitest / playwright
# ---------------------------------------------------------------------------

DOD_VITEST_JSON_NAME=vitest-results.json
DOD_PLAYWRIGHT_JSON_NAME=playwright-results.json

# assert_vitest_suite <檔名比對字串>
assert_vitest_suite() {
    local pat="$1" json="${DOD_RUN_DIR}/${DOD_VITEST_JSON_NAME}"
    command -v jq >/dev/null 2>&1 || {
        echo "本項需要 jq"
        return 1
    }
    if [ ! -f "$json" ]; then
        echo "找不到 vitest 報告(${json})。"
        echo "(本項驗的是 M1-09 那次 npm run test 的結果;請先跑 M1-09。)"
        return 1
    fi
    if dod_report_stale "$json" frontend "${REPO_ROOT}/frontend/src"; then
        echo "vitest 報告已過期(原始碼比它新)。"
        return 1
    fi
    jq -e --arg p "$pat" '
        [ .testResults[]? | select((.name // "") | contains($p)) ]
        | if length == 0 then false
          else all(.[]; (.status // "failed") == "passed")
               and ([ .[].assertionResults[]? ] | length) > 0
          end' "$json" >/dev/null && return 0

    echo "vitest 報告中 ${pat} 不存在或未全數通過:"
    jq -r --arg p "$pat" '
        [ .testResults[]? | select((.name // "") | contains($p)) ]
        | if length == 0 then "  (找不到符合 \($p) 的測試檔——沒有被驗到,不是通過)"
          else (.[] | "  \(.status) \(.name)") end' "$json" 2>/dev/null | head -n 10
    return 1
}

# assert_playwright_suite <檔名/標題比對字串>
assert_playwright_suite() {
    local pat="$1" json="${DOD_RUN_DIR}/${DOD_PLAYWRIGHT_JSON_NAME}"
    command -v jq >/dev/null 2>&1 || {
        echo "本項需要 jq"
        return 1
    }
    if [ ! -f "$json" ]; then
        echo "找不到 Playwright 報告(${json})。"
        echo "(本項驗的是 M2-26 那次 playwright test 的結果;請先跑 M2-26。)"
        return 1
    fi
    if dod_report_stale "$json" frontend "${REPO_ROOT}/frontend/e2e" "${REPO_ROOT}/frontend/src"; then
        echo "Playwright 報告已過期(原始碼比它新)。"
        return 1
    fi
    jq -e --arg p "$pat" '
        [ .. | objects | select(has("specs")) | .specs[]? ]
        | map(select((((.file // "") + " " + (.title // "")) | ascii_downcase)
                     | contains($p | ascii_downcase)))
        | length > 0 and all(.[]; .ok == true)' "$json" >/dev/null && return 0

    echo "Playwright 報告中 ${pat} 不存在或未全數通過:"
    jq -r --arg p "$pat" '
        [ .. | objects | select(has("specs")) | .specs[]? ]
        | map(select((((.file // "") + " " + (.title // "")) | ascii_downcase)
                     | contains($p | ascii_downcase)))
        | if length == 0 then ["  (找不到符合 \($p) 的 spec——沒有被驗到,不是通過)"]
          else map("  ok=\(.ok) \(.file // "") \(.title // "")") end
        | .[]' "$json" 2>/dev/null | head -n 10
    return 1
}
