#!/usr/bin/env bash
# 한정반 초과판매 여부를 원격 DB/Redis 상태로 판정한다.
#
# 중요한 제약: 한정반 구매는 PENDING 주문이고 OrderExpirationScheduler 가 60초마다
# 돌며 10분 지난 주문을 취소하는데, 그때 limited_purchase 행이 삭제되고 재고가
# 복구된다. 즉 이 판정은 첫 구매(limited_purchase.created_at) 로부터 10분 안에만
# 유효하다. 10분이 넘었으면 결과가 이미 취소 복구로 오염됐을 수 있다.
# shellcheck disable=SC2086 # SSH_OPTS 는 여러 -o 플래그를 담는 문자열이라 의도적으로 언쿼팅
set -euo pipefail

SSH_KEY="${SSH_KEY:-$HOME/.ssh/groove-key.pem}"
SSH_HOST="${SSH_HOST:-ubuntu@52.78.95.139}"
SSH_OPTS="${SSH_OPTS:--o ConnectTimeout=8 -o BatchMode=yes}"

usage() {
	cat <<EOF
사용법: $(basename "$0") <dropId> <productId> [출력파일]

환경변수: SSH_KEY(기본 $HOME/.ssh/groove-key.pem), SSH_HOST(기본 ubuntu@52.78.95.139), SSH_OPTS
EOF
}

if [ $# -lt 2 ]; then
	usage
	exit 2
fi

DROP_ID="$1"
PRODUCT_ID="$2"
OUT_FILE="${3:-}"

echo "주의: 한정반 구매는 PENDING 상태로 10분 뒤 만료 스케줄러가 limited_purchase 를"
echo "      삭제하고 재고를 복구한다. 이 판정은 첫 구매로부터 10분 이내에만 유효하다."
echo

RAW_OUTPUT=$(ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" bash -s -- "$DROP_ID" "$PRODUCT_ID" <<'REMOTE_SCRIPT'
set -uo pipefail
DROP_ID="$1"
PRODUCT_ID="$2"

run_sql() {
	local sql="$1"
	# -i 를 주면 원격 스크립트의 stdin(ssh 채널)을 docker 가 먹어 나머지 명령이 사라진다. SQL 은 -e 로만 넘긴다.
	docker exec groove-mysql sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot -N -B \"\$MYSQL_DATABASE\" -e \"${sql}\"" </dev/null
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

redis_stock=$(docker exec groove-redis redis-cli GET "limited:stock:${DROP_ID}" 2>/dev/null || echo "")
echo "REDIS_STOCK=${redis_stock}"

redis_buyers=$(docker exec groove-redis redis-cli SCARD "limited:buyers:${DROP_ID}" 2>/dev/null || echo "")
echo "REDIS_BUYERS_COUNT=${redis_buyers}"

redis_attempts=$(docker exec groove-redis redis-cli HGETALL "limited:attempts:${DROP_ID}" 2>/dev/null || echo "")
while IFS= read -r line; do
	[ -z "$line" ] && continue
	echo "ATTEMPTS:${line}"
done <<< "$redis_attempts"
REMOTE_SCRIPT
)

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
ORDER_STATUS_LINES=$(echo "$RAW_OUTPUT" | grep '^ORDER_STATUS:' | sed 's/^ORDER_STATUS://') || true
ATTEMPTS_LINES=$(echo "$RAW_OUTPUT" | grep '^ATTEMPTS:' | sed 's/^ATTEMPTS://') || true

REPORT=$(cat <<REPORT_EOF
==== 한정반 초과판매 판정 (dropId=${DROP_ID}, productId=${PRODUCT_ID}) ====

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
Redis limited:attempts (HGETALL):
${ATTEMPTS_LINES:-  (없음)}

[판정]
REPORT_EOF
)

if [ -z "$RAW_OUTPUT" ]; then
	echo "원격 조회 결과가 비어 있다. SSH/컨테이너 상태를 확인해라." >&2
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

FULL_REPORT="${REPORT}
${JUDGE1}
${JUDGE2}
${JUDGE3}
${JUDGE4}
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
