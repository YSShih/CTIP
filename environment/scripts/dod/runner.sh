#!/usr/bin/env bash
# DoD Gate 執行核心(docs/spec/15-dod-gates.md §15.0)。由 dod.sh source,不直接執行。
#
# 負責:run 目錄與結果檔、跨行程 memo、具名資源鎖、逐項逾時、資源感知的 DAG 排程。
#
# ⚠️ 本檔必須在 bash 3.2 下可用(macOS 內建版本):
#   - 沒有 associative array(用平行的 indexed array + 線性搜尋)
#   - 沒有 `wait -n`(以 `kill -0` 輪詢 + `wait <pid>` 回收)

# ---------------------------------------------------------------------------
# run 目錄
# ---------------------------------------------------------------------------
#
# 放 ${TMPDIR} 而不是 repo 內:結構契約(05 §5.1)逐檔列出 repo 的內容,
# 而 gate 的中間產物不該進版控,也不該讓 .gitignore 為它開特例。

DOD_STATE_DIR="${TMPDIR:-/tmp}/ctip-dod-$(printf '%s' "$REPO_ROOT" | cksum | tr -d ' ')"
DOD_LOCK_DIR="${DOD_STATE_DIR}/locks"

dod_run_init() { # dod_run_init <gate>
    local gate="$1"
    if [ -z "${DOD_RUN_DIR:-}" ]; then
        if [ -n "${CTIP_DOD_RUN:-}" ]; then
            DOD_RUN_DIR="$CTIP_DOD_RUN"
        else
            DOD_RUN_DIR="${DOD_STATE_DIR}/run-${gate}"
        fi
    fi
    mkdir -p "${DOD_RUN_DIR}/results" "${DOD_RUN_DIR}/output" "${DOD_RUN_DIR}/memo" "$DOD_LOCK_DIR"
    export DOD_RUN_DIR
    # 只有真的要執行的模式才記 run.meta。--status / --plan / --report 是唯讀的,
    # 讓它們改寫開始時間會把「這一輪跑了多久」洗掉(實測:查了一次狀態,
    # 已跑 15 分鐘的一輪變成「已跑 11 秒」)。
    [ "${MODE:-run}" = run ] || return 0
    printf 'gate=%s\nstarted=%s\npid=%s\n' "$gate" "$(date +%s)" "$$" >"${DOD_RUN_DIR}/run.meta"
}

# 全新的一輪:清掉上一輪的結果與 memo(--parallel / 預設整輪執行時用)。
# `--lane` / `--shard` **不**清,那是要跟其他 agent 共用同一輪。
dod_run_reset() {
    rm -rf "${DOD_RUN_DIR}/results" "${DOD_RUN_DIR}/output" "${DOD_RUN_DIR}/memo" \
        "${DOD_RUN_DIR}"/stamp-* "${DOD_RUN_DIR}"/*.json 2>/dev/null
    mkdir -p "${DOD_RUN_DIR}/results" "${DOD_RUN_DIR}/output" "${DOD_RUN_DIR}/memo"
}

# ---------------------------------------------------------------------------
# 結果檔(原子寫入:先寫 tmp 再 mv,讀的一方不會看到半寫入的內容)
# ---------------------------------------------------------------------------
# 格式:<state>|<exit>|<start epoch>|<end epoch>|<pid>
#   state ∈ RUNNING | PASS | FAIL | TIMEOUT | SKIP

_dod_write_result() { # <id> <state> <exit> <start> <end> <pid>
    local f="${DOD_RUN_DIR}/results/$1"
    printf '%s|%s|%s|%s|%s\n' "$2" "$3" "$4" "$5" "$6" >"${f}.tmp.$$"
    mv -f "${f}.tmp.$$" "$f"
}

_dod_field() { # <id> <欄位序號>
    local f="${DOD_RUN_DIR}/results/$1"
    [ -f "$f" ] || return 1
    cut -d'|' -f"$2" <"$f"
}

dod_state_of() { # <id> → PENDING 若無結果檔
    local s
    s="$(_dod_field "$1" 1 2>/dev/null)" || {
        printf 'PENDING'
        return 0
    }
    printf '%s' "${s:-PENDING}"
}

dod_is_done() {
    case "$(dod_state_of "$1")" in
        PASS | FAIL | TIMEOUT | SKIP) return 0 ;;
    esac
    return 1
}

# ---------------------------------------------------------------------------
# 具名資源鎖(取代原本的單一全域鎖)
# ---------------------------------------------------------------------------
#
# 原本是「同一個 repo 同時只能有一個 gate 在跑」(15 §15.0 第 4 點)。那條規則的實據是
# **共用 backend/*/target 與容器記憶體**,而不是「gate」這個單位——把它降到資源層級之後,
# 前端、靜態檢查、CI 查詢就不必再排隊等 Maven(ADR 0052)。
#
# 一律以排序後的順序取得,且**取不到就全部放掉**(非阻塞):序死鎖與活鎖都不可能發生。

_dod_lock_try() { # <resource> → 0 取得
    local d="${DOD_LOCK_DIR}/$1" holder
    if mkdir "$d" 2>/dev/null; then
        printf '%s' "$$" >"${d}/pid"
        return 0
    fi
    holder="$(cat "${d}/pid" 2>/dev/null || true)"
    if [ -n "$holder" ] && kill -0 "$holder" 2>/dev/null; then
        return 1
    fi
    # 上一次被 kill 而沒清掉的殘鎖:接手
    rm -rf "$d" 2>/dev/null && mkdir "$d" 2>/dev/null || return 1
    printf '%s' "$$" >"${d}/pid"
    return 0
}

# 只放掉**自己持有**的:多個 agent 共用同一組鎖,不得把別人的鎖刪掉。
_dod_lock_release() {
    local d="${DOD_LOCK_DIR}/$1"
    [ -d "$d" ] || return 0
    [ "$(cat "${d}/pid" 2>/dev/null || true)" = "$$" ] || return 0
    rm -rf "$d" 2>/dev/null
}

# _dod_locks_acquire <逗號分隔的資源> → 0 全部取得;失敗時不持有任何一把
_dod_locks_acquire() {
    case "${1:-}" in "" | "-") return 0 ;; esac
    local r taken=""
    for r in $(printf '%s' "$1" | tr ',' '\n' | sort); do
        if _dod_lock_try "$r"; then
            taken="${taken} ${r}"
        else
            for r in $taken; do _dod_lock_release "$r"; done
            return 1
        fi
    done
    return 0
}

_dod_locks_release() {
    case "${1:-}" in "" | "-") return 0 ;; esac
    local r
    for r in $(printf '%s' "$1" | tr ',' '\n'); do _dod_lock_release "$r"; done
}

# ---------------------------------------------------------------------------
# 逾時(逐項斷路器)
# ---------------------------------------------------------------------------
#
# 這一層是 2026-08-31 那次過夜無聲卡死 8 小時 51 分**唯一擋得住的**東西:
# Testcontainers 的連線在系統睡眠後壞掉,測試永遠等下去,不逾時也不報錯。
# 14 §14.8 的 JUnit 30 秒在 JVM 內,管不到「JVM 整個卡死」或「Maven 呼叫不回來」。

DOD_TIMEOUT_build=2700
DOD_TIMEOUT_stack=1800
DOD_TIMEOUT_frontend=900
DOD_TIMEOUT_static=300
DOD_TIMEOUT_ci=300
DOD_TIMEOUT_aggregate=30

dod_timeout_for() { # <lane>
    if [ -n "${DOD_TIMEOUT_OVERRIDE:-}" ]; then
        printf '%s' "$DOD_TIMEOUT_OVERRIDE"
        return
    fi
    eval "printf '%s' \"\${DOD_TIMEOUT_$1:-600}\""
}

_dod_kill_tree() {
    local p="$1" c
    for c in $(pgrep -P "$p" 2>/dev/null); do _dod_kill_tree "$c"; done
    kill -TERM "$p" 2>/dev/null
}

# _dod_run_with_timeout <秒> <指令字串> → 回傳 124 代表逾時
#
# macOS 沒有 coreutils 的 `timeout`,自己做:一個 watcher 子行程數秒,時間到就把整棵
# 行程樹 TERM 掉(Maven 會生 JVM 子行程,只殺 shell 沒有用),寬限後再 KILL。
# 「是不是被殺的」以 marker 檔判定——靠 watcher 的退出碼會與它自己的寬限期形成 race。
_dod_run_with_timeout() {
    local secs="$1" cmd="$2" pid watcher rc marker
    marker="${DOD_RUN_DIR}/.timeout.$$.$RANDOM"
    (
        cd "$REPO_ROOT" || exit 1
        eval "$cmd"
    ) &
    pid=$!
    (
        _dod_wait_i=0
        while [ "$_dod_wait_i" -lt "$secs" ]; do
            kill -0 "$pid" 2>/dev/null || exit 0
            sleep 1
            _dod_wait_i=$((_dod_wait_i + 1))
        done
        kill -0 "$pid" 2>/dev/null || exit 0
        : >"$marker"
        _dod_kill_tree "$pid"
        sleep 5
        kill -KILL "$pid" 2>/dev/null
    ) &
    watcher=$!
    wait "$pid"
    rc=$?
    kill -TERM "$watcher" 2>/dev/null
    wait "$watcher" 2>/dev/null
    if [ -f "$marker" ]; then
        rm -f "$marker"
        return 124
    fi
    return "$rc"
}

# ---------------------------------------------------------------------------
# 執行單一項目
# ---------------------------------------------------------------------------

# dod_execute <index>:在**子行程**內執行,結果寫進 run 目錄。
dod_execute() {
    local i="$1"
    local id="${DOD_ID[$i]}" lane="${DOD_LANE[$i]}" kind="${DOD_KIND[$i]}" spec="${DOD_SPEC[$i]}"
    local out="${DOD_RUN_DIR}/output/${id}.out"
    local start end rc cmd key memo

    start="$(date +%s)"
    # bash 3.2 沒有 BASHPID,而子 shell 裡的 $$ 仍是父行程的 pid;
    # 用 sh -c 的 $PPID 才拿得到自己的(--status 的停滯偵測要靠它看 CPU%)。
    _dod_write_result "$id" RUNNING - "$start" - "$(sh -c 'echo $PPID')"

    case "$kind" in
        aggregate)
            # 不執行任何東西:所有指定字首的項目皆 PASS 才 PASS。
            #
            # ⚠️ 判定範圍是**整個 gate**,不是「本行程選中的項目」。用後者的話,
            # `--lane aggregate` 之下一個 M1 項目都沒被選中,rc 於是保持 0,
            # M2-01 空空地 PASS——正是本專案一再要防的假綠。
            rc=0
            : >"$out"
            local pfx j jid
            for pfx in $(printf '%s' "$spec" | tr ',' ' '); do
                j=0
                while [ "$j" -lt "$DOD_COUNT" ]; do
                    jid="${DOD_ID[$j]}"
                    case "$jid" in
                        "${pfx}"-*)
                            if [ "$j" != "$i" ] && dod_in_gate "$jid" "$DOD_GATE" &&
                                [ "$(dod_state_of "$jid")" != PASS ]; then
                                echo "${jid} 未通過($(dod_state_of "$jid"))" >>"$out"
                                rc=1
                            fi
                            ;;
                    esac
                    j=$((j + 1))
                done
            done
            [ "$rc" -eq 0 ] || echo "(本項是聚合判定:${spec}-* 全數 PASS 才算通過)" >>"$out"
            ;;
        report | vitest | playwright)
            case "$kind" in
                report) cmd="assert_test_report ${spec}" ;;
                vitest) cmd="assert_vitest_suite ${spec}" ;;
                playwright) cmd="assert_playwright_suite ${spec}" ;;
            esac
            _dod_run_with_timeout "$(dod_timeout_for "$lane")" "$cmd" >"$out" 2>&1
            rc=$?
            ;;
        *)
            # memo:同一個指令字串在同一輪只跑一次(M2-27 與 M1-01 逐字相同)。
            # 放在 run 目錄而不是行程內的 mktemp -d,跨行程(多 agent)也共用得到。
            key="$(printf '%s' "$spec" | cksum | tr -d ' ')"
            memo="${DOD_RUN_DIR}/memo/${key}"
            if [ -f "${memo}.status" ]; then
                rc="$(cat "${memo}.status")"
                cp "${memo}.out" "$out" 2>/dev/null || : >"$out"
            else
                # 第一個建置開跑前寫下 stamp:報告斷言以它判斷「這一輪產生的」。
                #
                # ⚠️ **已存在就不覆寫**。`_heavy` 是第二個寫 backend 報告的項目,它若重設 stamp,
                # M1-01 稍早產生的 40 幾份報告會全部被判成「比 stamp 舊」而過期
                # ——實測到的第一版就是這樣,50 項無辜 FAIL(ADR 0052)。
                case "$id" in
                    M1-01 | _heavy) [ -f "${DOD_RUN_DIR}/stamp-backend" ] || : >"${DOD_RUN_DIR}/stamp-backend" ;;
                    M1-09) [ -f "${DOD_RUN_DIR}/stamp-frontend" ] || : >"${DOD_RUN_DIR}/stamp-frontend" ;;
                esac
                _dod_run_with_timeout "$(dod_timeout_for "$lane")" "$spec" >"$out" 2>&1
                rc=$?
                printf '%s' "$rc" >"${memo}.status"
                cp "$out" "${memo}.out" 2>/dev/null || true
            fi
            ;;
    esac

    end="$(date +%s)"
    if [ "$rc" -eq 0 ]; then
        _dod_write_result "$id" PASS 0 "$start" "$end" -
    elif [ "$rc" -eq 124 ]; then
        echo "" >>"$out"
        echo "⚠ 逾時($(dod_timeout_for "$lane") 秒)後被強制中斷。" >>"$out"
        echo "  卡死與「跑得比較慢」在外觀上無法區分(2026-08-31 過夜卡死 8h51m 的教訓)," >>"$out"
        echo "  因此本項判 FAIL 而不是繼續等。要放寬用 --timeout <秒>。" >>"$out"
        _dod_write_result "$id" TIMEOUT 124 "$start" "$end" -
    else
        _dod_write_result "$id" FAIL "$rc" "$start" "$end" -
    fi
}

# ---------------------------------------------------------------------------
# 選取(gate / --only / --skip / --lane / --shard)
# ---------------------------------------------------------------------------

DOD_SELECTED=() # 與 DOD_ID 等長的 0/1

# dod_select <gate>:填 DOD_SELECTED。前置步驟(_*)只在有被選中的項目相依時才留下。
dod_select() {
    local gate="$1" i=0 id
    while [ "$i" -lt "$DOD_COUNT" ]; do
        id="${DOD_ID[$i]}"
        DOD_SELECTED[$i]=0
        if dod_in_gate "$id" "$gate"; then
            case "$id" in
                _*) DOD_SELECTED[$i]=1 ;; # 先留著,下面再剪
                *)
                    DOD_SELECTED[$i]=1
                    if [ -n "$DOD_ONLY_IDS" ]; then
                        dod_in_list "$id" "$DOD_ONLY_IDS" || DOD_SELECTED[$i]=0
                    fi
                    dod_in_list "$id" "$DOD_SKIP_IDS" && DOD_SELECTED[$i]=0
                    if [ -n "$DOD_LANE_FILTER" ] && [ "${DOD_LANE[$i]}" != "$DOD_LANE_FILTER" ]; then
                        DOD_SELECTED[$i]=0
                    fi
                    ;;
            esac
        fi
        i=$((i + 1))
    done

    # --shard i/N:對已選中的**非前置**項目依序輪流分配
    if [ -n "$DOD_SHARD" ]; then
        local n=0 idx="${DOD_SHARD%%/*}" total="${DOD_SHARD##*/}"
        i=0
        while [ "$i" -lt "$DOD_COUNT" ]; do
            case "${DOD_ID[$i]}" in _*) ;; *)
                if [ "${DOD_SELECTED[$i]}" = 1 ]; then
                    [ "$((n % total + 1))" -eq "$idx" ] || DOD_SELECTED[$i]=0
                    n=$((n + 1))
                fi
                ;;
            esac
            i=$((i + 1))
        done
    fi

    # 剪掉沒有任何被選中項目相依的前置步驟
    i=0
    while [ "$i" -lt "$DOD_COUNT" ]; do
        case "${DOD_ID[$i]}" in
            _*)
                if [ "${DOD_SELECTED[$i]}" = 1 ] && ! _dod_prep_needed "${DOD_ID[$i]}"; then
                    DOD_SELECTED[$i]=0
                fi
                ;;
        esac
        i=$((i + 1))
    done
}

_dod_prep_needed() { # <prep id>
    local j=0
    while [ "$j" -lt "$DOD_COUNT" ]; do
        if [ "${DOD_SELECTED[$j]}" = 1 ]; then
            case "${DOD_ID[$j]}" in _*) ;; *)
                dod_in_list "$1" "$(printf '%s' "${DOD_AFTER[$j]}" | tr ',' ' ')" && return 0
                ;;
            esac
        fi
        j=$((j + 1))
    done
    return 1
}

dod_in_list() { case " $2 " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }

# ---------------------------------------------------------------------------
# 相依判定
# ---------------------------------------------------------------------------
#
# after 只表示**順序**,不表示閘門:相依失敗時後續項目照樣執行
# ——15 §15.0「不得因為某項失敗就中止後續檢查(要一次看到全部問題)」。

_dod_deps_met() { # <index>
    local after="${DOD_AFTER[$1]}" d j pfx

    # 聚合項目的相依是「它聚合的每一項」,不必(也不該)在 registry 逐一列出:
    # 少了這一段,M2-01 / M3-01 會在第 0 秒就以「大家都還沒 PASS」判 FAIL。
    if [ "${DOD_KIND[$1]}" = aggregate ]; then
        for pfx in $(printf '%s' "${DOD_SPEC[$1]}" | tr ',' ' '); do
            j=0
            while [ "$j" -lt "$DOD_COUNT" ]; do
                case "${DOD_ID[$j]}" in
                    "${pfx}"-*)
                        # 排除自己而不是排除所有聚合項:M3-01 聚合 M1,M2,其中包含
                        # M2-01(它本身也是聚合項),必須等它先有結論。無循環——
                        # M2-01 只聚合 M1,而 M1 裡沒有聚合項。
                        if [ "${DOD_SELECTED[$j]}" = 1 ] && [ "$j" != "$1" ]; then
                            dod_is_done "${DOD_ID[$j]}" || return 1
                        fi
                        ;;
                esac
                j=$((j + 1))
            done
        done
        return 0
    fi

    [ "$after" = "-" ] && return 0
    for d in $(printf '%s' "$after" | tr ',' ' '); do
        case "$d" in
            build | frontend | static | stack | ci)
                # lane 名:該 lane 的項目全部結束。共用一輪時看的是**全 gate**
                # (那些項目由別的執行者負責),自己跑時只看本行程選中的。
                j=0
                while [ "$j" -lt "$DOD_COUNT" ]; do
                    if [ "${DOD_LANE[$j]}" = "$d" ]; then
                        if [ "${DOD_SELECTED[$j]}" = 1 ] ||
                            { [ "$DOD_SHARED_RUN" = 1 ] && dod_in_gate "${DOD_ID[$j]}" "$DOD_GATE"; }; then
                            dod_is_done "${DOD_ID[$j]}" || return 1
                        fi
                    fi
                    j=$((j + 1))
                done
                ;;
            *)
                # 項目 ID。沒被本行程選中時分兩種情況:
                #
                #   共用一輪(--lane / --shard):相依的是**別的執行者**負責的項目,
                #     必須等它有結論——否則 `--shard 2/4` 的報告斷言會在
                #     `--shard 1/4` 的 M1-01 還沒建置完就跑掉,無辜 FAIL。
                #   自己跑一部分(--only / --skip):相依項根本不會有人跑,等下去就是掛死,
                #     直接放行,讓判準自己以「報告不存在/過期」誠實 FAIL。
                j="$(dod_index_of "$d")" || continue
                if [ "${DOD_SELECTED[$j]}" != 1 ]; then
                    [ "$DOD_SHARED_RUN" = 1 ] || continue
                    dod_in_gate "$d" "$DOD_GATE" || continue
                    # 等別的執行者,但**不是無限等**:相依還停在 PENDING(沒有人在動它)
                    # 且我們已經空轉超過 DOD_DEP_WAIT,就當作「那個 lane 沒人跑」而放行,
                    # 讓判準自己以「報告不存在」誠實 FAIL。RUNNING 中的則繼續等——
                    # 那是真的有人在做。
                    if [ "$DOD_DEP_WAIT_EXPIRED" = 1 ] &&
                        [ "$(dod_state_of "$d")" = PENDING ]; then
                        continue
                    fi
                fi
                dod_is_done "$d" || return 1
                ;;
        esac
    done
    return 0
}

# ---------------------------------------------------------------------------
# 排程器
# ---------------------------------------------------------------------------

DOD_JOBS=1
# 共用一輪時,等別的執行者跑相依項的上限(秒)。逾時後不再等停在 PENDING 的相依。
DOD_DEP_WAIT="${DOD_DEP_WAIT:-300}"
DOD_DEP_WAIT_EXPIRED=0

# 排程原則:只有搶同一個實體資源的項目才序列化,其餘一律同時跑。
# 每一輪把所有「相依已滿足、資源空著、還沒開始」的項目發動出去,上限 DOD_JOBS。
#
# 資源鎖在**父行程**以非阻塞方式取得:取不到就這一輪先跳過,下一輪再試。
# 因此不需要等待,也就不可能死鎖——而跨行程(多個 agent)的互斥同樣由這把鎖保證。
dod_schedule() {
    local i pid rc
    local -a run_pid run_idx new_pid new_idx
    run_pid=()
    run_idx=()
    local remaining=1 progressed n _idle=0

    while [ "$remaining" -eq 1 ]; do
        remaining=0
        progressed=0

        # 1. 發動
        i=0
        while [ "$i" -lt "$DOD_COUNT" ]; do
            if [ "${DOD_SELECTED[$i]}" = 1 ] && [ "$(dod_state_of "${DOD_ID[$i]}")" = PENDING ]; then
                remaining=1
                if [ "${#run_pid[@]}" -lt "$DOD_JOBS" ] && _dod_deps_met "$i"; then
                    if _dod_locks_acquire "${DOD_RES[$i]}"; then
                        # 先在**父行程**標記 RUNNING 再 fork:子行程要花一點時間才寫得到
                        # 結果檔,這段空窗裡下一輪掃描會看到 PENDING 而重複發動同一項。
                        _dod_write_result "${DOD_ID[$i]}" RUNNING - "$(date +%s)" - -
                        dod_execute "$i" &
                        run_pid[${#run_pid[@]}]=$!
                        run_idx[${#run_idx[@]}]=$i
                        progressed=1
                    fi
                fi
            fi
            i=$((i + 1))
        done

        # 2. 回收
        new_pid=()
        new_idx=()
        n=0
        while [ "$n" -lt "${#run_pid[@]}" ]; do
            pid="${run_pid[$n]}"
            i="${run_idx[$n]}"
            if kill -0 "$pid" 2>/dev/null; then
                new_pid[${#new_pid[@]}]="$pid"
                new_idx[${#new_idx[@]}]="$i"
                remaining=1
            else
                wait "$pid" 2>/dev/null
                _dod_locks_release "${DOD_RES[$i]}"
                dod_print_result "$i"
                progressed=1
            fi
            n=$((n + 1))
        done
        run_pid=("${new_pid[@]:-}")
        run_idx=("${new_idx[@]:-}")
        # bash 3.2 對空陣列展開的處理:上一行在空陣列時會塞一個空字串,清掉
        [ "${#new_pid[@]}" -eq 0 ] && {
            run_pid=()
            run_idx=()
        }

        [ "$remaining" -eq 1 ] || break
        if [ "$progressed" -eq 1 ]; then
            _idle=0
        else
            sleep 1
            _idle=$((_idle + 1))
            # 停等超過一分鐘就說在等誰:共用一輪時這通常代表在等別的執行者,
            # 而「安靜地不動」與「掛死」在畫面上無法區分(2026-08-31 的教訓)。
            if [ "$((_idle % 60))" -eq 0 ] && [ "${#run_pid[@]}" -eq 0 ]; then
                i=0
                while [ "$i" -lt "$DOD_COUNT" ]; do
                    if [ "${DOD_SELECTED[$i]}" = 1 ] &&
                        [ "$(dod_state_of "${DOD_ID[$i]}")" = PENDING ]; then
                        warn "等待中(${_idle}s):${DOD_ID[$i]} 的相依「${DOD_AFTER[$i]}」或資源「${DOD_RES[$i]}」尚未就緒"
                        break
                    fi
                    i=$((i + 1))
                done
            fi
            if [ "$DOD_DEP_WAIT_EXPIRED" = 0 ] && [ "$_idle" -ge "$DOD_DEP_WAIT" ] &&
                [ "${#run_pid[@]}" -eq 0 ]; then
                DOD_DEP_WAIT_EXPIRED=1
                warn "空轉 ${_idle}s 且相依仍停在 PENDING——判定「沒有其他執行者在跑那些項目」,"
                warn "不再等待。相依它們的判準會以「報告不存在/過期」誠實 FAIL。"
                warn "若其他執行者只是還沒開始,請調高 DOD_DEP_WAIT(目前 ${DOD_DEP_WAIT}s)後重跑。"
            fi
        fi
    done

    n=0
    while [ "$n" -lt "${#run_pid[@]}" ]; do
        wait "${run_pid[$n]}" 2>/dev/null
        n=$((n + 1))
    done
}

# ---------------------------------------------------------------------------
# 輸出
# ---------------------------------------------------------------------------

dod_print_result() { # <index>
    local id="${DOD_ID[$1]}" state
    state="$(dod_state_of "$id")"
    case "$id" in
        _*)
            # 前置步驟不是 DoD 項目:不計入 N/M,只有失敗時說一聲
            [ "$state" = PASS ] || {
                warn "前置步驟 ${id} ${state}(${DOD_DESC[$1]});相依它的項目會因為報告不存在或過期而 FAIL"
                sed 's/^/       | /' "${DOD_RUN_DIR}/output/${id}.out" 2>/dev/null | tail -n 30
            }
            return 0
            ;;
    esac
    if [ "$state" = PASS ]; then
        printf '%s[PASS]%s %s %s\n' "${C_GREEN}" "${C_RESET}" "$id" "${DOD_DESC[$1]}"
    else
        printf '%s[%s]%s %s %s\n' "${C_RED}" "$state" "${C_RESET}" "$id" "${DOD_DESC[$1]}"
        sed 's/^/       | /' "${DOD_RUN_DIR}/output/${id}.out" 2>/dev/null | tail -n 60
    fi
}

DOD_SCOPE_LABEL="${DOD_SCOPE_LABEL:-}"

DOD_MANUAL_ITEMS='  P-01 聚合圖(03-diagrams.md §3.2)與實際 domain 類別的方法一致
  P-02 Ubiquitous Language 詞彙表被遵守(可自動化部分已列為 ArchUnit 擴充)
  P-03 程式碼「人類易讀」
  P-04 Grafana dashboard 的圖表確實有意義
  P-05 docs/architecture/decisions/ 的 ADR 內容正確
  P-06 版本表的「推估」支援終止日(需上網查證,見 06-tech-stack.md §6.4)
  P-07 deploy-prod 的 production environment 已設定 required reviewers(GitHub repo 設定,非版控檔案)'

# dod_summarise <gate> → 印結果行與人工清單,回傳 0/1
dod_summarise() {
    local gate="$1" i=0 total=0 pass=0 failed="" state
    while [ "$i" -lt "$DOD_COUNT" ]; do
        if [ "${DOD_SELECTED[$i]}" = 1 ]; then
            case "${DOD_ID[$i]}" in _*) ;; *)
                total=$((total + 1))
                state="$(dod_state_of "${DOD_ID[$i]}")"
                if [ "$state" = PASS ]; then
                    pass=$((pass + 1))
                else
                    failed="${failed} ${DOD_ID[$i]}"
                fi
                ;;
            esac
        fi
        i=$((i + 1))
    done

    info ""
    # 帶 gate 名稱(15 §15.0 第 7 條)。判斷完成請一律用退出碼,不要比對 log。
    #
    # 只跑一部分時**必須把範圍寫進結果行**:`=== 結果(full):6/6 通過 ===` 從一個
    # `--lane frontend` 印出來,看起來就像整個 full gate 過了——正是本專案一再要防的
    # 「看起來有結論、其實沒有」。
    info "=== 結果(${gate}${DOD_SCOPE_LABEL}):${pass}/${total} 通過 ==="
    [ -n "$failed" ] && printf '%s失敗項目:%s%s\n' "${C_RED}" "${failed}" "${C_RESET}"

    info ""
    info "=== 需人工確認(以下項目未被自動驗證,見 15-dod-gates.md §15.5)==="
    printf '%s\n' "$DOD_MANUAL_ITEMS"

    [ -z "$failed" ]
}
