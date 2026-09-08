#!/usr/bin/env bash
# 인덱스 후보의 실행계획을 재보는 하네스.
# 운영/로컬 데이터는 수백 건뿐이라 EXPLAIN 을 떠도 전부 풀스캔이라 인덱스 판단이 안 된다.
# 그래서 버려도 되는 별도 스키마 groove_perf 를 만들어 Flyway 로 운영과 같은 스키마를 세우고
# 합성 데이터를 수십만 건 채운 뒤, 인덱스 DDL 적용 전(before)/후(after)를 같은 방식으로 재서 비교한다.
# 개발 DB(groove)는 절대 건드리지 않는다 — groove_perf 는 스키마명이 하드코딩되어 있다.
#
# --after-ddl 로 지정한 마이그레이션은 after 단계 진입 시점에 순서대로(sort -V) 적용한다.
# 여러 번 지정하면 누적되며, 그중 하나가 product.sold_quantity 컬럼처럼 컬럼 자체를 새로 만드는 경우
# 그 컬럼에 의존하는 백필/시드 보정도 after 단계에서만 돌려야 한다(자세한 내용은 각 지점의 주석 참고).
#
# 사용법:
#   index-explain.sh [--scale N] [--after-ddl 파일]... [--out 파일] [--keep] [--phase before|after|both]
set -euo pipefail

# OrbStack 은 DOCKER_HOST 를 별도로 export 해야 docker CLI 가 데몬을 찾는다.
if [ -z "${DOCKER_HOST:-}" ] && [ -S "${HOME}/.orbstack/run/docker.sock" ]; then
	export DOCKER_HOST="unix://${HOME}/.orbstack/run/docker.sock"
fi

MYSQL_CONTAINER="${MYSQL_CONTAINER:-groove-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root1234}"
readonly PERF_SCHEMA="groove_perf"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MIGRATION_DIR="${BACKEND_DIR}/src/main/resources/db/migration"

SCALE="1.0"
# macOS 기본 bash 3.2 는 연관 배열이 없어도 일반(인덱스) 배열은 쓸 수 있다. --after-ddl 을
# 여러 번 넘기면 이 배열에 누적된다.
AFTER_DDLS=()
OUT_FILE=""
KEEP=false
PHASE="both"

while [ $# -gt 0 ]; do
	case "$1" in
	--scale)
		SCALE="$2"
		shift 2
		;;
	--after-ddl)
		AFTER_DDLS+=("$2")
		shift 2
		;;
	--out)
		OUT_FILE="$2"
		shift 2
		;;
	--keep)
		KEEP=true
		shift
		;;
	--phase)
		PHASE="$2"
		shift 2
		;;
	*)
		echo "알 수 없는 옵션: $1" >&2
		exit 1
		;;
	esac
done

if ! [[ "${SCALE}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
	echo "--scale 값이 올바르지 않습니다: ${SCALE}" >&2
	exit 1
fi

# --after-ddl 을 한 번도 안 줬으면 기본값 하나만 넣는다. bash 3.2 는 길이 0 배열을 그냥 확인하는
# 건 괜찮지만 그 상태로 "${AFTER_DDLS[@]}" 를 펼치면 set -u 에서 죽으므로, 항목을 채운 뒤에만 펼친다.
if [ ${#AFTER_DDLS[@]} -eq 0 ]; then
	AFTER_DDLS=("${MIGRATION_DIR}/V12__product_sold_quantity.sql")
fi

RUN_BEFORE=false
RUN_AFTER=false
case "${PHASE}" in
before)
	RUN_BEFORE=true
	;;
after)
	RUN_AFTER=true
	;;
both)
	RUN_BEFORE=true
	RUN_AFTER=true
	;;
*)
	echo "알 수 없는 --phase 값: ${PHASE} (before|after|both)" >&2
	exit 1
	;;
esac

# 존재하는 파일만 절대 경로로 정규화해 남긴다. 정규화가 없으면 상대 경로로 넘긴 마이그레이션이
# apply_migrations 의 절대 경로와 문자열 비교에서 어긋나 before 단계에도 적용된다.
# 결과가 빈 배열이면 AFTER_DDLS 는 그대로 두고(항상 최소 1개는 들고 있어야 이후
# "${AFTER_DDLS[@]}" 펼치기가 bash 3.2 에서 안전하다) after 단계 자체를 건너뛴다.
EXISTING_AFTER_DDLS=()
for _ddl in "${AFTER_DDLS[@]}"; do
	if [ -f "${_ddl}" ]; then
		EXISTING_AFTER_DDLS+=("$(cd "$(dirname "${_ddl}")" && pwd)/$(basename "${_ddl}")")
	else
		echo "[안내] --after-ddl 파일이 없습니다(${_ddl})."
	fi
done
unset _ddl

if [ ${#EXISTING_AFTER_DDLS[@]} -eq 0 ]; then
	if [ "${RUN_AFTER}" = true ]; then
		echo "[안내] 적용할 after-ddl 파일이 없어 after 단계를 건너뛰고 before 만 측정합니다."
	fi
	RUN_AFTER=false
	RUN_BEFORE=true
else
	AFTER_DDLS=("${EXISTING_AFTER_DDLS[@]}")
fi

# 검색 케이스 고정값
readonly TARGET_MEMBER_ID=1
readonly TARGET_PRODUCT_ID=1
GENRE_ID_1=""
GENRE_ID_2=""
BODY_TMP=""
REPORT_TMP=""

CASE_IDS=(P1 P2 P3 P4 P5 P6 P7 O1 O2 O3 A1 A2 A3 A4 A5 R1 R2 R3 R4 N1 N2 N3)

# macOS 기본 /bin/bash 는 3.2 라 연관 배열(declare -A)을 못 쓴다. 케이스 설명/요약은
# case_desc()/set_summary()/get_summary() 로 대신한다.
case_desc() {
	case "$1" in
	P1) echo "상품 검색 기본 목록 LATEST, 필터 없음 (ProductSearchMapper.xml searchProducts)" ;;
	P2) echo "상품 검색 키워드 LATEST, keyword=Pressing 12 (ProductSearchMapper.xml searchProducts)" ;;
	P3) echo "상품 검색 장르 2개+가격대 LATEST (ProductSearchMapper.xml searchProducts)" ;;
	P4) echo "상품 검색 가격대 PRICE_ASC (ProductSearchMapper.xml searchProducts)" ;;
	P5) echo "상품 검색 POPULAR (before: 파생 테이블 집계 / after: product.sold_quantity)" ;;
	P6) echo "상품 검색 RATING, 깊은 페이지 offset=1000 (ProductSearchMapper.xml searchProducts)" ;;
	P7) echo "상품 검색 countProducts 무필터 (ProductSearchMapper.xml countProducts)" ;;
	O1) echo "내 주문 목록, status 없음 (OrderQueryMapper.xml findMyOrders)" ;;
	O2) echo "내 주문 목록, status=DELIVERED (OrderQueryMapper.xml findMyOrders)" ;;
	O3) echo "내 주문 countMyOrders (OrderQueryMapper.xml countMyOrders)" ;;
	A1) echo "관리자 주문 목록 무필터 (OrderQueryMapper.xml findAdminOrders)" ;;
	A2) echo "관리자 주문 목록 status=PAID (OrderQueryMapper.xml findAdminOrders)" ;;
	A3) echo "관리자 주문 목록 최근 30일 (OrderQueryMapper.xml findAdminOrders)" ;;
	A4) echo "관리자 주문 목록 키워드 perf12 (OrderQueryMapper.xml findAdminOrders)" ;;
	A5) echo "관리자 주문 countAdminOrders 무필터 (OrderQueryMapper.xml countAdminOrders)" ;;
	R1) echo "리뷰 목록 LATEST (ReviewRepository.findByProductId)" ;;
	R2) echo "리뷰 목록 RATING_DESC (ReviewRepository.findByProductId)" ;;
	R3) echo "리뷰 개수 (ReviewRepository 파생 count)" ;;
	R4) echo "상품별 별점 분포 (ReviewRepository.countByRatingForProduct)" ;;
	N1) echo "알림 목록 전체 (NotificationRepository.findAllByMemberId)" ;;
	N2) echo "알림 목록 안읽음만 (NotificationRepository.findAllByMemberIdAndReadAtIsNull)" ;;
	N3) echo "안읽음 개수 (NotificationRepository.countByMemberIdAndReadAtIsNull)" ;;
	*) echo "?" ;;
	esac
}

set_summary() {
	local phase="$1" id="$2" value="$3"
	printf -v "SUMMARY_${phase}_${id}" '%s' "${value}"
}

get_summary() {
	local varname="SUMMARY_$1_$2"
	if [ -n "${!varname+set}" ]; then
		printf '%s' "${!varname}"
	else
		printf '%s' "-"
	fi
}

cleanup() {
	local exit_code=$?
	rm -f "${BODY_TMP:-}" "${REPORT_TMP:-}"
	if [ "${KEEP}" != true ]; then
		mysql_root "DROP DATABASE IF EXISTS ${PERF_SCHEMA};" || true
	fi
	exit "${exit_code}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

mysql_root() {
	# 기본 스키마 없이 접속한다. CREATE/DROP DATABASE 용이라 root 로만 붙는다.
	docker exec -i -e MYSQL_PWD="${MYSQL_PASSWORD}" "${MYSQL_CONTAINER}" \
		mysql -u"${MYSQL_USER}" -N -B --raw -e "$1"
}

mysql_perf() {
	# groove_perf 스키마를 기본 스키마로 접속한다. 개발 DB(groove)는 여기서 절대 지정하지 않는다.
	docker exec -i -e MYSQL_PWD="${MYSQL_PASSWORD}" "${MYSQL_CONTAINER}" \
		mysql -u"${MYSQL_USER}" -N -B --raw "${PERF_SCHEMA}" -e "$1"
}

mysql_perf_file() {
	# 마이그레이션/DDL 파일을 그대로 stdin 으로 흘려보낸다 (PREPARE/EXECUTE 등 다중 문장 포함 가능).
	docker exec -i -e MYSQL_PWD="${MYSQL_PASSWORD}" "${MYSQL_CONTAINER}" \
		mysql -u"${MYSQL_USER}" "${PERF_SCHEMA}" < "$1"
}

scaled_count() {
	awk -v base="$1" -v scale="${SCALE}" 'BEGIN {
		v = base * scale
		r = int(v + 0.5)
		if (r < 1) { r = 1 }
		print r
	}'
}

# AFTER_DDLS 목록에 파일이 들어있는지 확인한다. AFTER_DDLS 는 항상 최소 1개를 들고 있으므로
# "${AFTER_DDLS[@]}" 를 펼쳐도 bash 3.2 nounset 에서 안전하다.
is_after_ddl() {
	local target="$1" candidate
	for candidate in "${AFTER_DDLS[@]}"; do
		if [ "${candidate}" = "${target}" ]; then
			return 0
		fi
	done
	return 1
}

apply_migrations() {
	local file base
	for file in $(find "${MIGRATION_DIR}" -maxdepth 1 -name 'V*.sql' | sort -V); do
		base=$(basename "${file}")
		if [ "${base}" = "V5__spring_batch_schema.sql" ]; then
			# 배치 메타 테이블(BATCH_*)은 시드에도 EXPLAIN 케이스에도 쓰이지 않아 건너뛴다.
			echo "  건너뜀: ${base}"
			continue
		fi
		if is_after_ddl "${file}"; then
			# after 단계에서 적용할 마이그레이션을 여기서 미리 넣으면 before 가 이미 변경된 상태가 된다.
			echo "  건너뜀: ${base} (after 단계에서 적용)"
			continue
		fi
		echo "  적용: ${base}"
		mysql_perf_file "${file}"
	done
}

# V12 가 after 단계에서야 product.sold_quantity 컬럼을 만들기 때문에, 시드 INSERT 에는 이 컬럼 값을
# 리터럴로 박지 않고 여기서 실제 백필 문장(V12 와 동일)을 한 번 돌려 분포를 맞춘다. --after-ddl 로
# V12 를 안 넣은 조합에서는 컬럼 자체가 없어 그대로 돌리면 에러가 나므로, 컬럼 존재를 먼저 확인한다.
backfill_sold_quantity() {
	local has_column
	has_column=$(mysql_perf "SELECT COUNT(*) FROM information_schema.columns
		WHERE table_schema = '${PERF_SCHEMA}' AND table_name = 'product' AND column_name = 'sold_quantity';")
	if [ "${has_column}" != "1" ]; then
		return 0
	fi
	echo "  - product.sold_quantity 백필"
	mysql_perf "
		update product p
		join (select oi.product_id, sum(oi.quantity) as q
		      from order_item oi join orders o on o.id = oi.order_id
		      where o.status in ('PAID', 'PREPARING', 'SHIPPED', 'DELIVERED')
		      group by oi.product_id) s on s.product_id = p.id
		set p.sold_quantity = s.q;
	"
}

seed() {
	local member_n artist_n label_n album_n product_n orders_n order_item_n payment_n
	local review_n notification_n wishlist_n coupon_n member_coupon_n
	local review_heavy_n notification_heavy_n
	# review 헤비 케이스가 product 1 에 이미 쓴 member_id 1~6 과 겹치지 않도록 회원 수를 넉넉히 늘린다.
	member_n=$(scaled_count 40000)
	artist_n=$(scaled_count 2000)
	label_n=$(scaled_count 200)
	album_n=$(scaled_count 30000)
	product_n=$(scaled_count 50000)
	orders_n=$(scaled_count 200000)
	order_item_n=$(scaled_count 500000)
	payment_n=$(scaled_count 150000)
	review_n=$(scaled_count 300000)
	notification_n=$(scaled_count 500000)
	wishlist_n=$(scaled_count 100000)
	coupon_n=$(scaled_count 500)
	member_coupon_n=$(scaled_count 20000)
	review_heavy_n=$(scaled_count 20000)
	notification_heavy_n=$(scaled_count 50000)

	# payment.order_id 를 n 그대로 1:1 매핑하므로 orders 건수를 넘으면 FK 위반이 난다.
	if [ "${payment_n}" -gt "${orders_n}" ]; then
		payment_n="${orders_n}"
	fi

	# member_coupon.member_id 를 n 그대로 매핑하므로 회원 수를 넘으면 FK 위반이 난다.
	if [ "${member_coupon_n}" -gt "${member_n}" ]; then
		member_coupon_n="${member_n}"
	fi

	local max_needed=0 c
	for c in "${member_n}" "${artist_n}" "${label_n}" "${album_n}" "${product_n}" \
		"${orders_n}" "${order_item_n}" "${payment_n}" "${review_n}" "${notification_n}" "${wishlist_n}" \
		"${coupon_n}" "${member_coupon_n}" "${review_heavy_n}" "${notification_heavy_n}"; do
		if [ "${c}" -gt "${max_needed}" ]; then
			max_needed="${c}"
		fi
	done
	if [ "${max_needed}" -gt 1000000 ]; then
		echo "scale 이 너무 커서 numbers 헬퍼 테이블(1,000,000행)을 초과합니다. --scale 을 낮추세요." >&2
		exit 1
	fi

	echo "[시드] numbers 헬퍼 테이블 생성 (재귀 CTE 1000행 + 자기 교차조인으로 1,000,000행까지 확장)"
	mysql_perf "
		SET SESSION cte_max_recursion_depth = 4000000;
		DROP TABLE IF EXISTS seq1k;
		CREATE TABLE seq1k (n INT PRIMARY KEY) ENGINE=InnoDB;
		INSERT INTO seq1k (n)
		WITH RECURSIVE seq(n) AS (
			SELECT 1
			UNION ALL
			SELECT n + 1 FROM seq WHERE n < 1000
		)
		SELECT n FROM seq;
		DROP TABLE IF EXISTS numbers;
		CREATE TABLE numbers (n BIGINT PRIMARY KEY) ENGINE=InnoDB;
		INSERT INTO numbers (n)
		SELECT (a.n - 1) * 1000 + b.n
		FROM seq1k a CROSS JOIN seq1k b;
		DROP TABLE seq1k;
	"

	mysql_perf "
		INSERT INTO member (email, password, nickname, role, status, created_at, updated_at)
		SELECT
			CONCAT('perf', n, '@groove.local'),
			'perfdummypasswordhash0000000000000000000000000000',
			CONCAT('digger', n),
			'USER',
			IF(n % 50 = 0, 'SUSPENDED', 'ACTIVE'),
			DATE_SUB(NOW(6), INTERVAL (n % 730) DAY),
			NOW(6)
		FROM numbers WHERE n <= ${member_n};
	"
	echo "[시드] member ${member_n}건"

	mysql_perf "
		INSERT INTO artist (name, name_en, description, created_at, updated_at)
		SELECT CONCAT('Perf Artist ', n), NULL, NULL, NOW(6), NOW(6)
		FROM numbers WHERE n <= ${artist_n};
	"
	echo "[시드] artist ${artist_n}건"

	mysql_perf "
		INSERT INTO label (name, country, created_at, updated_at)
		SELECT CONCAT('Perf Label ', n), 'KR', NOW(6), NOW(6)
		FROM numbers WHERE n <= ${label_n};
	"
	echo "[시드] label ${label_n}건"

	mysql_perf "
		INSERT INTO album (title, artist_id, original_release_year, created_at, updated_at)
		SELECT CONCAT('Perf Album ', n), ((n - 1) % ${artist_n}) + 1, 1960 + (n % 60), NOW(6), NOW(6)
		FROM numbers WHERE n <= ${album_n};
	"
	echo "[시드] album ${album_n}건"

	mysql_perf "
		INSERT INTO product (title, album_id, artist_id, label_id, pressing_info, color_variant, country,
			pressing_year, catalog_no, catalog_no_normalized, barcode, edition_type, price, status,
			avg_rating, review_count, created_at, updated_at)
		SELECT
			CONCAT('Perf Pressing ', n),
			((n - 1) % ${album_n}) + 1,
			((n - 1) % ${artist_n}) + 1,
			((n - 1) % ${label_n}) + 1,
			'180g',
			'Black',
			ELT(1 + (n % 5), 'US', 'South Korea', 'Japan', 'Germany', 'France'),
			1990 + (n % 34),
			CONCAT('PERF-', n),
			CONCAT('PERF', n),
			LPAD(9000000000000 + n, 13, '0'),
			ELT(1 + (n % 6), 'STANDARD', 'ORIGINAL', 'REISSUE', 'REMASTER', 'LIMITED', 'PROMO'),
			15000 + (n * 991) % 85000,
			CASE WHEN n % 100 < 92 THEN 'ON_SALE' WHEN n % 100 < 97 THEN 'SOLD_OUT' ELSE 'HIDDEN' END,
			1.0 + (n % 41) / 10,
			n % 50,
			DATE_SUB(NOW(6), INTERVAL (n % 730) DAY),
			NOW(6)
		FROM numbers WHERE n <= ${product_n};
	"
	echo "[시드] product ${product_n}건"

	GENRE_ID_1=$(mysql_perf "SELECT MIN(id) FROM genre;")
	local genre_count
	genre_count=$(mysql_perf "SELECT COUNT(*) FROM genre;")
	GENRE_ID_2=$((GENRE_ID_1 + 1))

	mysql_perf "
		INSERT INTO product_genre (product_id, genre_id)
		SELECT p.n, ${GENRE_ID_1} + ((p.n + g.offset - 1) % ${genre_count})
		FROM numbers p
		CROSS JOIN (SELECT 0 AS offset UNION ALL SELECT 1) g
		WHERE p.n <= ${product_n};
	"
	echo "[시드] product_genre $((product_n * 2))건"

	mysql_perf "
		INSERT INTO product_image (product_id, image_url, sort_order, created_at, updated_at)
		SELECT n, CONCAT('https://cdn.groove.local/perf/', n, '.jpg'), 0, NOW(6), NOW(6)
		FROM numbers WHERE n <= ${product_n};
	"
	echo "[시드] product_image ${product_n}건"

	mysql_perf "
		INSERT INTO coupon (code, name, discount_type, discount_value, max_discount_amount, min_order_amount,
			total_quantity, issued_count, status, version, expires_at, created_at, updated_at)
		SELECT
			CONCAT('PERF', n),
			CONCAT('Perf Coupon ', n),
			IF(n % 2 = 0, 'FIXED', 'RATE'),
			IF(n % 2 = 0, 1000 + (n * 137) % 9000, 5 + (n % 20)),
			IF(n % 2 = 0, NULL, 10000 + (n * 271) % 40000),
			(n % 10) * 5000,
			1000 + (n % 5000),
			(n % 500),
			'ACTIVE',
			0,
			DATE_ADD(NOW(6), INTERVAL 1 + (n % 365) DAY),
			NOW(6),
			NOW(6)
		FROM numbers WHERE n <= ${coupon_n};
	"
	echo "[시드] coupon ${coupon_n}건"

	# O1/O2 가 hash join 이 아니라 정상적인 인덱스 접근으로 풀리려면 coupon/member_coupon 이 비어있으면 안 된다.
	mysql_perf "
		INSERT INTO member_coupon (member_id, coupon_id, used, issued_at, used_at, used_order_id,
			created_at, updated_at)
		SELECT
			n,
			((n - 1) % ${coupon_n}) + 1,
			IF(n % 5 = 0, 1, 0),
			DATE_SUB(NOW(6), INTERVAL (n % 365) DAY),
			IF(n % 5 = 0, DATE_SUB(NOW(6), INTERVAL (n % 300) DAY), NULL),
			IF(n % 5 = 0, n, NULL),
			NOW(6),
			NOW(6)
		FROM numbers WHERE n <= ${member_coupon_n};
	"
	echo "[시드] member_coupon ${member_coupon_n}건 (member_id=n 이라 uk_member_coupon_member_coupon 이 저절로 지켜진다)"

	mysql_perf "
		INSERT INTO orders (order_number, member_id, total_amount, discount_amount, final_amount, status,
			zip_code, phone, recipient_name, address1, address2, member_coupon_id, expires_at, created_at,
			updated_at)
		SELECT
			CONCAT('PERF-', t.n),
			t.mid,
			15000 + (t.n * 777) % 200000,
			IF(t.n % 100 < 30, 1000 + (t.n * 137) % 9000, 0),
			15000 + (t.n * 777) % 200000 - IF(t.n % 100 < 30, 1000 + (t.n * 137) % 9000, 0),
			-- 회원 쏠림 배정도 n % 100 을 쓰므로 그대로 두면 특정 회원 status 가 한 값에 고정된다
			CASE
				WHEN (t.n + t.n DIV 100) % 100 < 5 THEN 'PENDING'
				WHEN (t.n + t.n DIV 100) % 100 < 25 THEN 'PAID'
				WHEN (t.n + t.n DIV 100) % 100 < 40 THEN 'PREPARING'
				WHEN (t.n + t.n DIV 100) % 100 < 60 THEN 'SHIPPED'
				WHEN (t.n + t.n DIV 100) % 100 < 90 THEN 'DELIVERED'
				WHEN (t.n + t.n DIV 100) % 100 < 98 THEN 'CANCELED'
				ELSE 'REFUNDED'
			END,
			LPAD(t.n % 100000, 5, '0'),
			CONCAT('010-0000-', LPAD(t.n % 10000, 4, '0')),
			CONCAT('Perf Recipient ', t.n),
			CONCAT('Perf Address ', t.n),
			NULL,
			-- FK 를 만족하도록 이 회원(t.mid) 소유의 member_coupon.id 만 모듈러로 골라 30%에 배정한다
			IF(t.n % 100 < 30, ((t.mid - 1) % ${member_coupon_n}) + 1, NULL),
			DATE_ADD(DATE_SUB(NOW(6), INTERVAL (t.n % 730) DAY), INTERVAL 30 MINUTE),
			DATE_SUB(NOW(6), INTERVAL (t.n % 730) DAY),
			NOW(6)
		FROM (
			SELECT n, CASE WHEN n % 10 < 3 THEN (n % 100) + 1 ELSE (n % ${member_n}) + 1 END AS mid
			FROM numbers WHERE n <= ${orders_n}
		) t;
	"
	echo "[시드] orders ${orders_n}건"

	mysql_perf "
		INSERT INTO order_item (order_id, product_id, quantity, price_snapshot, product_name_snapshot,
			created_at, updated_at)
		SELECT
			(n % ${orders_n}) + 1,
			((n * 7919) % ${product_n}) + 1,
			1 + (n % 3),
			15000 + ((((n * 7919) % ${product_n}) + 1) * 991) % 85000,
			CONCAT('Perf Pressing ', ((n * 7919) % ${product_n}) + 1),
			NOW(6),
			NOW(6)
		FROM numbers WHERE n <= ${order_item_n};
	"
	echo "[시드] order_item ${order_item_n}건"

	mysql_perf "
		INSERT INTO payment (order_id, payment_key, toss_order_id, method, amount, status, approved_at,
			canceled_at, fail_reason, created_at, updated_at)
		SELECT
			n,
			CASE WHEN n % 100 < 98 THEN CONCAT('perf_payment_key_', n) ELSE NULL END,
			CONCAT('PERF-TOSS-', n),
			'CARD',
			15000 + (n * 991) % 85000,
			CASE WHEN n % 100 < 90 THEN 'DONE' WHEN n % 100 < 98 THEN 'CANCELED' ELSE 'FAILED' END,
			CASE WHEN n % 100 < 98 THEN DATE_SUB(NOW(6), INTERVAL (n % 730) DAY) ELSE NULL END,
			CASE WHEN n % 100 >= 90 AND n % 100 < 98
				THEN DATE_ADD(DATE_SUB(NOW(6), INTERVAL (n % 730) DAY), INTERVAL 1 DAY)
				ELSE NULL END,
			CASE WHEN n % 100 >= 98 THEN 'PERF_TEST_FAILURE' ELSE NULL END,
			NOW(6),
			NOW(6)
		FROM numbers WHERE n <= ${payment_n};
	"
	echo "[시드] payment ${payment_n}건"

	mysql_perf "
		INSERT INTO review (rating, title, content, product_id, member_id, created_at, updated_at)
		SELECT
			1 + (n % 5),
			CONCAT('Perf Review ', n),
			'perf test review content',
			((n - 1) % ${product_n}) + 1,
			FLOOR((n - 1) / ${product_n}) + 1,
			DATE_SUB(NOW(6), INTERVAL (n % 730) DAY),
			NOW(6)
		FROM numbers WHERE n <= ${review_n};
	"
	echo "[시드] review ${review_n}건"

	mysql_perf "
		-- filesort 대상을 키우는 헤비 케이스: product_id=1 에 대량 추가. 위 기본 시드가 product 1 에
		-- 이미 쓴 member_id 1~6 과 겹치지 않도록 회원 상위 구간만 쓴다
		INSERT INTO review (rating, title, content, product_id, member_id, created_at, updated_at)
		SELECT
			1 + (n % 5),
			CONCAT('Perf Heavy Review ', n),
			'perf heavy review content',
			1,
			${member_n} - ${review_heavy_n} + n,
			DATE_SUB(NOW(6), INTERVAL (n % 730) DAY),
			NOW(6)
		FROM numbers WHERE n <= ${review_heavy_n};
	"
	echo "[시드] review(product_id=1 헤비) ${review_heavy_n}건"

	mysql_perf "
		INSERT INTO notification (member_id, type, product_id, album_id, title_snapshot, read_at,
			created_at, updated_at)
		SELECT
			(n % ${member_n}) + 1,
			IF(n % 2 = 0, 'RESTOCK', 'NEW_PRESSING'),
			((n - 1) % ${product_n}) + 1,
			NULL,
			CONCAT('Perf Notification ', n),
			IF(n % 10 < 3, NULL, DATE_SUB(NOW(6), INTERVAL (n % 730) DAY)),
			DATE_SUB(NOW(6), INTERVAL (n % 730) DAY),
			NOW(6)
		FROM numbers WHERE n <= ${notification_n};
	"
	echo "[시드] notification ${notification_n}건"

	mysql_perf "
		-- filesort 헤비 케이스: member_id=1 에 대량 추가. 안읽음 비율을 1%로 낮춰 인덱스가 read_at 필터로
		-- 얼마나 걸러내는지 드러낸다
		INSERT INTO notification (member_id, type, product_id, album_id, title_snapshot, read_at,
			created_at, updated_at)
		SELECT
			1,
			IF(n % 2 = 0, 'RESTOCK', 'NEW_PRESSING'),
			((n - 1) % ${product_n}) + 1,
			NULL,
			CONCAT('Perf Heavy Notification ', n),
			IF(n % 100 < 1, NULL, DATE_SUB(NOW(6), INTERVAL (n % 730) DAY)),
			DATE_SUB(NOW(6), INTERVAL (n % 730) DAY),
			NOW(6)
		FROM numbers WHERE n <= ${notification_heavy_n};
	"
	echo "[시드] notification(member_id=1 헤비) ${notification_heavy_n}건"

	mysql_perf "
		INSERT INTO wishlist (member_id, product_id, alert_enabled, created_at, updated_at)
		SELECT
			FLOOR((n - 1) / ${product_n}) + 1,
			((n - 1) % ${product_n}) + 1,
			1,
			DATE_SUB(NOW(6), INTERVAL (n % 730) DAY),
			NOW(6)
		FROM numbers WHERE n <= ${wishlist_n};
	"
	echo "[시드] wishlist ${wishlist_n}건"
}

analyze_tables() {
	echo "[통계] ANALYZE TABLE 실행"
	mysql_perf "ANALYZE TABLE member, artist, label, genre, album, product, product_genre, product_image,
		orders, order_item, payment, review, notification, wishlist, coupon, member_coupon;" > /dev/null
}

# EXPLAIN ANALYZE 결과 텍스트의 첫 줄에서 접근 방식과 마지막 actual time 값을 뽑는다.
# 파싱이 실패해도(형식이 바뀌는 등) 스크립트가 죽지 않게 항상 값을 채워 반환한다.
parse_summary() {
	local text="$1"
	local first_line access time_val flags=""
	first_line=$(printf '%s\n' "${text}" | head -n 1)
	access=$(printf '%s' "${first_line}" | grep -oE '^-> [^(]+' | sed -E 's/^-> //; s/ +$//' || true)
	time_val=$(printf '%s' "${first_line}" | grep -oE 'actual time=[0-9.e+-]+\.\.[0-9.e+-]+' \
		| sed -E 's/.*\.\.//' || true)
	# 'sort' 로 찾으면 idx_product_image_product 의 sort_order 컬럼에 걸린다. 계획 노드 'Sort:' 만 본다.
	if printf '%s' "${text}" | grep -q 'Sort:'; then
		flags="${flags}Sort "
	fi
	if printf '%s' "${text}" | grep -qi 'temporary table'; then
		flags="${flags}Temp "
	fi
	[ -z "${access}" ] && access="(파싱 실패)"
	[ -z "${time_val}" ] && time_val="?"
	if [ -n "${flags}" ]; then
		echo "${access} / ${time_val}ms (${flags% })"
	else
		echo "${access} / ${time_val}ms"
	fi
}

# 케이스별 SQL. mapper XML/리포지토리에서 그대로 복사하고 파라미터만 리터럴로 치환했다.
# phase 는 대부분 케이스에서 안 쓰인다(before/after 가 같은 SQL 을 쓰고 인덱스 DDL 만 다름).
# P5 처럼 쿼리 자체를 재작성하는 케이스만 phase 로 갈라 서로 다른 SQL 을 낸다.
get_case_sql() {
	local id="$1" phase="${2:-after}"
	case "${id}" in
	P1)
		cat <<-SQL
			-- ProductSearchMapper.xml searchProducts, sort=LATEST, 필터 없음, memberId=NULL
			SELECT
				p.id, p.title, a.name AS artist_name, l.name AS label_name, p.price,
				p.color_variant, p.pressing_info, p.status,
				(SELECT MIN(i.image_url) FROM product_image i WHERE i.product_id = p.id AND i.sort_order = 0)
					AS thumbnail_url,
				p.avg_rating AS average_rating, p.review_count AS review_count,
				p.country AS country, p.pressing_year AS pressing_year, p.edition_type AS edition_type,
				NULL AS wishlisted
			FROM product p
			JOIN artist a ON a.id = p.artist_id
			LEFT JOIN label l ON l.id = p.label_id
			WHERE p.status <> 'HIDDEN'
			ORDER BY p.created_at DESC, p.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	P2)
		cat <<-SQL
			-- ProductSearchMapper.xml searchProducts, sort=LATEST, keyword='Pressing 12'
			SELECT
				p.id, p.title, a.name AS artist_name, l.name AS label_name, p.price,
				p.color_variant, p.pressing_info, p.status,
				(SELECT MIN(i.image_url) FROM product_image i WHERE i.product_id = p.id AND i.sort_order = 0)
					AS thumbnail_url,
				p.avg_rating AS average_rating, p.review_count AS review_count,
				p.country AS country, p.pressing_year AS pressing_year, p.edition_type AS edition_type,
				NULL AS wishlisted
			FROM product p
			JOIN artist a ON a.id = p.artist_id
			LEFT JOIN label l ON l.id = p.label_id
			WHERE p.status <> 'HIDDEN'
			AND (p.title LIKE '%Pressing 12%' OR a.name LIKE '%Pressing 12%')
			ORDER BY p.created_at DESC, p.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	P3)
		cat <<-SQL
			-- ProductSearchMapper.xml searchProducts, sort=LATEST, genreIds=(${GENRE_ID_1},${GENRE_ID_2}),
			-- price 30000~60000
			SELECT
				p.id, p.title, a.name AS artist_name, l.name AS label_name, p.price,
				p.color_variant, p.pressing_info, p.status,
				(SELECT MIN(i.image_url) FROM product_image i WHERE i.product_id = p.id AND i.sort_order = 0)
					AS thumbnail_url,
				p.avg_rating AS average_rating, p.review_count AS review_count,
				p.country AS country, p.pressing_year AS pressing_year, p.edition_type AS edition_type,
				NULL AS wishlisted
			FROM product p
			JOIN artist a ON a.id = p.artist_id
			LEFT JOIN label l ON l.id = p.label_id
			WHERE p.status <> 'HIDDEN'
			AND EXISTS (
				SELECT 1 FROM product_genre pg
				WHERE pg.product_id = p.id
				AND pg.genre_id IN (${GENRE_ID_1}, ${GENRE_ID_2})
			)
			AND p.price >= 30000 AND p.price <= 60000
			ORDER BY p.created_at DESC, p.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	P4)
		cat <<-SQL
			-- ProductSearchMapper.xml searchProducts, sort=PRICE_ASC, price 30000~60000
			SELECT
				p.id, p.title, a.name AS artist_name, l.name AS label_name, p.price,
				p.color_variant, p.pressing_info, p.status,
				(SELECT MIN(i.image_url) FROM product_image i WHERE i.product_id = p.id AND i.sort_order = 0)
					AS thumbnail_url,
				p.avg_rating AS average_rating, p.review_count AS review_count,
				p.country AS country, p.pressing_year AS pressing_year, p.edition_type AS edition_type,
				NULL AS wishlisted
			FROM product p
			JOIN artist a ON a.id = p.artist_id
			LEFT JOIN label l ON l.id = p.label_id
			WHERE p.status <> 'HIDDEN'
			AND p.price >= 30000 AND p.price <= 60000
			ORDER BY p.price ASC, p.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	P5)
		if [ "${phase}" = before ]; then
			cat <<-SQL
				-- ProductSearchMapper.xml searchProducts, sort=POPULAR (before: order_item 파생 테이블 집계)
				SELECT
					p.id, p.title, a.name AS artist_name, l.name AS label_name, p.price,
					p.color_variant, p.pressing_info, p.status,
					(SELECT MIN(i.image_url) FROM product_image i WHERE i.product_id = p.id AND i.sort_order = 0)
						AS thumbnail_url,
					p.avg_rating AS average_rating, p.review_count AS review_count,
					p.country AS country, p.pressing_year AS pressing_year, p.edition_type AS edition_type,
					NULL AS wishlisted
				FROM product p
				JOIN artist a ON a.id = p.artist_id
				LEFT JOIN label l ON l.id = p.label_id
				LEFT JOIN (
					SELECT oi.product_id, SUM(oi.quantity) AS sold_quantity
					FROM order_item oi
					JOIN orders o ON o.id = oi.order_id
					WHERE o.status IN ('PAID', 'PREPARING', 'SHIPPED', 'DELIVERED')
					GROUP BY oi.product_id
				) sold ON sold.product_id = p.id
				WHERE p.status <> 'HIDDEN'
				ORDER BY COALESCE(sold.sold_quantity, 0) DESC, p.review_count DESC, p.created_at DESC, p.id DESC
				LIMIT 20 OFFSET 0
			SQL
		else
			cat <<-SQL
				-- ProductSearchMapper.xml searchProducts, sort=POPULAR (after: product.sold_quantity 비정규화 컬럼)
				SELECT
					p.id, p.title, a.name AS artist_name, l.name AS label_name, p.price,
					p.color_variant, p.pressing_info, p.status,
					(SELECT MIN(i.image_url) FROM product_image i WHERE i.product_id = p.id AND i.sort_order = 0)
						AS thumbnail_url,
					p.avg_rating AS average_rating, p.review_count AS review_count,
					p.country AS country, p.pressing_year AS pressing_year, p.edition_type AS edition_type,
					NULL AS wishlisted
				FROM product p
				JOIN artist a ON a.id = p.artist_id
				LEFT JOIN label l ON l.id = p.label_id
				WHERE p.status <> 'HIDDEN'
				ORDER BY p.sold_quantity DESC, p.review_count DESC, p.created_at DESC, p.id DESC
				LIMIT 20 OFFSET 0
			SQL
		fi
		;;
	P6)
		cat <<-SQL
			-- ProductSearchMapper.xml searchProducts, sort=RATING, 깊은 페이지 offset=1000
			SELECT
				p.id, p.title, a.name AS artist_name, l.name AS label_name, p.price,
				p.color_variant, p.pressing_info, p.status,
				(SELECT MIN(i.image_url) FROM product_image i WHERE i.product_id = p.id AND i.sort_order = 0)
					AS thumbnail_url,
				p.avg_rating AS average_rating, p.review_count AS review_count,
				p.country AS country, p.pressing_year AS pressing_year, p.edition_type AS edition_type,
				NULL AS wishlisted
			FROM product p
			JOIN artist a ON a.id = p.artist_id
			LEFT JOIN label l ON l.id = p.label_id
			WHERE p.status <> 'HIDDEN'
			ORDER BY p.avg_rating DESC, p.review_count DESC, p.created_at DESC, p.id DESC
			LIMIT 20 OFFSET 1000
		SQL
		;;
	P7)
		cat <<-SQL
			-- ProductSearchMapper.xml countProducts, 무필터
			SELECT COUNT(*)
			FROM product p
			JOIN artist a ON a.id = p.artist_id
			LEFT JOIN label l ON l.id = p.label_id
			WHERE p.status <> 'HIDDEN'
		SQL
		;;
	O1)
		cat <<-SQL
			-- OrderQueryMapper.xml findMyOrders, member_id=${TARGET_MEMBER_ID}, status 없음
			SELECT
				o.id, o.order_number, o.status, o.final_amount, o.discount_amount, c.name AS coupon_name,
				o.created_at,
				(SELECT oi.product_name_snapshot FROM order_item oi
					WHERE oi.order_id = o.id ORDER BY oi.id LIMIT 1) AS representative_product_name,
				(SELECT COUNT(*) FROM order_item oi WHERE oi.order_id = o.id) AS item_count,
				(SELECT MIN(pi.image_url) FROM product_image pi
					WHERE pi.sort_order = 0
					AND pi.product_id = (SELECT oi.product_id FROM order_item oi
						WHERE oi.order_id = o.id ORDER BY oi.id LIMIT 1)) AS thumbnail_url
			FROM orders o
			LEFT JOIN member_coupon mc ON mc.id = o.member_coupon_id
			LEFT JOIN coupon c ON c.id = mc.coupon_id
			WHERE o.member_id = ${TARGET_MEMBER_ID}
			ORDER BY o.created_at DESC, o.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	O2)
		cat <<-SQL
			-- OrderQueryMapper.xml findMyOrders, member_id=${TARGET_MEMBER_ID}, status=DELIVERED
			SELECT
				o.id, o.order_number, o.status, o.final_amount, o.discount_amount, c.name AS coupon_name,
				o.created_at,
				(SELECT oi.product_name_snapshot FROM order_item oi
					WHERE oi.order_id = o.id ORDER BY oi.id LIMIT 1) AS representative_product_name,
				(SELECT COUNT(*) FROM order_item oi WHERE oi.order_id = o.id) AS item_count,
				(SELECT MIN(pi.image_url) FROM product_image pi
					WHERE pi.sort_order = 0
					AND pi.product_id = (SELECT oi.product_id FROM order_item oi
						WHERE oi.order_id = o.id ORDER BY oi.id LIMIT 1)) AS thumbnail_url
			FROM orders o
			LEFT JOIN member_coupon mc ON mc.id = o.member_coupon_id
			LEFT JOIN coupon c ON c.id = mc.coupon_id
			WHERE o.member_id = ${TARGET_MEMBER_ID}
			AND o.status = 'DELIVERED'
			ORDER BY o.created_at DESC, o.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	O3)
		cat <<-SQL
			-- OrderQueryMapper.xml countMyOrders, member_id=${TARGET_MEMBER_ID}
			SELECT COUNT(*)
			FROM orders o
			WHERE o.member_id = ${TARGET_MEMBER_ID}
		SQL
		;;
	A1)
		cat <<-SQL
			-- OrderQueryMapper.xml findAdminOrders, 무필터
			SELECT
				o.id, o.order_number, m.email AS member_email, o.status, o.final_amount, o.created_at,
				(SELECT COUNT(*) FROM order_item oi WHERE oi.order_id = o.id) AS item_count
			FROM orders o
			JOIN member m ON m.id = o.member_id
			ORDER BY o.created_at DESC, o.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	A2)
		cat <<-SQL
			-- OrderQueryMapper.xml findAdminOrders, status=PAID
			SELECT
				o.id, o.order_number, m.email AS member_email, o.status, o.final_amount, o.created_at,
				(SELECT COUNT(*) FROM order_item oi WHERE oi.order_id = o.id) AS item_count
			FROM orders o
			JOIN member m ON m.id = o.member_id
			WHERE o.status = 'PAID'
			ORDER BY o.created_at DESC, o.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	A3)
		cat <<-SQL
			-- OrderQueryMapper.xml findAdminOrders, created_at 최근 30일
			SELECT
				o.id, o.order_number, m.email AS member_email, o.status, o.final_amount, o.created_at,
				(SELECT COUNT(*) FROM order_item oi WHERE oi.order_id = o.id) AS item_count
			FROM orders o
			JOIN member m ON m.id = o.member_id
			WHERE o.created_at >= DATE_SUB(NOW(6), INTERVAL 30 DAY)
			AND o.created_at < NOW(6)
			ORDER BY o.created_at DESC, o.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	A4)
		cat <<-SQL
			-- OrderQueryMapper.xml findAdminOrders, keyword='perf12'
			SELECT
				o.id, o.order_number, m.email AS member_email, o.status, o.final_amount, o.created_at,
				(SELECT COUNT(*) FROM order_item oi WHERE oi.order_id = o.id) AS item_count
			FROM orders o
			JOIN member m ON m.id = o.member_id
			WHERE (m.email LIKE '%perf12%' OR o.order_number LIKE '%perf12%')
			ORDER BY o.created_at DESC, o.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	A5)
		cat <<-SQL
			-- OrderQueryMapper.xml countAdminOrders, 무필터
			SELECT COUNT(*)
			FROM orders o
			JOIN member m ON m.id = o.member_id
		SQL
		;;
	R1)
		cat <<-SQL
			-- ReviewRepository.findByProductId, @EntityGraph(member), ReviewSortType.LATEST, product_id=${TARGET_PRODUCT_ID}
			SELECT r.id, r.rating, r.title, r.content, r.created_at, r.updated_at, r.product_id, r.member_id,
				m.id, m.email, m.nickname, m.role, m.status, m.created_at, m.updated_at
			FROM review r
			JOIN member m ON m.id = r.member_id
			WHERE r.product_id = ${TARGET_PRODUCT_ID}
			ORDER BY r.created_at DESC, r.id DESC
			LIMIT 10 OFFSET 0
		SQL
		;;
	R2)
		cat <<-SQL
			-- ReviewRepository.findByProductId, @EntityGraph(member), ReviewSortType.RATING_DESC, product_id=${TARGET_PRODUCT_ID}
			SELECT r.id, r.rating, r.title, r.content, r.created_at, r.updated_at, r.product_id, r.member_id,
				m.id, m.email, m.nickname, m.role, m.status, m.created_at, m.updated_at
			FROM review r
			JOIN member m ON m.id = r.member_id
			WHERE r.product_id = ${TARGET_PRODUCT_ID}
			ORDER BY r.rating DESC, r.created_at DESC, r.id DESC
			LIMIT 10 OFFSET 0
		SQL
		;;
	R3)
		cat <<-SQL
			-- ReviewRepository 파생 count, product_id=${TARGET_PRODUCT_ID}
			SELECT COUNT(*) FROM review WHERE product_id = ${TARGET_PRODUCT_ID}
		SQL
		;;
	R4)
		cat <<-SQL
			-- ReviewRepository.countByRatingForProduct, product_id=${TARGET_PRODUCT_ID}
			SELECT r.rating, COUNT(r.id)
			FROM review r
			WHERE r.product_id = ${TARGET_PRODUCT_ID}
			GROUP BY r.rating
		SQL
		;;
	N1)
		cat <<-SQL
			-- NotificationRepository.findAllByMemberId, member_id=${TARGET_MEMBER_ID}
			SELECT n.id, n.member_id, n.product_id, n.album_id, n.type, n.title_snapshot, n.read_at,
				n.created_at, n.updated_at
			FROM notification n
			WHERE n.member_id = ${TARGET_MEMBER_ID}
			ORDER BY n.created_at DESC, n.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	N2)
		cat <<-SQL
			-- NotificationRepository.findAllByMemberIdAndReadAtIsNull, member_id=${TARGET_MEMBER_ID}
			SELECT n.id, n.member_id, n.product_id, n.album_id, n.type, n.title_snapshot, n.read_at,
				n.created_at, n.updated_at
			FROM notification n
			WHERE n.member_id = ${TARGET_MEMBER_ID}
			AND n.read_at IS NULL
			ORDER BY n.created_at DESC, n.id DESC
			LIMIT 20 OFFSET 0
		SQL
		;;
	N3)
		cat <<-SQL
			-- NotificationRepository.countByMemberIdAndReadAtIsNull, member_id=${TARGET_MEMBER_ID}
			SELECT COUNT(*) FROM notification WHERE member_id = ${TARGET_MEMBER_ID} AND read_at IS NULL
		SQL
		;;
	*)
		echo "알 수 없는 케이스: ${id}" >&2
		exit 1
		;;
	esac
}

run_case() {
	local phase="$1" id="$2"
	local sql tree analyze summary i
	sql="$(get_case_sql "${id}" "${phase}")"

	for i in 1 2 3; do
		mysql_perf "${sql}" > /dev/null
	done

	tree=$(mysql_perf "EXPLAIN FORMAT=TREE ${sql}")
	analyze=$(mysql_perf "EXPLAIN ANALYZE ${sql}")
	summary=$(parse_summary "${analyze}")
	set_summary "${phase}" "${id}" "${summary}"

	{
		echo "#### ${id} / ${phase}"
		echo
		echo '```sql'
		echo "${sql}"
		echo '```'
		echo
		echo "EXPLAIN FORMAT=TREE"
		echo '```'
		echo "${tree}"
		echo '```'
		echo
		echo "EXPLAIN ANALYZE"
		echo '```'
		echo "${analyze}"
		echo '```'
		echo
	} >> "${BODY_TMP}"
}

report() {
	echo "# 인덱스 실행계획 리포트"
	echo
	echo "- 생성 시각: $(date '+%Y-%m-%d %H:%M:%S %Z')"
	echo "- scale: ${SCALE}"
	if [ "${RUN_AFTER}" = true ]; then
		echo "- after-ddl: ${AFTER_DDLS[*]}"
	else
		echo "- after-ddl: 없음 (지정한 파일이 없어 before 만 측정)"
	fi
	echo
	echo "## 요약"
	echo
	echo "| 케이스 | 설명 | before 접근/시간 | after 접근/시간 |"
	echo "|---|---|---|---|"
	local id
	for id in "${CASE_IDS[@]}"; do
		echo "| ${id} | $(case_desc "${id}") | $(get_summary before "${id}") | $(get_summary after "${id}") |"
	done
	echo
	echo "## 케이스 상세"
	echo
	cat "${BODY_TMP}"
}

main() {
	echo "[1/6] ${PERF_SCHEMA} 스키마 초기화 (개발 DB groove 는 건드리지 않는다)"
	mysql_root "DROP DATABASE IF EXISTS ${PERF_SCHEMA};
		CREATE DATABASE ${PERF_SCHEMA} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

	echo "[2/6] Flyway 마이그레이션 적용"
	apply_migrations

	echo "[3/6] 시드 데이터 적재 (scale=${SCALE})"
	local seed_start seed_end
	seed_start=$(date +%s)
	seed
	seed_end=$(date +%s)
	echo "[시드] 총 소요 시간: $((seed_end - seed_start))초"

	echo "[4/6] 테이블 통계 갱신"
	analyze_tables

	echo "[5/6] 실행계획 측정"
	BODY_TMP=$(mktemp)
	if [ "${RUN_BEFORE}" = true ]; then
		echo "  - before 단계"
		local id
		for id in "${CASE_IDS[@]}"; do
			run_case before "${id}"
		done
	fi
	if [ "${RUN_AFTER}" = true ]; then
		local ddl
		for ddl in $(printf '%s\n' "${AFTER_DDLS[@]}" | sort -V); do
			echo "  - after-ddl 적용: ${ddl}"
			mysql_perf_file "${ddl}"
		done
		backfill_sold_quantity
		analyze_tables
		echo "  - after 단계"
		local id2
		for id2 in "${CASE_IDS[@]}"; do
			run_case after "${id2}"
		done
	fi

	echo "[6/6] 리포트 작성"
	REPORT_TMP=$(mktemp)
	report > "${REPORT_TMP}"
	if [ -n "${OUT_FILE}" ]; then
		cp "${REPORT_TMP}" "${OUT_FILE}"
		echo "리포트: ${OUT_FILE}"
	else
		cat "${REPORT_TMP}"
	fi
}

main "$@"
