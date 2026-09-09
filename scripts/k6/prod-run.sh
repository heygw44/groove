#!/usr/bin/env bash
# 운영 도메인에 대고 한정반 선착순 구매 부하 테스트를 VU 를 단계적으로 올리며 돌린다.
# 목적은 좋은 수치가 아니라 어디서 무너지는지를 안전하게 찾아 기록하는 것이다.
# 그래서 헬스체크가 실패하면 더 큰 VU 로 밀지 않고 그 자리에서 멈춘다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

VU_STAGES="50 200 500 1000"
STOCK=100
BASE_URL="https://groove-lp.duckdns.org"
OUT_DIR=""
COOLDOWN=60
NO_MONITOR=0
MEMBER_EMAIL_PREFIX="${MEMBER_EMAIL_PREFIX:-k6lt-}"
PRODUCT_TITLE_PREFIX="${PRODUCT_TITLE_PREFIX:-LIMITED-LOADTEST-}"
# t3.micro 는 setup 의 BCrypt 동시성이 곧 부하다. 회원 준비 청크를 로컬 기본(20)보다 낮춘다.
SETUP_BATCH_SIZE="${SETUP_BATCH_SIZE:-10}"

usage() {
	cat <<EOF
사용법: $(basename "$0") [옵션]
  --vus "50 200 500 1000"     단계 목록 (기본값)
  --stock 100                 단계마다 고정할 한정반 재고 (기본 100)
  --base-url <url>            (기본 ${BASE_URL})
  --out <디렉토리>            기본 scripts/k6/results/prod-<YYYYMMDD-HHmmss>
  --cooldown 60               단계 사이 대기 초 (기본 60)
  --smoke                     --vus "10" --stock 3 과 동등한 스모크 모드
  --no-monitor                원격 모니터링 생략

환경변수(필수): ADMIN_EMAIL, ADMIN_PASSWORD (운영 관리자 자격증명이라 기본값을 두지 않는다)
환경변수(선택): MEMBER_EMAIL_PREFIX(기본 k6lt-), PRODUCT_TITLE_PREFIX(기본 LIMITED-LOADTEST-)
EOF
}

while [ $# -gt 0 ]; do
	case "$1" in
		--vus)
			VU_STAGES="$2"
			shift 2
			;;
		--stock)
			STOCK="$2"
			shift 2
			;;
		--base-url)
			BASE_URL="$2"
			shift 2
			;;
		--out)
			OUT_DIR="$2"
			shift 2
			;;
		--cooldown)
			COOLDOWN="$2"
			shift 2
			;;
		--smoke)
			VU_STAGES="10"
			STOCK=3
			shift
			;;
		--no-monitor)
			NO_MONITOR=1
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "알 수 없는 옵션: $1" >&2
			usage
			exit 2
			;;
	esac
done

# 자격증명을 셸 히스토리에 남기지 않도록, 있으면 로컬 전용 env 파일에서 읽는다(gitignore 대상).
ENV_FILE="${ENV_FILE:-${SCRIPT_DIR}/.env.prod}"
if [ -f "$ENV_FILE" ] && { [ -z "${ADMIN_EMAIL:-}" ] || [ -z "${ADMIN_PASSWORD:-}" ]; }; then
	# shellcheck disable=SC1090
	. "$ENV_FILE"
fi

if [ -z "${ADMIN_EMAIL:-}" ] || [ -z "${ADMIN_PASSWORD:-}" ]; then
	echo "ADMIN_EMAIL, ADMIN_PASSWORD 환경변수가 필요하다 (운영 관리자 자격증명)." >&2
	usage
	exit 2
fi

if [ -z "$OUT_DIR" ]; then
	OUT_DIR="${REPO_ROOT}/scripts/k6/results/prod-$(date +%Y%m%d-%H%M%S)"
fi
mkdir -p "$OUT_DIR"

RUN_LOG="${OUT_DIR}/run.log"
SUMMARY_TSV="${OUT_DIR}/summary.tsv"
DROP_IDS_FILE="${OUT_DIR}/drop-ids.txt"
MONITOR_SCRIPT="${SCRIPT_DIR}/monitor-remote.sh"
VERIFY_SCRIPT="${SCRIPT_DIR}/verify-oversell.sh"
K6_SCRIPT="${SCRIPT_DIR}/limited-purchase.js"

log() {
	echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$RUN_LOG"
}

printf 'vu\tstock\texit_code\tstatus\tp50_ms\tp95_ms\tp99_ms\twaiting_p95_ms\treq_total\tfail_rate\trps\tsuccess_201\tsold_out_409\tserver_error\n' > "$SUMMARY_TSV"

# base 에 대해 curl -w 로 RTT 만이라도 남긴다(monitor-remote.sh 의 snapshot 이 없을 때 대체용).
local_baseline() {
	local label="$1"
	local file="${OUT_DIR}/baseline-${label}.txt"
	{
		echo "# local curl RTT baseline (${label}), $(date '+%Y-%m-%d %H:%M:%S')"
		for _ in 1 2 3; do
			curl -s -o /dev/null -w "dns=%{time_namelookup} conn=%{time_connect} tls=%{time_appconnect} ttfb=%{time_starttransfer} total=%{time_total}\n" \
				-m 10 "${BASE_URL}/api/v1/health" || echo "요청 실패"
		done
	} > "$file"
}

snapshot_baseline() {
	local label="$1"
	if [ "$NO_MONITOR" -eq 1 ]; then
		return
	fi
	if [ -x "$MONITOR_SCRIPT" ]; then
		"$MONITOR_SCRIPT" snapshot "$OUT_DIR" "$label" >> "$RUN_LOG" 2>&1 || log "monitor-remote.sh snapshot ${label} 실패, 로컬 baseline 으로 대체"
	fi
	local_baseline "$label"
}

health_check() {
	curl -fsS -m 10 "${BASE_URL}/api/v1/health" > /dev/null 2>&1
}

# 최대 6회, 5초 간격으로 재시도. 실패하면 1, 성공하면 0 을 반환.
health_check_with_retry() {
	local attempt
	for attempt in 1 2 3 4 5 6; do
		if health_check; then
			return 0
		fi
		log "헬스체크 실패 (시도 ${attempt}/6), 5초 후 재시도"
		sleep 5
	done
	return 1
}

# summary.tsv 한 단계 요약을 채운다. 파싱 실패는 죽지 않고 빈 값으로 남긴다(방어적 파싱).
append_summary_row() {
	local vu="$1" status="$2" exit_code="$3" json_file="$4"
	local p50="" p95="" p99="" waiting_p95="" req_total="" fail_rate="" rps=""
	local success_201="" sold_out_409="" server_error=""

	if [ -n "$json_file" ] && [ -f "$json_file" ]; then
		if command -v jq > /dev/null 2>&1; then
			# 태그별 서브메트릭(http_req_duration{name:purchase})이 있으면 우선 사용.
			local dur_key='"http_req_duration"'
			if jq -e '.metrics["http_req_duration{name:purchase}"]' "$json_file" > /dev/null 2>&1; then
				dur_key='"http_req_duration{name:purchase}"'
			fi
			p50=$(jq -r "(.metrics[${dur_key}].values.med) // \"\"" "$json_file" 2>/dev/null || echo "")
			p95=$(jq -r "(.metrics[${dur_key}].values[\"p(95)\"]) // \"\"" "$json_file" 2>/dev/null || echo "")
			p99=$(jq -r "(.metrics[${dur_key}].values[\"p(99)\"]) // \"\"" "$json_file" 2>/dev/null || echo "")
			waiting_p95=$(jq -r '(.metrics["http_req_waiting"].values["p(95)"]) // ""' "$json_file" 2>/dev/null || echo "")
			req_total=$(jq -r '(.metrics["http_reqs"].values.count) // ""' "$json_file" 2>/dev/null || echo "")
			fail_rate=$(jq -r '(.metrics["http_req_failed"].values.rate) // ""' "$json_file" 2>/dev/null || echo "")
			rps=$(jq -r '(.metrics["http_reqs"].values.rate) // ""' "$json_file" 2>/dev/null || echo "")
			success_201=$(jq -r '(.metrics["purchase_success"].values.count) // ""' "$json_file" 2>/dev/null || echo "")
			sold_out_409=$(jq -r '(.metrics["purchase_sold_out"].values.count) // ""' "$json_file" 2>/dev/null || echo "")
			server_error=$(jq -r '(.metrics["purchase_server_error"].values.count) // ""' "$json_file" 2>/dev/null || echo "")
		elif command -v python3 > /dev/null 2>&1; then
			read -r p50 p95 p99 waiting_p95 req_total fail_rate rps success_201 sold_out_409 server_error <<PYOUT
$(python3 - "$json_file" <<'PYEOF' 2>/dev/null
import json
import sys

with open(sys.argv[1]) as f:
    data = json.load(f)

metrics = data.get("metrics", {})
dur_key = "http_req_duration{name:purchase}" if "http_req_duration{name:purchase}" in metrics else "http_req_duration"


def get(key, path, default=""):
    node = metrics.get(key, {})
    values = node.get("values", {})
    val = values
    for part in path:
        if not isinstance(val, dict) or part not in val:
            return default
        val = val[part]
    return val if val is not None else default


fields = [
    get(dur_key, ["med"]),
    get(dur_key, ["p(95)"]),
    get(dur_key, ["p(99)"]),
    get("http_req_waiting", ["p(95)"]),
    get("http_reqs", ["count"]),
    get("http_req_failed", ["rate"]),
    get("http_reqs", ["rate"]),
    get("purchase_success", ["count"]),
    get("purchase_sold_out", ["count"]),
    get("purchase_server_error", ["count"]),
]
print(" ".join(str(x) if x != "" else "-" for x in fields))
PYEOF
)
PYOUT
			[ "$p50" = "-" ] && p50=""
			[ "$p95" = "-" ] && p95=""
			[ "$p99" = "-" ] && p99=""
			[ "$waiting_p95" = "-" ] && waiting_p95=""
			[ "$req_total" = "-" ] && req_total=""
			[ "$fail_rate" = "-" ] && fail_rate=""
			[ "$rps" = "-" ] && rps=""
			[ "$success_201" = "-" ] && success_201=""
			[ "$sold_out_409" = "-" ] && sold_out_409=""
			[ "$server_error" = "-" ] && server_error=""
		else
			log "jq 도 python3 도 없어 summary.tsv 값을 채우지 못함 (${json_file})"
		fi
	fi

	printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
		"$vu" "$STOCK" "$exit_code" "$status" "$p50" "$p95" "$p99" "$waiting_p95" \
		"$req_total" "$fail_rate" "$rps" "$success_201" "$sold_out_409" "$server_error" >> "$SUMMARY_TSV"
}

print_final_summary() {
	log "=== 최종 요약 ==="
	if command -v column > /dev/null 2>&1; then
		column -t -s $'\t' "$SUMMARY_TSV"
	else
		cat "$SUMMARY_TSV"
	fi

	local drop_ids_csv=""
	if [ -f "$DROP_IDS_FILE" ]; then
		drop_ids_csv=$(tr '\n' ',' < "$DROP_IDS_FILE" | sed 's/,$//')
	fi

	echo
	echo "결과 디렉토리: ${OUT_DIR}"
	echo "정리 명령 (dry-run 먼저):"
	echo "  ${SCRIPT_DIR}/cleanup-prod-loadtest.sh --drop-ids \"${drop_ids_csv}\""
	echo "  ${SCRIPT_DIR}/cleanup-prod-loadtest.sh --drop-ids \"${drop_ids_csv}\" --apply"
	if [ "${SERVICE_DOWN:-0}" -eq 1 ]; then
		echo
		echo "서비스가 죽은 채로 종료됨. 복구 명령:"
		echo "  ssh -i ~/.ssh/groove-key.pem ubuntu@52.78.95.139 'cd /opt/groove && docker compose -f docker-compose.prod.yml up -d'"
	fi
}

log "=== prod-run 시작: base=${BASE_URL} stages=[${VU_STAGES}] stock=${STOCK} out=${OUT_DIR} ==="
SERVICE_DOWN=0

snapshot_baseline "before"

for vu in $VU_STAGES; do
	stage_label=$(printf 'vu-%03d' "$vu")
	stage_dir="${OUT_DIR}/${stage_label}"
	mkdir -p "$stage_dir"

	log "--- 단계 시작: VU=${vu} STOCK=${STOCK} ---"

	if ! health_check_with_retry; then
		log "사전 헬스체크 실패, 여기서 멈춤 (VU=${vu})"
		append_summary_row "$vu" "DOWN_BEFORE" "" ""
		SERVICE_DOWN=1
		break
	fi

	if [ "$NO_MONITOR" -eq 0 ]; then
		if [ -x "$MONITOR_SCRIPT" ]; then
			"$MONITOR_SCRIPT" start "$stage_dir" >> "$RUN_LOG" 2>&1 || log "monitor-remote.sh start 실패, 모니터 없이 진행"
		else
			log "monitor-remote.sh 가 아직 없어 모니터 없이 진행"
		fi
	fi

	k6_stdout="${stage_dir}/k6-stdout.log"
	exit_code_file="${stage_dir}/exit-code.txt"

	log "k6 실행 시작 (VU=${vu})"
	set +e
	BASE_URL="$BASE_URL" VUS="$vu" STOCK="$STOCK" RUN_LABEL="$stage_label" \
		MEMBER_EMAIL_PREFIX="$MEMBER_EMAIL_PREFIX" PRODUCT_TITLE_PREFIX="$PRODUCT_TITLE_PREFIX" \
		ADMIN_EMAIL="$ADMIN_EMAIL" ADMIN_PASSWORD="$ADMIN_PASSWORD" \
		SETUP_BATCH_SIZE="$SETUP_BATCH_SIZE" RESULT_DIR="$stage_dir" \
		k6 run "$K6_SCRIPT" 2>&1 | tee "$k6_stdout"
	k6_exit=${PIPESTATUS[0]}
	set -e
	echo "$k6_exit" > "$exit_code_file"
	log "k6 실행 종료 (VU=${vu}, exit=${k6_exit})"

	status="OK"
	if [ "$k6_exit" -eq 99 ]; then
		status="THRESHOLD_FAIL"
	elif [ "$k6_exit" -ne 0 ]; then
		status="ERROR"
	fi

	# k6 가 남긴 JSON 요약 파일 하나를 stage_dir 에서 찾는다(RUN_LABEL 로 이름이 고정된다).
	json_file=$(find "$stage_dir" -maxdepth 1 -name "limited-${stage_label}-*.json" | head -n1 || true)

	# 판정은 쿨다운 전에 즉시 한다: 한정반 구매는 PENDING 주문이라 만료 스케줄러가
	# 10분 뒤 주문을 취소하며 limited_purchase 행을 지우고 재고를 되돌린다. 늦게
	# 판정하면 초과 판매 증거 자체가 사라지므로 여기서 바로 verify 를 호출한다.
	drop_id=""
	product_id=""
	if [ -n "$k6_stdout" ] && [ -f "$k6_stdout" ]; then
		parsed_line=$(grep -oE '(setup done|teardown): dropId=[0-9]+ productId=[0-9]+' "$k6_stdout" | tail -n1 || true)
		if [ -n "$parsed_line" ]; then
			drop_id=$(echo "$parsed_line" | grep -oE 'dropId=[0-9]+' | head -n1 | cut -d= -f2)
			product_id=$(echo "$parsed_line" | grep -oE 'productId=[0-9]+' | head -n1 | cut -d= -f2)
		fi
	fi

	if [ -n "$drop_id" ] && [ -n "$product_id" ]; then
		echo "$drop_id" >> "$DROP_IDS_FILE"
		if [ -x "$VERIFY_SCRIPT" ]; then
			"$VERIFY_SCRIPT" "$drop_id" "$product_id" "${stage_dir}/verify.txt" >> "$RUN_LOG" 2>&1 || log "verify-oversell.sh 실패 (dropId=${drop_id})"
		else
			log "verify-oversell.sh 가 아직 없어 판정을 건너뜀 (dropId=${drop_id}, productId=${product_id})"
		fi
	else
		log "k6 로그에서 dropId/productId 파싱 실패, verify 건너뜀"
	fi

	if [ "$NO_MONITOR" -eq 0 ] && [ -x "$MONITOR_SCRIPT" ]; then
		"$MONITOR_SCRIPT" stop "$stage_dir" >> "$RUN_LOG" 2>&1 || log "monitor-remote.sh stop 실패"
	fi

	if ! health_check_with_retry; then
		log "사후 헬스체크 실패, 여기서 멈춤 (VU=${vu})"
		append_summary_row "$vu" "DOWN_AFTER" "$k6_exit" "$json_file"
		SERVICE_DOWN=1
		break
	fi

	append_summary_row "$vu" "$status" "$k6_exit" "$json_file"
	log "--- 단계 종료: VU=${vu} status=${status} ---"

	is_last_stage=0
	last_vu=$(echo "$VU_STAGES" | awk '{print $NF}')
	[ "$vu" = "$last_vu" ] && is_last_stage=1

	if [ "$is_last_stage" -eq 0 ]; then
		log "쿨다운 ${COOLDOWN}초"
		sleep "$COOLDOWN"
	fi
done

snapshot_baseline "after"

print_final_summary
log "=== prod-run 종료 ==="
