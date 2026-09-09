#!/usr/bin/env bash
# 운영 EC2 에 대고 한정반 k6 부하 테스트를 돌리면 남는 회원/상품/주문 등을 지운다.
# 기본은 dry-run. --apply 를 줘야 실제로 지운다. 삭제문은 전부 이메일/타이틀 접두사로 좁힌 id 집합에서만 나온다.
set -euo pipefail

SSH_KEY="${SSH_KEY:-$HOME/.ssh/groove-key.pem}"
SSH_HOST="${SSH_HOST:-ubuntu@52.78.95.139}"
SSH_OPTS="${SSH_OPTS:--o ConnectTimeout=8 -o BatchMode=yes}"

MEMBER_EMAIL_PREFIX="${MEMBER_EMAIL_PREFIX:-k6lt-}"
PRODUCT_TITLE_PREFIX="${PRODUCT_TITLE_PREFIX:-LIMITED-LOADTEST-}"
MAX_MEMBERS="${MAX_MEMBERS:-2000}"

APPLY=0
AUTO_YES=0
DROP_IDS_OVERRIDE=""

usage() {
	echo "사용법: $0 [--apply] [--yes] [--drop-ids \"1,2,3\"]"
	echo "  기본은 dry-run(조회만). --apply 를 줘야 실제로 DELETE 한다."
}

while [[ $# -gt 0 ]]; do
	case "$1" in
	--apply)
		APPLY=1
		shift
		;;
	--yes)
		AUTO_YES=1
		shift
		;;
	--drop-ids)
		DROP_IDS_OVERRIDE="$2"
		if [[ ! "$DROP_IDS_OVERRIDE" =~ ^[0-9]+(,[0-9]+)*$ ]]; then
			echo "거부: --drop-ids 는 콤마로 구분된 숫자여야 한다 (받은 값: '${DROP_IDS_OVERRIDE}')." >&2
			exit 2
		fi
		shift 2
		;;
	-h | --help)
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

# 접두사 안전 검사: 빈 문자열이거나 와일드카드(%, _)로만 이루어지면 전체 테이블을 긁을 수 있으므로 즉시 거부한다.
validate_prefix() {
	local name="$1"
	local value="$2"
	if [[ -z "$value" ]]; then
		echo "거부: ${name} 가 비어 있다." >&2
		exit 2
	fi
	if [[ ${#value} -lt 4 ]]; then
		echo "거부: ${name} 는 최소 4자 이상이어야 한다 (현재: '${value}')." >&2
		exit 2
	fi
	local stripped="${value//%/}"
	stripped="${stripped//_/}"
	if [[ -z "$stripped" ]]; then
		echo "거부: ${name} 가 와일드카드(%, _)만으로 이루어져 있다." >&2
		exit 2
	fi
}

validate_prefix "MEMBER_EMAIL_PREFIX" "$MEMBER_EMAIL_PREFIX"
validate_prefix "PRODUCT_TITLE_PREFIX" "$PRODUCT_TITLE_PREFIX"

echo "== 운영 한정반 부하테스트 데이터 정리 =="
echo "대상 호스트     : ${SSH_HOST}"
echo "회원 이메일 접두사: ${MEMBER_EMAIL_PREFIX}"
echo "상품 타이틀 접두사: ${PRODUCT_TITLE_PREFIX}"
echo "모드           : $([[ $APPLY -eq 1 ]] && echo APPLY || echo DRY-RUN)"
echo

run_remote_sql() {
	# $1: 원격에서 실행할 SQL(heredoc 본문). 로컬 변수는 호출부에서 이미 치환된 상태로 넘어온다.
	local sql="$1"
	# shellcheck disable=SC2086,SC2087  # SSH_OPTS 는 의도적 언쿼팅, heredoc 치환도 로컬에서 해야 한다
	ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" bash -s <<REMOTE_SCRIPT
set -euo pipefail
docker exec -i groove-mysql sh -c 'MYSQL_PWD="\$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B "\$MYSQL_DATABASE"' <<'SQL'
${sql}
SQL
REMOTE_SCRIPT
}

run_remote_redis_del() {
	# $1: 공백으로 구분된 redis key 목록. KEYS 스캔은 절대 쓰지 않고 명시적 key만 지운다.
	local keys="$1"
	if [[ -z "$keys" ]]; then
		return 0
	fi
	# shellcheck disable=SC2086,SC2087  # SSH_OPTS 는 의도적 언쿼팅, heredoc 치환도 로컬에서 해야 한다
	ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" bash -s <<REMOTE_SCRIPT
set -euo pipefail
docker exec groove-redis redis-cli DEL ${keys} >/dev/null
REMOTE_SCRIPT
}

# ---- 1. 대상 회원/상품/앨범/드롭 id 수집 (drop id 는 limited_drop 을 지우기 전에 미리 확보해야 admin_audit_log 정리에 쓸 수 있다) ----
echo "-- 대상 id 수집 중 --"
COLLECT_SQL=$(cat <<SQL
SELECT id FROM member WHERE email LIKE '${MEMBER_EMAIL_PREFIX}%@groove.com';
SQL
)
MEMBER_IDS=$(run_remote_sql "$COLLECT_SQL" | tr '\n' ',' | sed 's/,$//')

COLLECT_SQL=$(cat <<SQL
SELECT id FROM product WHERE title LIKE '${PRODUCT_TITLE_PREFIX}%';
SQL
)
PRODUCT_IDS=$(run_remote_sql "$COLLECT_SQL" | tr '\n' ',' | sed 's/,$//')

COLLECT_SQL=$(cat <<SQL
SELECT id FROM album WHERE title LIKE '${PRODUCT_TITLE_PREFIX}%';
SQL
)
ALBUM_IDS=$(run_remote_sql "$COLLECT_SQL" | tr '\n' ',' | sed 's/,$//')

DROP_IDS=""
if [[ -n "$PRODUCT_IDS" ]]; then
	COLLECT_SQL=$(cat <<SQL
SELECT id FROM limited_drop WHERE product_id IN (${PRODUCT_IDS});
SQL
)
	DROP_IDS=$(run_remote_sql "$COLLECT_SQL" | tr '\n' ',' | sed 's/,$//')
fi

# --drop-ids 는 대체가 아니라 추가다. 상품이 이미 지워졌거나 부분 실패한 드롭을 마저 정리하는 용도.
if [[ -n "$DROP_IDS_OVERRIDE" ]]; then
	DROP_IDS=$(printf '%s\n%s\n' "${DROP_IDS//,/$'\n'}" "${DROP_IDS_OVERRIDE//,/$'\n'}" | grep -E '^[0-9]+$' | sort -un | paste -sd, -)
	echo "--drop-ids 를 합쳐 대상 드롭: ${DROP_IDS}"
fi

# 콤마 목록의 원소 수. 빈 문자열이면 0.
count_ids() {
	local list="${1:-}"
	if [[ -z "$list" ]]; then
		echo 0
		return 0
	fi
	awk -F, '{print NF}' <<<"$list"
}

MEMBER_COUNT=$(count_ids "$MEMBER_IDS")

echo "회원 ${MEMBER_COUNT}건, 상품 $(count_ids "${PRODUCT_IDS:-}")건, 앨범 $(count_ids "${ALBUM_IDS:-}")건, 드롭 $(count_ids "${DROP_IDS:-}")건"

if [[ -z "$MEMBER_IDS" && -z "$PRODUCT_IDS" ]]; then
	echo "삭제 대상이 없다. 종료한다."
	exit 0
fi

if [[ $MEMBER_COUNT -gt $MAX_MEMBERS ]]; then
	echo "거부: 대상 회원 수(${MEMBER_COUNT})가 MAX_MEMBERS(${MAX_MEMBERS}) 를 초과한다. 접두사를 확인해라." >&2
	exit 2
fi

# ---- 2. 관리자 계정 포함 여부 확인 ----
if [[ -n "$MEMBER_IDS" ]]; then
	ADMIN_CHECK_SQL=$(cat <<SQL
SELECT COUNT(*) FROM member WHERE id IN (${MEMBER_IDS}) AND role = 'ADMIN';
SQL
)
	ADMIN_COUNT=$(run_remote_sql "$ADMIN_CHECK_SQL")
	if [[ "${ADMIN_COUNT:-0}" != "0" ]]; then
		echo "거부: 삭제 대상에 ADMIN 계정이 ${ADMIN_COUNT}건 포함되어 있다." >&2
		exit 2
	fi
fi

MEMBER_IDS_SQL="${MEMBER_IDS:--1}"
PRODUCT_IDS_SQL="${PRODUCT_IDS:--1}"
ALBUM_IDS_SQL="${ALBUM_IDS:--1}"
DROP_IDS_SQL="${DROP_IDS:--1}"

# ---- 3. 테이블별 대상 건수 미리보기 ----
COUNT_SQL=$(cat <<SQL
SELECT 'limited_drop_stat', COUNT(*) FROM limited_drop_stat WHERE drop_id IN (${DROP_IDS_SQL})
UNION ALL SELECT 'limited_purchase', COUNT(*) FROM limited_purchase WHERE drop_id IN (${DROP_IDS_SQL})
UNION ALL SELECT 'limited_drop', COUNT(*) FROM limited_drop WHERE product_id IN (${PRODUCT_IDS_SQL})
UNION ALL SELECT 'stock_history', COUNT(*) FROM stock_history WHERE stock_id IN (SELECT id FROM stock WHERE product_id IN (${PRODUCT_IDS_SQL}))
UNION ALL SELECT 'order_item', COUNT(*) FROM order_item WHERE product_id IN (${PRODUCT_IDS_SQL}) OR order_id IN (SELECT id FROM orders WHERE member_id IN (${MEMBER_IDS_SQL}))
UNION ALL SELECT 'payment', COUNT(*) FROM payment WHERE order_id IN (SELECT id FROM orders WHERE member_id IN (${MEMBER_IDS_SQL}))
UNION ALL SELECT 'orders', COUNT(*) FROM orders WHERE member_id IN (${MEMBER_IDS_SQL})
UNION ALL SELECT 'stock', COUNT(*) FROM stock WHERE product_id IN (${PRODUCT_IDS_SQL})
UNION ALL SELECT 'product_image', COUNT(*) FROM product_image WHERE product_id IN (${PRODUCT_IDS_SQL})
UNION ALL SELECT 'product_genre', COUNT(*) FROM product_genre WHERE product_id IN (${PRODUCT_IDS_SQL})
UNION ALL SELECT 'cart_item', COUNT(*) FROM cart_item WHERE product_id IN (${PRODUCT_IDS_SQL}) OR cart_id IN (SELECT id FROM cart WHERE member_id IN (${MEMBER_IDS_SQL}))
UNION ALL SELECT 'cart', COUNT(*) FROM cart WHERE member_id IN (${MEMBER_IDS_SQL})
UNION ALL SELECT 'wishlist', COUNT(*) FROM wishlist WHERE member_id IN (${MEMBER_IDS_SQL}) OR product_id IN (${PRODUCT_IDS_SQL})
UNION ALL SELECT 'review', COUNT(*) FROM review WHERE member_id IN (${MEMBER_IDS_SQL}) OR product_id IN (${PRODUCT_IDS_SQL})
UNION ALL SELECT 'member_coupon', COUNT(*) FROM member_coupon WHERE member_id IN (${MEMBER_IDS_SQL})
UNION ALL SELECT 'notification', COUNT(*) FROM notification WHERE member_id IN (${MEMBER_IDS_SQL}) OR product_id IN (${PRODUCT_IDS_SQL}) OR album_id IN (${ALBUM_IDS_SQL})
UNION ALL SELECT 'album_watch', COUNT(*) FROM album_watch WHERE member_id IN (${MEMBER_IDS_SQL}) OR album_id IN (${ALBUM_IDS_SQL})
UNION ALL SELECT 'product_view_log', COUNT(*) FROM product_view_log WHERE member_id IN (${MEMBER_IDS_SQL}) OR product_id IN (${PRODUCT_IDS_SQL})
UNION ALL SELECT 'member_taste_profile', COUNT(*) FROM member_taste_profile WHERE member_id IN (${MEMBER_IDS_SQL})
UNION ALL SELECT 'product', COUNT(*) FROM product WHERE id IN (${PRODUCT_IDS_SQL})
UNION ALL SELECT 'album', COUNT(*) FROM album WHERE id IN (${ALBUM_IDS_SQL})
UNION ALL SELECT 'address', COUNT(*) FROM address WHERE member_id IN (${MEMBER_IDS_SQL})
UNION ALL SELECT 'admin_audit_log(product)', COUNT(*) FROM admin_audit_log WHERE target_type = 'PRODUCT' AND target_id IN (${PRODUCT_IDS_SQL})
UNION ALL SELECT 'admin_audit_log(limited_drop)', COUNT(*) FROM admin_audit_log WHERE target_type = 'LIMITED_DROP' AND target_id IN (${DROP_IDS_SQL})
UNION ALL SELECT 'member', COUNT(*) FROM member WHERE id IN (${MEMBER_IDS_SQL})
SQL
)

echo
echo "-- 테이블별 삭제 대상 건수 --"
run_remote_sql "$COUNT_SQL" | while IFS=$'\t' read -r table cnt; do
	printf '%-32s %s\n' "$table" "$cnt"
done

# ---- 4. 실행할 DELETE 문 조립.
# member_taste_artist/decade/genre 는 k6 경로에서 애초에 안 생기지만, member_taste_profile 을 지우기 전에
# 방어적으로 profile id 기준으로 먼저 정리한다.
DELETE_SQL=$(cat <<SQL
START TRANSACTION;

DELETE FROM limited_drop_stat WHERE drop_id IN (${DROP_IDS_SQL});
DELETE FROM limited_purchase WHERE drop_id IN (${DROP_IDS_SQL});
DELETE FROM limited_drop WHERE product_id IN (${PRODUCT_IDS_SQL});

DELETE FROM stock_history WHERE stock_id IN (SELECT id FROM stock WHERE product_id IN (${PRODUCT_IDS_SQL}));

DELETE FROM order_item WHERE product_id IN (${PRODUCT_IDS_SQL}) OR order_id IN (SELECT id FROM orders WHERE member_id IN (${MEMBER_IDS_SQL}));
-- 한정반 구매는 PENDING 주문이라 payment 행이 생기지 않는다. 그래도 orders 를 참조하는 FK 라 방어적으로 먼저 지운다.
DELETE FROM payment WHERE order_id IN (SELECT id FROM orders WHERE member_id IN (${MEMBER_IDS_SQL}));
DELETE FROM orders WHERE member_id IN (${MEMBER_IDS_SQL});

DELETE FROM stock WHERE product_id IN (${PRODUCT_IDS_SQL});

DELETE FROM product_image WHERE product_id IN (${PRODUCT_IDS_SQL});
DELETE FROM product_genre WHERE product_id IN (${PRODUCT_IDS_SQL});

DELETE FROM cart_item WHERE product_id IN (${PRODUCT_IDS_SQL}) OR cart_id IN (SELECT id FROM cart WHERE member_id IN (${MEMBER_IDS_SQL}));
DELETE FROM cart WHERE member_id IN (${MEMBER_IDS_SQL});
DELETE FROM wishlist WHERE member_id IN (${MEMBER_IDS_SQL}) OR product_id IN (${PRODUCT_IDS_SQL});
DELETE FROM review WHERE member_id IN (${MEMBER_IDS_SQL}) OR product_id IN (${PRODUCT_IDS_SQL});
DELETE FROM member_coupon WHERE member_id IN (${MEMBER_IDS_SQL});
DELETE FROM notification WHERE member_id IN (${MEMBER_IDS_SQL}) OR product_id IN (${PRODUCT_IDS_SQL}) OR album_id IN (${ALBUM_IDS_SQL});
DELETE FROM album_watch WHERE member_id IN (${MEMBER_IDS_SQL}) OR album_id IN (${ALBUM_IDS_SQL});
DELETE FROM product_view_log WHERE member_id IN (${MEMBER_IDS_SQL}) OR product_id IN (${PRODUCT_IDS_SQL});
DELETE FROM member_taste_artist WHERE profile_id IN (SELECT id FROM member_taste_profile WHERE member_id IN (${MEMBER_IDS_SQL}));
DELETE FROM member_taste_decade WHERE profile_id IN (SELECT id FROM member_taste_profile WHERE member_id IN (${MEMBER_IDS_SQL}));
DELETE FROM member_taste_genre WHERE profile_id IN (SELECT id FROM member_taste_profile WHERE member_id IN (${MEMBER_IDS_SQL}));
DELETE FROM member_taste_profile WHERE member_id IN (${MEMBER_IDS_SQL});

DELETE FROM product WHERE id IN (${PRODUCT_IDS_SQL});
DELETE FROM album WHERE id IN (${ALBUM_IDS_SQL});

DELETE FROM address WHERE member_id IN (${MEMBER_IDS_SQL});

DELETE FROM admin_audit_log WHERE target_type = 'PRODUCT' AND target_id IN (${PRODUCT_IDS_SQL});
DELETE FROM admin_audit_log WHERE target_type = 'LIMITED_DROP' AND target_id IN (${DROP_IDS_SQL});

DELETE FROM member WHERE id IN (${MEMBER_IDS_SQL});

COMMIT;
SQL
)

echo
echo "-- 실행될 DELETE 문 (트랜잭션 1개) --"
echo "$DELETE_SQL"
echo

if [[ $APPLY -eq 0 ]]; then
	echo "dry-run 종료: 실제로는 아무것도 지우지 않았다. 실행하려면 --apply 를 붙여라."
	exit 0
fi

if [[ $AUTO_YES -eq 0 ]]; then
	read -r -p "위 내용대로 운영 DB 에서 삭제를 진행할까? (yes 입력 시 진행) " CONFIRM
	if [[ "$CONFIRM" != "yes" ]]; then
		echo "취소됨."
		exit 0
	fi
fi

echo "-- 삭제 실행 중 --"
run_remote_sql "$DELETE_SQL"

echo "-- Redis 키 정리 (수집한 id 기준으로만 DEL, KEYS 스캔 없음) --"
REDIS_KEYS=""
if [[ -n "${DROP_IDS:-}" ]]; then
	for d in $(echo "$DROP_IDS" | tr ',' ' '); do
		REDIS_KEYS="${REDIS_KEYS} limited:stock:${d} limited:buyers:${d} limited:attempts:${d}"
	done
fi
if [[ -n "${MEMBER_IDS:-}" ]]; then
	for m in $(echo "$MEMBER_IDS" | tr ',' ' '); do
		REDIS_KEYS="${REDIS_KEYS} refresh:${m}"
	done
fi
run_remote_redis_del "${REDIS_KEYS# }"

echo
echo "-- 사후 검증: 같은 접두사로 재조회 --"
VERIFY_SQL=$(cat <<SQL
SELECT 'member' AS t, COUNT(*) FROM member WHERE email LIKE '${MEMBER_EMAIL_PREFIX}%@groove.com'
UNION ALL SELECT 'product', COUNT(*) FROM product WHERE title LIKE '${PRODUCT_TITLE_PREFIX}%'
UNION ALL SELECT 'album', COUNT(*) FROM album WHERE title LIKE '${PRODUCT_TITLE_PREFIX}%';
SQL
)
VERIFY_RESULT=$(run_remote_sql "$VERIFY_SQL")
echo "$VERIFY_RESULT"

# sales_daily / sales_daily_product 는 손대지 않는다: 집계 소스가 payment.approved_at 인데
# k6 부하테스트 주문에는 payment 행이 없어 애초에 집계에 안 잡힌다.

REMAIN=$(echo "$VERIFY_RESULT" | awk -F'\t' '{sum += $2} END {print sum+0}')
if [[ "$REMAIN" != "0" ]]; then
	echo "실패: 삭제 후에도 ${REMAIN}건이 남아 있다." >&2
	exit 1
fi

echo "완료: 접두사 '${MEMBER_EMAIL_PREFIX}' / '${PRODUCT_TITLE_PREFIX}' 대상 데이터를 모두 지웠다."
