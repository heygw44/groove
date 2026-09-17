#!/usr/bin/env bash
# 한정반 초과판매 여부를 원격(운영) 또는 로컬 DB/Redis 상태로 판정한다.
#
# 중요한 제약: 한정반 구매는 PENDING 주문이고 OrderExpirationScheduler 가 60초마다
# 돌며 10분 지난 주문을 취소하는데, 그때 limited_purchase 행이 삭제되고 재고가
# 복구된다. 즉 이 판정은 첫 구매(limited_purchase.created_at) 로부터 10분 안에만
# 유효하다. 10분이 넘었으면 결과가 이미 취소 복구로 오염됐을 수 있다.
# shellcheck disable=SC2086 # SSH_OPTS 는 여러 -o 플래그를 담는 문자열이라 의도적으로 언쿼팅
set -euo pipefail

SSH_KEY="${SSH_KEY:-}"
SSH_HOST="${SSH_HOST:-}"
SSH_OPTS="${SSH_OPTS:--o ConnectTimeout=8 -o BatchMode=yes}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-groove-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-groove-redis}"

usage() {
	cat <<EOF
사용법: $(basename "$0") [--local] [--chaos] <dropId> <productId> [출력파일]

  --local  SSH 대신 로컬 docker 컨테이너(MYSQL_CONTAINER/REDIS_CONTAINER)로 조회한다.
  --chaos  카오스 테스트용 판정 항목(7종)을 쓴다. 기본은 표준 4항목.

환경변수: SSH_KEY(필수, pem 경로), SSH_HOST(필수, 예: ubuntu@<EC2-IP>), SSH_OPTS - --local 없을 때 사용
          MYSQL_CONTAINER(기본 groove-mysql), REDIS_CONTAINER(기본 groove-redis) - --local 에서 사용
EOF
}

LOCAL_MODE=0
CHAOS_MODE=0
POSITIONAL=()
while [ $# -gt 0 ]; do
	case "$1" in
		--local)
			LOCAL_MODE=1
			shift
			;;
		--chaos)
			CHAOS_MODE=1
			shift
			;;
		-*)
			echo "알 수 없는 옵션: $1" >&2
			usage
			exit 2
			;;
		*)
			POSITIONAL+=("$1")
			shift
			;;
	esac
done

if [ "${#POSITIONAL[@]}" -lt 2 ]; then
	usage
	exit 2
fi

DROP_ID="${POSITIONAL[0]}"
PRODUCT_ID="${POSITIONAL[1]}"
OUT_FILE="${POSITIONAL[2]:-}"

if [ "$LOCAL_MODE" != "1" ]; then
    : "${SSH_HOST:?SSH_HOST(예: ubuntu@<EC2-IP>)를 지정하세요}"
    : "${SSH_KEY:?SSH_KEY(pem 경로)를 지정하세요}"
fi

MODE_LABEL="remote"
[ "$LOCAL_MODE" = "1" ] && MODE_LABEL="local"
VERDICT_LABEL="standard"
[ "$CHAOS_MODE" = "1" ] && VERDICT_LABEL="chaos"

echo "주의: 한정반 구매는 PENDING 상태로 10분 뒤 만료 스케줄러가 limited_purchase 를"
echo "      삭제하고 재고를 복구한다. 이 판정은 첫 구매로부터 10분 이내에만 유효하다."
echo

REMOTE_SCRIPT=$(cat <<'REMOTE_SCRIPT_EOF'
set -uo pipefail
DROP_ID="$1"
PRODUCT_ID="$2"
MYSQL_CONTAINER="${3:-groove-mysql}"
REDIS_CONTAINER="${4:-groove-redis}"

run_sql() {
	local sql="$1"
	# -i 를 주면 원격 스크립트의 stdin(ssh 채널)을 docker 가 먹어 나머지 명령이 사라진다. SQL 은 -e 로만 넘긴다.
	docker exec "$MYSQL_CONTAINER" sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot -N -B \"\$MYSQL_DATABASE\" -e \"${sql}\"" </dev/null
}

run_redis() {
	docker exec "$REDIS_CONTAINER" redis-cli "$@" 2>/dev/null || echo ""
}

purchase_count=$(run_sql "SELECT COUNT(*) FROM limited_purchase WHERE drop_id=${DROP_ID}")
echo "PURCHASE_COUNT=${purchase_count}"

stock_qty=$(run_sql "SELECT quantity FROM stock WHERE product_id=${PRODUCT_ID}")
echo "STOCK_QTY=${stock_qty}"

drop_row=$(run_sql "SELECT total_quantity, sold_count, status, IFNULL(sold_out_at, 'NULL') FROM limited_drop WHERE id=${DROP_ID}")
echo "DROP_TOTAL_QTY=$(echo "$drop_row" | cut -f1)"
echo "DROP_SOLD_COUNT=$(echo "$drop_row" | cut -f2)"
echo "DROP_STATUS=$(echo "$drop_row" | cut -f3)"
echo "DROP_SOLD_OUT_AT=$(echo "$drop_row" | cut -f4)"

order_status_rows=$(run_sql "SELECT status, COUNT(*) FROM orders o JOIN order_item oi ON oi.order_id=o.id WHERE oi.product_id=${PRODUCT_ID} GROUP BY status")
while IFS=$'\t' read -r status cnt; do
	[ -z "$status" ] && continue
	echo "ORDER_STATUS:${status}=${cnt}"
done <<< "$order_status_rows"

time_row=$(run_sql "SELECT MIN(created_at), MAX(created_at), TIMESTAMPDIFF(SECOND, MIN(created_at), NOW()) FROM limited_purchase WHERE drop_id=${DROP_ID}")
echo "MIN_CREATED_AT=$(echo "$time_row" | cut -f1)"
echo "MAX_CREATED_AT=$(echo "$time_row" | cut -f2)"
echo "ELAPSED_SECONDS=$(echo "$time_row" | cut -f3)"

redis_stock=$(run_redis GET "limited:stock:${DROP_ID}")
echo "REDIS_STOCK=${redis_stock}"

redis_buyers_count=$(run_redis SCARD "limited:buyers:${DROP_ID}")
echo "REDIS_BUYERS_COUNT=${redis_buyers_count}"

redis_attempts=$(run_redis HGETALL "limited:attempts:${DROP_ID}")
while IFS= read -r line; do
	[ -z "$line" ] && continue
	echo "ATTEMPTS:${line}"
done <<< "$redis_attempts"

redis_pending_count=$(run_redis ZCARD "limited:pending:${DROP_ID}")
[ -z "$redis_pending_count" ] && redis_pending_count=0
echo "REDIS_PENDING_COUNT=${redis_pending_count}"

# GROUP_CONCAT 기본 상한 1024자면 구매자 200명쯤에서 잘려 집합 비교가 거짓 FAIL 이 난다.
db_buyers=$(run_sql "SET SESSION group_concat_max_len=1000000; SELECT IFNULL(GROUP_CONCAT(member_id ORDER BY member_id), '') FROM limited_purchase WHERE drop_id=${DROP_ID}")
echo "DB_BUYERS=${db_buyers}"

redis_buyers_raw=$(run_redis SMEMBERS "limited:buyers:${DROP_ID}")
if [ -z "$redis_buyers_raw" ]; then
	redis_buyers=""
else
	redis_buyers=$(printf '%s\n' "$redis_buyers_raw" | sort -n | paste -sd, -)
fi
echo "REDIS_BUYERS=${redis_buyers}"

redis_pending_members_raw=$(run_redis ZRANGE "limited:pending:${DROP_ID}" 0 -1)
if [ -z "$redis_pending_members_raw" ]; then
	redis_pending_members=""
else
	redis_pending_members=$(printf '%s\n' "$redis_pending_members_raw" | paste -sd, -)
fi
echo "REDIS_PENDING_MEMBERS=${redis_pending_members}"
REMOTE_SCRIPT_EOF
)

if [ "$LOCAL_MODE" = "1" ]; then
	RAW_OUTPUT=$(bash -s -- "$DROP_ID" "$PRODUCT_ID" "$MYSQL_CONTAINER" "$REDIS_CONTAINER" <<<"$REMOTE_SCRIPT")
else
	RAW_OUTPUT=$(ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" bash -s -- "$DROP_ID" "$PRODUCT_ID" "$MYSQL_CONTAINER" "$REDIS_CONTAINER" <<<"$REMOTE_SCRIPT")
fi

get_val() {
	echo "$RAW_OUTPUT" | grep "^$1=" | head -1 | cut -d= -f2- || true
}

PURCHASE_COUNT=$(get_val PURCHASE_COUNT)
STOCK_QTY=$(get_val STOCK_QTY)
DROP_TOTAL_QTY=$(get_val DROP_TOTAL_QTY)
DROP_SOLD_COUNT=$(get_val DROP_SOLD_COUNT)
DROP_STATUS=$(get_val DROP_STATUS)
DROP_SOLD_OUT_AT=$(get_val DROP_SOLD_OUT_AT)
MIN_CREATED_AT=$(get_val MIN_CREATED_AT)
MAX_CREATED_AT=$(get_val MAX_CREATED_AT)
ELAPSED_SECONDS=$(get_val ELAPSED_SECONDS)
REDIS_STOCK=$(get_val REDIS_STOCK)
REDIS_BUYERS_COUNT=$(get_val REDIS_BUYERS_COUNT)
REDIS_PENDING_COUNT=$(get_val REDIS_PENDING_COUNT)
DB_BUYERS=$(get_val DB_BUYERS)
REDIS_BUYERS=$(get_val REDIS_BUYERS)
REDIS_PENDING_MEMBERS=$(get_val REDIS_PENDING_MEMBERS)
ORDER_STATUS_LINES=$(echo "$RAW_OUTPUT" | grep '^ORDER_STATUS:' | sed 's/^ORDER_STATUS://') || true
ATTEMPTS_LINES=$(echo "$RAW_OUTPUT" | grep '^ATTEMPTS:' | sed 's/^ATTEMPTS://') || true

REPORT=$(cat <<REPORT_EOF
==== 한정반 초과판매 판정 (dropId=${DROP_ID}, productId=${PRODUCT_ID}) ====
모드: ${MODE_LABEL}, 판정: ${VERDICT_LABEL}

[원본 조회 결과]
limited_purchase 행 수      : ${PURCHASE_COUNT}
stock.quantity              : ${STOCK_QTY}
limited_drop.total_quantity : ${DROP_TOTAL_QTY}
limited_drop.sold_count     : ${DROP_SOLD_COUNT}
limited_drop.status         : ${DROP_STATUS}
limited_drop.sold_out_at    : ${DROP_SOLD_OUT_AT}
주문 상태별 건수 (product_id=${PRODUCT_ID}):
${ORDER_STATUS_LINES:-  (없음)}
limited_purchase 최초/최후 시각 : ${MIN_CREATED_AT} ~ ${MAX_CREATED_AT}
최초 구매로부터 경과(초)    : ${ELAPSED_SECONDS}
Redis limited:stock          : ${REDIS_STOCK}
Redis limited:buyers (SCARD) : ${REDIS_BUYERS_COUNT}
Redis limited:pending (ZCARD): ${REDIS_PENDING_COUNT}
Redis limited:pending 멤버   : ${REDIS_PENDING_MEMBERS:-  (없음)}
DB 구매자 집합               : ${DB_BUYERS:-  (없음)}
Redis 구매자 집합            : ${REDIS_BUYERS:-  (없음)}
Redis limited:attempts (HGETALL):
${ATTEMPTS_LINES:-  (없음)}

[판정]
REPORT_EOF
)

if [ -z "$RAW_OUTPUT" ]; then
	echo "조회 결과가 비어 있다. SSH/컨테이너 상태를 확인해라." >&2
	exit 2
fi

if [ -z "$DROP_TOTAL_QTY" ]; then
	echo "dropId=${DROP_ID} 인 한정반 드롭이 없다. 판정할 대상이 없다." >&2
	exit 2
fi

FAIL=0
ELAPSED_OVER=0

if [ -n "${ELAPSED_SECONDS:-}" ] && [ "$ELAPSED_SECONDS" != "NULL" ] && [ "$ELAPSED_SECONDS" -gt 600 ] 2>/dev/null; then
	ELAPSED_OVER=1
fi

if [ "$CHAOS_MODE" = "1" ]; then
	STOCK_EXPECTED=$(( ${DROP_TOTAL_QTY:-0} - ${PURCHASE_COUNT:-0} ))
	C1_OK=1
	C1_DETAIL=""
	if ! [ "${PURCHASE_COUNT:-0}" -le "${DROP_TOTAL_QTY:-0}" ] 2>/dev/null; then
		C1_OK=0
		C1_DETAIL="${C1_DETAIL}purchase_count(${PURCHASE_COUNT})>total_quantity(${DROP_TOTAL_QTY}) "
	fi
	if [ "$DROP_SOLD_COUNT" != "$PURCHASE_COUNT" ]; then
		C1_OK=0
		C1_DETAIL="${C1_DETAIL}sold_count(${DROP_SOLD_COUNT})!=purchase_count(${PURCHASE_COUNT}) "
	fi
	if [ "$STOCK_QTY" != "$STOCK_EXPECTED" ]; then
		C1_OK=0
		C1_DETAIL="${C1_DETAIL}stock.quantity(${STOCK_QTY})!=total_quantity-purchase_count(${STOCK_EXPECTED}) "
	fi
	if [ "$C1_OK" = "1" ]; then
		C1_LINE="  1) PASS - 초과판매 없음 (purchase_count=${PURCHASE_COUNT}, stock=${STOCK_QTY}, sold_count=${DROP_SOLD_COUNT})"
	else
		C1_LINE="  1) FAIL - 초과판매 의심: ${C1_DETAIL}"
		FAIL=1
	fi

	if [ "$PURCHASE_COUNT" = "$DROP_TOTAL_QTY" ]; then
		C2_LINE="  2) PASS - 완판: purchase_count(${PURCHASE_COUNT}) == total_quantity(${DROP_TOTAL_QTY})"
	else
		SHORTAGE=$(( ${DROP_TOTAL_QTY:-0} - ${PURCHASE_COUNT:-0} ))
		C2_LINE="  2) INFO - 미달 ${SHORTAGE}건 (purchase_count=${PURCHASE_COUNT}, total_quantity=${DROP_TOTAL_QTY})"
	fi

	C3_FAIL=0
	if [ "${REDIS_PENDING_COUNT:-0}" = "0" ]; then
		C3_LINE="  3) PASS - Redis pending 잔여 없음"
	else
		C3_LINE="  3) FAIL - Redis pending 잔여 ${REDIS_PENDING_COUNT}건 (members: ${REDIS_PENDING_MEMBERS:-없음})"
		C3_FAIL=1
	fi

	C4_FAIL=0
	REDIS_STOCK_EXPECTED=$(( ${DROP_TOTAL_QTY:-0} - ${DROP_SOLD_COUNT:-0} ))
	if [ -z "${REDIS_STOCK:-}" ]; then
		C4_LINE="  4) FAIL - Redis limited:stock 키 없음"
		C4_FAIL=1
	elif [ "$REDIS_STOCK" = "$REDIS_STOCK_EXPECTED" ]; then
		C4_LINE="  4) PASS - Redis stock(${REDIS_STOCK}) == total_quantity-sold_count(${REDIS_STOCK_EXPECTED})"
	else
		C4_LINE="  4) FAIL - Redis stock(${REDIS_STOCK}) != total_quantity-sold_count(${REDIS_STOCK_EXPECTED})"
		C4_FAIL=1
	fi

	C5_FAIL=0
	if [ "${REDIS_BUYERS:-}" = "${DB_BUYERS:-}" ]; then
		C5_LINE="  5) PASS - Redis 구매자 집합 == DB 구매자 집합"
	else
		DIFF_COUNT=$(comm -3 <(printf '%s\n' "${REDIS_BUYERS:-}" | tr ',' '\n' | sort -n) <(printf '%s\n' "${DB_BUYERS:-}" | tr ',' '\n' | sort -n) | grep -c '[0-9]' || true)
		C5_LINE="  5) FAIL - Redis 구매자 집합 != DB 구매자 집합 (대칭차 ${DIFF_COUNT}건)"
		C5_FAIL=1
	fi

	C6_FAIL=0
	if [ "${REDIS_BUYERS_COUNT:-}" = "${PURCHASE_COUNT:-}" ]; then
		C6_LINE="  6) PASS - Redis buyers 수(${REDIS_BUYERS_COUNT}) == limited_purchase 행 수(${PURCHASE_COUNT})"
	else
		C6_LINE="  6) FAIL - Redis buyers 수(${REDIS_BUYERS_COUNT}) != limited_purchase 행 수(${PURCHASE_COUNT})"
		C6_FAIL=1
	fi

	if [ "$DROP_STATUS" = "CLOSED" ]; then
		C3_LINE="  3) 판정 불가 - 드롭이 CLOSED 라 Redis 키가 지워졌을 수 있음"
		C4_LINE="  4) 판정 불가 - 드롭이 CLOSED 라 Redis 키가 지워졌을 수 있음"
		C5_LINE="  5) 판정 불가 - 드롭이 CLOSED 라 Redis 키가 지워졌을 수 있음"
		C6_LINE="  6) 판정 불가 - 드롭이 CLOSED 라 Redis 키가 지워졌을 수 있음"
		C3_FAIL=0
		C4_FAIL=0
		C5_FAIL=0
		C6_FAIL=0
	fi

	if [ "$C3_FAIL" = "1" ] || [ "$C4_FAIL" = "1" ] || [ "$C5_FAIL" = "1" ] || [ "$C6_FAIL" = "1" ]; then
		FAIL=1
	fi

	C7_LINE="  7) 10분 경과 경고: 경과 ${ELAPSED_SECONDS:-?}초 (초과 시 아래 경고 참조, 판정 무효 가능)"

	JUDGEMENT_BLOCK="${C1_LINE}
${C2_LINE}
${C3_LINE}
${C4_LINE}
${C5_LINE}
${C6_LINE}
${C7_LINE}"
else
	if [ "$PURCHASE_COUNT" = "$DROP_TOTAL_QTY" ]; then
		JUDGE1="  PASS - limited_purchase 행 수(${PURCHASE_COUNT}) == total_quantity(${DROP_TOTAL_QTY})"
	else
		JUDGE1="  FAIL - limited_purchase 행 수(${PURCHASE_COUNT}) != total_quantity(${DROP_TOTAL_QTY})"
		FAIL=1
	fi

	if [ "$STOCK_QTY" = "0" ]; then
		JUDGE2="  PASS - stock.quantity == 0"
	else
		JUDGE2="  FAIL - stock.quantity(${STOCK_QTY}) != 0"
		FAIL=1
	fi

	if [ "$DROP_SOLD_COUNT" = "$DROP_TOTAL_QTY" ]; then
		JUDGE3="  PASS - sold_count(${DROP_SOLD_COUNT}) == total_quantity(${DROP_TOTAL_QTY})"
	else
		JUDGE3="  FAIL - sold_count(${DROP_SOLD_COUNT}) != total_quantity(${DROP_TOTAL_QTY})"
		FAIL=1
	fi

	if [ "$DROP_STATUS" = "CLOSED" ]; then
		JUDGE4="  판정 불가 - 드롭이 이미 CLOSED 라 Redis 키가 지워졌을 수 있음 (buyers=${REDIS_BUYERS_COUNT})"
	elif [ "$REDIS_BUYERS_COUNT" = "$DROP_TOTAL_QTY" ]; then
		JUDGE4="  PASS - Redis limited:buyers(${REDIS_BUYERS_COUNT}) == total_quantity(${DROP_TOTAL_QTY})"
	else
		JUDGE4="  FAIL - Redis limited:buyers(${REDIS_BUYERS_COUNT}) != total_quantity(${DROP_TOTAL_QTY})"
		FAIL=1
	fi

	if [ "${REDIS_BUYERS:-}" = "${DB_BUYERS:-}" ]; then
		INFO_BUYERS="  INFO - Redis 구매자 집합 == DB 구매자 집합 (pending 잔여 ${REDIS_PENDING_COUNT:-0}건, 판정에는 포함하지 않음)"
	else
		INFO_BUYERS="  INFO - Redis 구매자 집합 != DB 구매자 집합 (pending 잔여 ${REDIS_PENDING_COUNT:-0}건, 판정에는 포함하지 않음)"
	fi

	JUDGEMENT_BLOCK="${JUDGE1}
${JUDGE2}
${JUDGE3}
${JUDGE4}
${INFO_BUYERS}"
fi

FULL_REPORT="${REPORT}
${JUDGEMENT_BLOCK}
"

if [ "$ELAPSED_OVER" = "1" ]; then
	FULL_REPORT="${FULL_REPORT}
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!! 경고: 최초 구매로부터 ${ELAPSED_SECONDS}초 경과 (10분 초과)
!! 만료 스케줄러가 limited_purchase 를 이미 지웠을 수 있음 - 판정 무효 가능
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
"
fi

echo "$FULL_REPORT"

if [ -n "$OUT_FILE" ]; then
	echo "$FULL_REPORT" | tee -a "$OUT_FILE" > /dev/null
	echo "결과 저장함: ${OUT_FILE}"
fi

if [ "$FAIL" = "1" ]; then
	if [ "$ELAPSED_OVER" = "1" ]; then
		exit 3
	fi
	exit 1
fi

exit 0
