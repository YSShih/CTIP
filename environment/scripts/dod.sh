#!/usr/bin/env bash
# Definition of Done Gate 檢查(docs/spec/15-dod-gates.md)。
#
# 用法:
#   ./environment/scripts/dod.sh mvp                 # DoD-MVP 全部 38 項(序列)
#   ./environment/scripts/dod.sh phase2              # DoD-Phase2 全部 27 項
#   ./environment/scripts/dod.sh full                # DoD-Full 全部 25 項
#   ./environment/scripts/dod.sh mvp M1-14           # 只執行單一項目
#   ./environment/scripts/dod.sh mvp --only M1-11 --skip M1-01
#
#   ./environment/scripts/dod.sh full --parallel     # 自行 fan-out(建議用法)
#   ./environment/scripts/dod.sh full --parallel -j 6
#
# 多個 agent 分工(共用同一個 run 目錄):
#   export CTIP_DOD_RUN=/tmp/ctip-dod-multi
#   ./environment/scripts/dod.sh full --reset        # 開新的一輪(清掉共用 run 目錄)
#   ./environment/scripts/dod.sh full --plan         # 看項目 / lane / 資源 / 相依
#   ./environment/scripts/dod.sh full --lane build   # agent A
#   ./environment/scripts/dod.sh full --lane frontend# agent B
#   ./environment/scripts/dod.sh full --shard 2/4    # 或依 shard 切
#   ./environment/scripts/dod.sh full --status       # 隨時查(不執行任何東西)
#   ./environment/scripts/dod.sh full --report       # 彙整全部 lane,exit 0/1
#
# 契約(§15.0):逐項印 [PASS]/[FAIL] 與失敗輸出;任一失敗不中止後續;
# 全過 exit 0,否則 exit 1 並列出失敗清單;結尾印出「需人工確認」清單。

_DOD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${_DOD_DIR}/_common.sh"
set +e # 覆寫 _common.sh 的 -e:gate 必須跑完全部檢查,不因單項失敗中止

source "${_DOD_DIR}/dod/registry.sh"
source "${_DOD_DIR}/dod/reports.sh"
source "${_DOD_DIR}/dod/checks.sh"
source "${_DOD_DIR}/dod/runner.sh"

# ---------------------------------------------------------------------------
# 參數解析
# ---------------------------------------------------------------------------
DOD_ARGV=("$@") # 下面的 shift 會把 $@ 吃光,caffeinate re-exec 要用原樣的參數
GATE=""
DOD_ONLY_IDS=""
DOD_SKIP_IDS=""
DOD_LANE_FILTER=""
DOD_SHARD=""
DOD_TIMEOUT_OVERRIDE=""
MODE=run
PARALLEL=0
JOBS_EXPLICIT=0

while [ $# -gt 0 ]; do
    case "$1" in
        --only)
            [ -n "${2:-}" ] || die "--only 需要一個項目 ID"
            DOD_ONLY_IDS="${DOD_ONLY_IDS} $2"
            shift 2
            ;;
        --skip)
            [ -n "${2:-}" ] || die "--skip 需要一個項目 ID"
            DOD_SKIP_IDS="${DOD_SKIP_IDS} $2"
            shift 2
            ;;
        --lane)
            [ -n "${2:-}" ] || die "--lane 需要一個 lane 名(build|frontend|static|stack|ci|aggregate)"
            DOD_LANE_FILTER="$2"
            shift 2
            ;;
        --shard)
            case "${2:-}" in
                [0-9]*/[0-9]*) ;;
                *) die "--shard 的格式是 <第幾份>/<總份數>,例如 --shard 2/4" ;;
            esac
            DOD_SHARD="$2"
            shift 2
            ;;
        -j)
            [ -n "${2:-}" ] || die "-j 需要一個數字"
            DOD_JOBS="$2"
            JOBS_EXPLICIT=1
            shift 2
            ;;
        --timeout)
            [ -n "${2:-}" ] || die "--timeout 需要秒數"
            DOD_TIMEOUT_OVERRIDE="$2"
            shift 2
            ;;
        --run-id)
            [ -n "${2:-}" ] || die "--run-id 需要一個名稱"
            DOD_RUN_DIR="${DOD_STATE_DIR}/run-$2"
            shift 2
            ;;
        --parallel)
            PARALLEL=1
            shift
            ;;
        --reset)
            MODE=reset
            shift
            ;;
        --plan)
            MODE=plan
            shift
            ;;
        --status)
            MODE=status
            shift
            ;;
        --report)
            MODE=report
            shift
            ;;
        --json)
            DOD_JSON=1
            shift
            ;;
        -*) die "未知選項:$1" ;;
        *)
            if [ -z "$GATE" ]; then GATE="$1"; else DOD_ONLY_IDS="${DOD_ONLY_IDS} $1"; fi
            shift
            ;;
    esac
done
DOD_JSON="${DOD_JSON:-0}"

case "$GATE" in
    mvp | phase2 | full) ;;
    *) die "用法:dod.sh <mvp|phase2|full> [id] [--only <id>] [--skip <id>]
     [--parallel [-j N]] [--lane <name>] [--shard i/N]
     [--plan|--status|--report|--reset] [--json] [--timeout <秒>] [--run-id <名稱>]" ;;
esac

if [ "$PARALLEL" = 1 ] && [ "$JOBS_EXPLICIT" = 0 ]; then
    # 預設併發度:留兩顆給 host 與 Docker
    _NCPU="$( (sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4) )"
    DOD_JOBS=$((_NCPU - 2))
    [ "$DOD_JOBS" -lt 1 ] && DOD_JOBS=1
    [ "$DOD_JOBS" -gt 8 ] && DOD_JOBS=8
fi

# 長時間 gate 必須擋住系統睡眠:2026-08-31 的過夜執行在睡眠後無聲卡死 8 小時 51 分,
# 而症狀(程序還在、容器 healthy)與「跑得比較慢」完全無法區分。唯讀模式不需要。
if [ "$MODE" = run ] && [ "${CTIP_DOD_CAFFEINATED:-}" != 1 ] &&
    command -v caffeinate >/dev/null 2>&1; then
    info "以 caffeinate -ims 包住執行(擋住系統睡眠)"
    export CTIP_DOD_CAFFEINATED=1
    exec caffeinate -ims "$0" "${DOD_ARGV[@]}"
fi

# 只跑一部分時把範圍標進結果行,避免被誤讀成整個 gate 的結論
DOD_SCOPE_LABEL=""
[ -n "$DOD_LANE_FILTER" ] && DOD_SCOPE_LABEL="${DOD_SCOPE_LABEL}/lane=${DOD_LANE_FILTER}"
[ -n "$DOD_SHARD" ] && DOD_SCOPE_LABEL="${DOD_SCOPE_LABEL}/shard=${DOD_SHARD}"
[ -n "$DOD_ONLY_IDS" ] && DOD_SCOPE_LABEL="${DOD_SCOPE_LABEL}/只跑${DOD_ONLY_IDS# }"
[ -n "$DOD_SKIP_IDS" ] && DOD_SCOPE_LABEL="${DOD_SCOPE_LABEL}/略過${DOD_SKIP_IDS# }"

DOD_GATE="$GATE"
# 共用一輪(多執行者分工):相依可能落在別的執行者身上,必須等它
DOD_SHARED_RUN=0
{ [ -n "$DOD_LANE_FILTER" ] || [ -n "$DOD_SHARD" ]; } && DOD_SHARED_RUN=1

dod_run_init "$GATE"
dod_select "$GATE"

# ---------------------------------------------------------------------------
# 唯讀模式
# ---------------------------------------------------------------------------

if [ "$MODE" = plan ]; then
    if [ "$DOD_JSON" = 1 ]; then
        printf '{"gate":"%s","runDir":"%s","items":[' "$GATE" "$DOD_RUN_DIR"
        _first=1
        _i=0
        while [ "$_i" -lt "$DOD_COUNT" ]; do
            if [ "${DOD_SELECTED[$_i]}" = 1 ]; then
                [ "$_first" = 1 ] || printf ','
                _first=0
                printf '{"id":"%s","lane":"%s","resources":"%s","after":"%s","kind":"%s","desc":"%s"}' \
                    "${DOD_ID[$_i]}" "${DOD_LANE[$_i]}" "${DOD_RES[$_i]}" \
                    "${DOD_AFTER[$_i]}" "${DOD_KIND[$_i]}" "${DOD_DESC[$_i]}"
            fi
            _i=$((_i + 1))
        done
        printf ']}\n'
    else
        info "=== 執行計畫(${GATE})——run 目錄:${DOD_RUN_DIR} ==="
        printf '%-8s %-10s %-16s %-12s %-11s %s\n' ID LANE RESOURCES AFTER KIND 描述
        _i=0
        while [ "$_i" -lt "$DOD_COUNT" ]; do
            if [ "${DOD_SELECTED[$_i]}" = 1 ]; then
                printf '%-8s %-10s %-16s %-12s %-11s %s\n' \
                    "${DOD_ID[$_i]}" "${DOD_LANE[$_i]}" "${DOD_RES[$_i]}" \
                    "${DOD_AFTER[$_i]}" "${DOD_KIND[$_i]}" "${DOD_DESC[$_i]}"
            fi
            _i=$((_i + 1))
        done
    fi
    exit 0
fi

if [ "$MODE" = status ]; then
    _now="$(date +%s)"
    [ "$DOD_JSON" = 1 ] && printf '{"gate":"%s","runDir":"%s","items":[' "$GATE" "$DOD_RUN_DIR"
    [ "$DOD_JSON" = 1 ] || info "=== 狀態(${GATE})——run 目錄:${DOD_RUN_DIR} ==="
    _first=1
    _i=0
    _pending=0
    _running=0
    _pass=0
    _fail=0
    while [ "$_i" -lt "$DOD_COUNT" ]; do
        if [ "${DOD_SELECTED[$_i]}" = 1 ]; then
            _id="${DOD_ID[$_i]}"
            _state="$(dod_state_of "$_id")"
            _start="$(_dod_field "$_id" 3 2>/dev/null || printf '')"
            _end="$(_dod_field "$_id" 4 2>/dev/null || printf '')"
            _pid="$(_dod_field "$_id" 5 2>/dev/null || printf '')"
            _elapsed=""
            case "$_state" in
                RUNNING)
                    _running=$((_running + 1))
                    [ -n "$_start" ] && [ "$_start" != "-" ] && _elapsed=$((_now - _start))
                    ;;
                PASS)
                    _pass=$((_pass + 1))
                    [ "$_end" != "-" ] && [ -n "$_end" ] && _elapsed=$((_end - _start))
                    ;;
                PENDING) _pending=$((_pending + 1)) ;;
                *)
                    _fail=$((_fail + 1))
                    [ "$_end" != "-" ] && [ -n "$_end" ] && _elapsed=$((_end - _start))
                    ;;
            esac
            if [ "$DOD_JSON" = 1 ]; then
                [ "$_first" = 1 ] || printf ','
                _first=0
                printf '{"id":"%s","lane":"%s","state":"%s","seconds":%s}' \
                    "$_id" "${DOD_LANE[$_i]}" "$_state" "${_elapsed:-null}"
            else
                _mark=""
                # 停滯偵測:跑超過該 lane 逾時的六成就標記。判斷卡死 vs 慢的依據是
                # 「執行時間 + CPU%」——只看「還在跑」會被誤導(2026-08-31 的教訓)。
                if [ "$_state" = RUNNING ] && [ -n "$_elapsed" ]; then
                    if [ "$_elapsed" -gt "$(($(dod_timeout_for "${DOD_LANE[$_i]}") * 6 / 10))" ]; then
                        _mark=" ⚠ 疑似停滯"
                        if [ -n "$_pid" ] && [ "$_pid" != "-" ]; then
                            _mark="${_mark}(CPU $(ps -o %cpu= -p "$_pid" 2>/dev/null | tr -d ' ' || printf '?')%)"
                        fi
                    fi
                fi
                printf '%-8s %-10s %-8s %6ss  %s%s\n' \
                    "$_id" "${DOD_LANE[$_i]}" "$_state" "${_elapsed:--}" "${DOD_DESC[$_i]}" "$_mark"
            fi
        fi
        _i=$((_i + 1))
    done
    if [ "$DOD_JSON" = 1 ]; then
        printf '],"summary":{"pending":%s,"running":%s,"pass":%s,"fail":%s}}\n' \
            "$_pending" "$_running" "$_pass" "$_fail"
    else
        info ""
        info "PENDING ${_pending} / RUNNING ${_running} / PASS ${_pass} / 未通過 ${_fail}"
        info "(判斷整輪是否結束一律用**執行中那個行程的退出碼**,不要比對本輸出——15 §15.0 第 5 條)"
    fi
    exit 0
fi

if [ "$MODE" = reset ]; then
    # 多執行者分工的起手式:`--lane` / `--shard` **不**清結果(它們要共用同一輪),
    # 因此共用的 run 目錄必須有一個明確的「開新的一輪」動作,否則第二輪會直接沿用
    # 上一輪的 PASS 與 stamp,什麼都不重跑。
    dod_run_reset
    info "已清空 run 目錄:${DOD_RUN_DIR}"
    info "接著各執行者可用 --lane / --shard 分工,最後以 --report 彙整。"
    exit 0
fi

if [ "$MODE" = report ]; then
    _i=0
    while [ "$_i" -lt "$DOD_COUNT" ]; do
        [ "${DOD_SELECTED[$_i]}" = 1 ] && dod_print_result "$_i"
        _i=$((_i + 1))
    done
    dod_summarise "$GATE"
    exit $?
fi

# ---------------------------------------------------------------------------
# 執行
# ---------------------------------------------------------------------------

# `--lane` / `--shard` 是多 agent 分工,要跟其他 agent 共用同一輪,不清結果;
# 其餘情況是全新的一輪。
if [ -z "$DOD_LANE_FILTER" ] && [ -z "$DOD_SHARD" ]; then
    dod_run_reset
fi

# 上一輪殘留的容器會讓這一輪的分數不可信。
#
# 實測(2026-08-31):前一輪被 kill 之後 staging 的 8 個容器還開著,下一輪的 M1-01
# 就在 surefire「forked VM terminated without properly saying goodbye」掛掉
# ——容器與 Testcontainers 互搶記憶體,而錯誤訊息裡完全看不出這件事。
# 與 ADR 0028 記的「並行執行的分數完全不可信」是同一類。
#
# mvp 的三個容器是正常的(M1-14 本來就要它們);多出來的才警告。
if [ "$MODE" = run ] && [ -z "$DOD_LANE_FILTER" ] && [ -z "$DOD_SHARD" ]; then
    _RUNNING_SVC="$(docker compose --env-file "$(env_file_path mvp)" -f "${COMPOSE_FILE}" \
        ps --services --status running 2>/dev/null | tr '\n' ' ')"
    _RUNNING_N="$(printf '%s' "$_RUNNING_SVC" | wc -w | tr -d ' ')"
    if [ "${_RUNNING_N:-0}" -gt 3 ]; then
        warn "偵測到 ${_RUNNING_N} 個服務正在執行:${_RUNNING_SVC}"
        warn "這超出 mvp 的三個容器,通常是上一輪(可能被中斷)留下的 staging/full profile。"
        warn "它們會與 Testcontainers 搶記憶體,症狀是 surefire 的"
        warn "「forked VM terminated without properly saying goodbye」——看不出是資源問題。"
        warn "建議先收掉:./environment/scripts/down.sh staging"
    fi
fi

info "=== DoD Gate:${GATE}(run 目錄:${DOD_RUN_DIR};併發 ${DOD_JOBS})==="
[ -n "$DOD_LANE_FILTER" ] && info "只執行 lane:${DOD_LANE_FILTER}"
[ -n "$DOD_SHARD" ] && info "只執行 shard:${DOD_SHARD}"

trap 'i=0; while [ "$i" -lt "$DOD_COUNT" ]; do _dod_locks_release "${DOD_RES[$i]}"; i=$((i+1)); done' EXIT

dod_schedule

# --lane / --shard 只回報自己那一份;整輪的結論用 --report 彙整。
if [ -n "$DOD_LANE_FILTER" ] || [ -n "$DOD_SHARD" ]; then
    info ""
    info "本份已結束。整輪彙整:$0 ${GATE} --report(run 目錄:${DOD_RUN_DIR})"
fi

# 顯式 exit:gate 的結論就是退出碼(15 §15.0 第 5 條),不能讓它取決於
# EXIT trap 裡最後一個指令的狀態。
dod_summarise "$GATE"
_DOD_RC=$?
exit "$_DOD_RC"
