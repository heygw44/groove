#!/usr/bin/env bash
# 부하 테스트가 도는 동안 EC2 자원을 1초 간격으로 CSV 에 적재한다. SSH 세션은
# start 시 한 번만 열고, 원격에서 무한 루프를 돌려 stdout 으로 CSV 행을
# 흘려보내는 구조다(샘플마다 SSH 를 새로 열면 핸드셰이크 비용 자체가 측정
# 대상 서버에 부하를 준다).
#
# 측정 대상인 한정반 선착순 구매 러시는 1~3초 안에 끝난다. docker stats
# --no-stream(약 1.5초) + vmstat 1 2(1초) + ss -s 를 매 샘플 돌리면 실효
# 간격이 5초 가까이 벌어져 피크를 통째로 놓친다. 그래서 컨테이너 CPU/메모리는
# cgroup v2 파일을 직접 읽고, steal 은 /proc/stat, TCP 는 /proc/net/sockstat 을
# 읽는다 — 전부 open/read 몇 번이라 1초 간격을 실제로 지킬 수 있다. cgroup 경로가
# 없는 환경(구형 커널, cgroup v1 등)에서만 docker stats 로 폴백한다.
# shellcheck disable=SC2086 # SSH_OPTS 는 여러 -o 플래그를 담는 문자열이라 의도적으로 언쿼팅
set -euo pipefail

SSH_KEY="${SSH_KEY:-$HOME/.ssh/groove-key.pem}"
SSH_HOST="${SSH_HOST:-ubuntu@52.78.95.139}"
SSH_OPTS="${SSH_OPTS:--o ConnectTimeout=8 -o BatchMode=yes}"

REMOTE_TAG="GROOVE_MONITOR_TAG"

usage() {
    cat <<EOF
사용법:
  $(basename "$0") start <출력디렉토리> [--interval 1]
  $(basename "$0") stop <출력디렉토리>
  $(basename "$0") snapshot <출력디렉토리> <라벨>

환경변수: SSH_KEY(기본 $HOME/.ssh/groove-key.pem), SSH_HOST(기본 ubuntu@52.78.95.139), SSH_OPTS
EOF
}

# 원격에서 도는 샘플링 루프. $1=간격(초), $2=태그(정리용, 사용 안 하지만 cmdline 에 남겨 pgrep 대상으로 씀)
remote_loop_script() {
    cat <<'REMOTE_SCRIPT'
set -uo pipefail
INTERVAL="$1"

# 컨테이너별 cgroup v2 디렉터리를 루프 시작 전에 한 번만 찾는다(docker inspect
# 도 매 샘플 부르면 비용이라 밖으로 뺐다). 셋 중 하나라도 못 찾으면 그 샘플러
# 전체를 docker stats 폴백으로 돌린다 — 컨테이너마다 방식이 섞이면 CSV 해석이
# 더 헷갈린다.
CG_MODE="cgroup"
declare -A CG_DIR
for key_name in "backend:groove-backend" "mysql:groove-mysql" "redis:groove-redis"; do
    key="${key_name%%:*}"
    cname="${key_name##*:}"
    cid=$(docker inspect --format '{{.Id}}' "$cname" 2>/dev/null || true)
    if [ -z "$cid" ]; then
        CG_MODE="fallback"
        continue
    fi
    found=""
    for cand in "/sys/fs/cgroup/system.slice/docker-${cid}.scope" "/sys/fs/cgroup/docker/${cid}"; do
        if [ -f "${cand}/memory.current" ] && [ -f "${cand}/cpu.stat" ]; then
            found="$cand"
            break
        fi
    done
    if [ -z "$found" ]; then
        CG_MODE="fallback"
    else
        CG_DIR[$key]="$found"
    fi
done

if [ "$CG_MODE" = "fallback" ]; then
    echo "# fallback: docker stats, 실효 간격 ~5s"
fi

# cgroup CPU%, /proc/stat steal% 은 델타 기반이라 이전 샘플 값을 들고 있어야
# 한다. 첫 샘플은 델타가 없으므로 0으로 낸다.
declare -A PREV_USEC
PREV_STAT_TOTAL=""
PREV_STAT_STEAL=""

while true; do
    start_ms=$(date +%s%3N)
    ts="$start_ms"

    backend_cpu=0
    backend_mem=0
    mysql_cpu=0
    mysql_mem=0
    redis_cpu=0
    redis_mem=0

    if [ "$CG_MODE" = "cgroup" ]; then
        for key in backend mysql redis; do
            dir="${CG_DIR[$key]}"
            mem_bytes=$(cat "${dir}/memory.current" 2>/dev/null)
            mem_bytes=${mem_bytes:-0}
            mem_mb=$(awk -v b="$mem_bytes" 'BEGIN{printf "%.1f", b/1024/1024}')

            usec=$(awk '/^usage_usec/{print $2}' "${dir}/cpu.stat" 2>/dev/null)
            usec=${usec:-0}
            prev_usec="${PREV_USEC[$key]:-}"
            if [ -n "$prev_usec" ]; then
                elapsed_ms=$((start_ms - PREV_LOOP_TS_MS))
                if [ "$elapsed_ms" -gt 0 ]; then
                    cpu_pct=$(awk -v u="$usec" -v pu="$prev_usec" -v ms="$elapsed_ms" \
                        'BEGIN{printf "%.1f", (u-pu)/(ms*1000)*100}')
                else
                    cpu_pct="0.0"
                fi
            else
                cpu_pct="0.0"
            fi
            PREV_USEC[$key]="$usec"

            case "$key" in
                backend)
                    backend_cpu="$cpu_pct"
                    backend_mem="$mem_mb"
                    ;;
                mysql)
                    mysql_cpu="$cpu_pct"
                    mysql_mem="$mem_mb"
                    ;;
                redis)
                    redis_cpu="$cpu_pct"
                    redis_mem="$mem_mb"
                    ;;
            esac
        done
    else
        stats=$(docker stats --no-stream --format '{{.Name}};{{.CPUPerc}};{{.MemUsage}}' groove-backend groove-mysql groove-redis 2>/dev/null || true)
        while IFS=';' read -r name cpu mem; do
            [ -z "$name" ] && continue
            cpu_num=$(echo "$cpu" | tr -d '%')
            mem_used=$(echo "$mem" | awk -F'/' '{print $1}' | xargs)
            case "$mem_used" in
                *GiB)
                    mem_mb=$(echo "$mem_used" | sed 's/GiB//' | awk '{printf "%.1f", $1*1024}')
                    ;;
                *MiB)
                    mem_mb=$(echo "$mem_used" | sed 's/MiB//')
                    ;;
                *)
                    mem_mb=0
                    ;;
            esac
            case "$name" in
                groove-backend)
                    backend_cpu="$cpu_num"
                    backend_mem="$mem_mb"
                    ;;
                groove-mysql)
                    mysql_cpu="$cpu_num"
                    mysql_mem="$mem_mb"
                    ;;
                groove-redis)
                    redis_cpu="$cpu_num"
                    redis_mem="$mem_mb"
                    ;;
            esac
        done <<EOF_STATS
$stats
EOF_STATS
    fi

    mem_available_mb=$(awk '/^MemAvailable:/{printf "%.0f", $2/1024}' /proc/meminfo)
    swap_total_kb=$(awk '/^SwapTotal:/{print $2}' /proc/meminfo)
    swap_free_kb=$(awk '/^SwapFree:/{print $2}' /proc/meminfo)
    swap_used_mb=$(awk -v t="${swap_total_kb:-0}" -v f="${swap_free_kb:-0}" 'BEGIN{printf "%.0f", (t-f)/1024}')

    # /proc/stat 1행: cpu user nice system idle iowait irq softirq steal guest guest_nice
    # steal 은 8번째 숫자 필드(guest/guest_nice 는 total 에서 뺀다 — user 에 이미 포함돼 이중 계산됨).
    read -r _ stat_user stat_nice stat_system stat_idle stat_iowait stat_irq stat_softirq stat_steal _ _ < /proc/stat
    stat_total=$((stat_user + stat_nice + stat_system + stat_idle + stat_iowait + stat_irq + stat_softirq + stat_steal))
    if [ -n "$PREV_STAT_TOTAL" ]; then
        total_delta=$((stat_total - PREV_STAT_TOTAL))
        steal_delta=$((stat_steal - PREV_STAT_STEAL))
        if [ "$total_delta" -gt 0 ]; then
            cpu_steal_pct=$(awk -v s="$steal_delta" -v t="$total_delta" 'BEGIN{printf "%.2f", s/t*100}')
        else
            cpu_steal_pct="0.00"
        fi
    else
        cpu_steal_pct="0.00"
    fi
    PREV_STAT_TOTAL="$stat_total"
    PREV_STAT_STEAL="$stat_steal"

    # /proc/net/sockstat 은 ss -s 보다 훨씬 싸다(커널이 이미 집계해둔 카운터를 그냥 읽는 것).
    sockstat_tcp=$(awk '/^TCP:/{print}' /proc/net/sockstat)
    tcp_estab=$(echo "$sockstat_tcp" | grep -oP 'inuse \K[0-9]+')
    tcp_estab=${tcp_estab:-0}
    tcp_timewait=$(echo "$sockstat_tcp" | grep -oP 'tw \K[0-9]+')
    tcp_timewait=${tcp_timewait:-0}

    echo "${ts},${backend_cpu},${backend_mem},${mysql_cpu},${mysql_mem},${redis_cpu},${redis_mem},${mem_available_mb},${swap_used_mb},${cpu_steal_pct},${tcp_estab},${tcp_timewait}"

    PREV_LOOP_TS_MS="$start_ms"

    end_ms=$(date +%s%3N)
    elapsed_ms=$((end_ms - start_ms))
    interval_ms=$(awk -v i="$INTERVAL" 'BEGIN{printf "%.0f", i*1000}')
    sleep_ms=$((interval_ms - elapsed_ms))
    if [ "$sleep_ms" -gt 0 ]; then
        sleep "$(awk -v ms="$sleep_ms" 'BEGIN{printf "%.3f", ms/1000}')"
    fi
done
REMOTE_SCRIPT
}

# 카운터형 지표(로그 이후 계속 누적되는 값)를 key=value 로 원격에서 긁어온다.
# start 시 pre-check 로, stop 시 post-check 델타 계산의 after 값으로 재사용한다.
# grep -c 는 매치 0건이면 "0"을 출력하면서도 exit 1 을 반환하므로, "|| echo 기본값"
# 을 뒤에 붙이면 0 이 정상 출력된 뒤에 기본값 라인까지 한 번 더 찍히는 중복이
# 난다. 그래서 항상 변수에 먼저 담고 빈 값일 때만 기본값을 채운다.
collect_counters_remote_script() {
    cat <<'REMOTE_SCRIPT'
nginx_5xx=$(sudo awk '$9 ~ /^5/ {c++} END {print c+0}' /var/log/nginx/access.log 2>/dev/null)
nginx_5xx=${nginx_5xx:-0}

nginx_worker_conn_warn=$(sudo grep -c 'worker_connections are not enough' /var/log/nginx/error.log 2>/dev/null)
nginx_worker_conn_warn=${nginx_worker_conn_warn:-0}

redis_evicted_keys=$(docker exec groove-redis redis-cli INFO stats 2>/dev/null | awk -F: '/^evicted_keys/{gsub("\r","",$2); print $2}')
redis_evicted_keys=${redis_evicted_keys:-0}

restart_backend=$(docker inspect --format '{{.RestartCount}}' groove-backend 2>/dev/null)
restart_backend=${restart_backend:-0}
restart_mysql=$(docker inspect --format '{{.RestartCount}}' groove-mysql 2>/dev/null)
restart_mysql=${restart_mysql:-0}
restart_redis=$(docker inspect --format '{{.RestartCount}}' groove-redis 2>/dev/null)
restart_redis=${restart_redis:-0}

nginx_access_lines=$(sudo wc -l < /var/log/nginx/access.log 2>/dev/null)
nginx_access_lines=${nginx_access_lines:-0}

echo "nginx_5xx=${nginx_5xx}"
echo "nginx_worker_conn_warn=${nginx_worker_conn_warn}"
echo "redis_evicted_keys=${redis_evicted_keys}"
echo "restart_backend=${restart_backend}"
echo "restart_mysql=${restart_mysql}"
echo "restart_redis=${restart_redis}"
echo "nginx_access_lines=${nginx_access_lines}"
REMOTE_SCRIPT
}

collect_counters() {
    # EOF 를 일부러 언쿼팅한다 — collect_counters_remote_script 의 결과를 로컬에서
    # 먼저 치환해 원격으로 보내는 스크립트 본문 자체를 구성하는 것이라, 여기서
    # 로컬 확장이 일어나지 않으면 스크립트 본문이 아예 비어버린다.
    # shellcheck disable=SC2087
    ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" bash -s <<EOF
$(collect_counters_remote_script)
EOF
}

do_start() {
    local out_dir="$1"
    local interval="1"
    shift
    while [ $# -gt 0 ]; do
        case "$1" in
            --interval)
                interval="$2"
                shift 2
                ;;
            *)
                echo "알 수 없는 옵션: $1" >&2
                usage
                exit 2
                ;;
        esac
    done

    mkdir -p "$out_dir"
    local pid_file="${out_dir}/monitor.pid"
    local csv_file="${out_dir}/resources.csv"
    local err_file="${out_dir}/monitor.err.log"
    local pre_file="${out_dir}/pre-check.txt"

    if [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
        echo "이미 모니터가 돌고 있음 (pid $(cat "$pid_file")). 먼저 stop 하세요." >&2
        exit 1
    fi

    # 누적 카운터의 "이번 실행분" 델타를 stop 에서 계산하려면 시작 시점 값이 필요하다.
    echo "사전 1회 수집 중..."
    collect_counters > "$pre_file" 2>&1 || echo "사전 수집 실패(네트워크 등) — 델타 없이 진행" >&2

    {
        echo "# ts는 밀리초(ms) epoch"
        echo "ts,backend_cpu_pct,backend_mem_mb,mysql_cpu_pct,mysql_mem_mb,redis_cpu_pct,redis_mem_mb,mem_available_mb,swap_used_mb,cpu_steal_pct,tcp_estab,tcp_timewait"
    } > "$csv_file"

    remote_loop_script | ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" bash -s -- "$interval" "$REMOTE_TAG" \
        >> "$csv_file" 2>> "$err_file" &
    local pid=$!
    echo "$pid" > "$pid_file"

    sleep 1
    if ! kill -0 "$pid" 2>/dev/null; then
        echo "모니터 시작 실패. ${err_file} 확인." >&2
        rm -f "$pid_file"
        exit 1
    fi

    echo "모니터 시작함 (pid $pid, 간격 ${interval}초). CSV: ${csv_file}"
}

do_stop() {
    local out_dir="$1"
    local pid_file="${out_dir}/monitor.pid"

    if [ ! -f "$pid_file" ]; then
        echo "모니터가 안 돌고 있음 (${pid_file} 없음)." >&2
        exit 1
    fi

    local pid
    pid=$(cat "$pid_file")

    if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
    fi
    rm -f "$pid_file"

    # 로컬 SSH 프로세스를 죽여도 원격 루프가 다음 write 에서야 SIGPIPE 로
    # 죽을 수 있으니, 태그로 원격 프로세스를 직접 정리한다.
    ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" "pkill -f '${REMOTE_TAG}' || true" 2>/dev/null || true

    echo "모니터 정지함."

    echo "사후 1회 수집 중..."
    post_check "$out_dir"
}

# key=value 형식 텍스트($1)에서 $2 키의 값을 뽑는다. 없으면 빈 문자열.
counter_value() {
    local text="$1"
    local key="$2"
    echo "$text" | awk -F= -v k="$key" '$1==k{print $2; found=1} END{if(!found) print ""}'
}

post_check() {
    local out_dir="$1"
    local post_file="${out_dir}/post-check.txt"
    local pre_file="${out_dir}/pre-check.txt"

    local after_counters
    after_counters=$(collect_counters 2>&1 || true)

    {
        echo "=== 이번 실행 증가분(델타) ==="
        if [ -f "$pre_file" ]; then
            local pre_counters
            pre_counters=$(cat "$pre_file")
            for label_key in "Nginx 5xx:nginx_5xx" "worker_connections 부족 경고:nginx_worker_conn_warn" \
                "Redis evicted_keys:redis_evicted_keys" "backend RestartCount:restart_backend" \
                "mysql RestartCount:restart_mysql" "redis RestartCount:restart_redis" \
                "nginx access.log 라인 수:nginx_access_lines"; do
                local label="${label_key%%:*}"
                local key="${label_key##*:}"
                local before after delta
                before=$(counter_value "$pre_counters" "$key")
                after=$(counter_value "$after_counters" "$key")
                if [ -z "$before" ] || [ -z "$after" ]; then
                    echo "${label}: 조회 실패(사전 또는 사후 값 없음)"
                    continue
                fi
                delta=$((after - before))
                echo "${label}: ${before} -> ${after} (+${delta})"
            done
        else
            echo "사전 수집 파일(${pre_file}) 없음 — start 를 거치지 않고 stop 만 호출했을 가능성. 델타 생략."
        fi
    } > "$post_file"

    ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" bash -s <<'REMOTE_SCRIPT' >> "$post_file" 2>&1

echo
echo "=== 컨테이너 상태 ==="
for c in groove-backend groove-mysql groove-redis; do
    docker inspect --format '{{.Name}} OOMKilled={{.State.OOMKilled}} Restarts={{.RestartCount}} Status={{.State.Status}} StartedAt={{.State.StartedAt}}' "$c" 2>/dev/null || echo "$c 조회 실패"
done

echo
echo "=== Nginx worker_connections 부족 경고 건수(누적) ==="
warn_count=$(sudo grep -c 'worker_connections are not enough' /var/log/nginx/error.log 2>/dev/null)
echo "${warn_count:-0}"

echo
echo "=== Nginx error.log 마지막 20줄 ==="
sudo tail -20 /var/log/nginx/error.log 2>/dev/null || echo "조회 실패"

echo
echo "=== Nginx access.log 5xx 건수(누적) ==="
xx_count=$(sudo awk '$9 ~ /^5/ {c++} END {print c+0}' /var/log/nginx/access.log 2>/dev/null)
echo "${xx_count:-0}"

echo
echo "=== Redis 통계 ==="
docker exec groove-redis redis-cli INFO stats 2>/dev/null | grep -E 'evicted_keys|keyspace_(hits|misses)' || echo "조회 실패"
docker exec groove-redis redis-cli INFO memory 2>/dev/null | grep -E 'used_memory_human|maxmemory_human' || echo "조회 실패"

echo
echo "=== dmesg OOM 흔적 (마지막 20줄) ==="
dmesg 2>/dev/null | tail -20 || true

echo
echo "=== 백엔드 로그 ERROR/Exception (마지막 200줄 중) ==="
docker logs --tail 200 groove-backend 2>&1 | grep -iE 'error|exception' | tail -30 || true
REMOTE_SCRIPT

    echo "사후 수집 완료: ${post_file}"
}

do_snapshot() {
    local out_dir="$1"
    local label="$2"

    mkdir -p "$out_dir"
    local snap_file="${out_dir}/snapshot-${label}.txt"

    {
        echo "=== 원격: free -m ==="
        ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" 'free -m' 2>&1

        echo
        echo "=== 원격: docker stats --no-stream ==="
        ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" 'docker stats --no-stream' 2>&1

        echo
        echo "=== 원격: uptime ==="
        ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" 'uptime' 2>&1

        echo
        echo "=== 원격: redis INFO memory ==="
        ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" 'docker exec groove-redis redis-cli INFO memory' 2>&1

        echo
        echo "=== 로컬: /api/v1/health RTT (3회) ==="
        for i in 1 2 3; do
            curl -o /dev/null -s -w "시도${i}: dns=%{time_namelookup} conn=%{time_connect} tls=%{time_appconnect} ttfb=%{time_starttransfer} total=%{time_total}\n" \
                "https://groove-lp.duckdns.org/api/v1/health" 2>&1 || echo "시도${i}: curl 실패"
        done
    } > "$snap_file"

    echo "스냅샷 저장함: ${snap_file}"
}

main() {
    if [ $# -lt 1 ]; then
        usage
        exit 2
    fi

    local cmd="$1"
    shift

    case "$cmd" in
        start)
            if [ $# -lt 1 ]; then
                usage
                exit 2
            fi
            do_start "$@"
            ;;
        stop)
            if [ $# -lt 1 ]; then
                usage
                exit 2
            fi
            do_stop "$1"
            ;;
        snapshot)
            if [ $# -lt 2 ]; then
                usage
                exit 2
            fi
            do_snapshot "$1" "$2"
            ;;
        *)
            usage
            exit 2
            ;;
    esac
}

main "$@"
