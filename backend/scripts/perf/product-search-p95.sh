#!/usr/bin/env bash
# GET /api/v1/products 의 p95 응답 시간을 측정한다.
# docker-compose.yml 의 mysql 서비스에 시드 데이터를 멱등하게 채운 뒤, 5가지 조합을 각 20회 호출해 p95(ms)를 구한다.
# 300ms 를 초과하면 실패(exit 1)한다.
set -euo pipefail

MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
MYSQL_USER="${MYSQL_USER:-groove}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-groove1234}"
MYSQL_DATABASE="${MYSQL_DATABASE:-groove}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
SEED_PRODUCT_COUNT=50
WARMUP_REQUESTS=5
REQUESTS_PER_CASE=20
P95_THRESHOLD_MS=300

mysql_exec() {
	docker compose exec -T -e MYSQL_PWD="${MYSQL_PASSWORD}" "${MYSQL_SERVICE}" \
		mysql -u"${MYSQL_USER}" "${MYSQL_DATABASE}" -N -B -e "$1"
}

echo "[1/3] 성능 측정용 시드 데이터 확인 및 적재"

mysql_exec "
INSERT INTO artist (name, name_en, description, created_at, updated_at)
SELECT 'Perf Artist', 'Perf Artist', 'perf test fixture', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM artist WHERE name = 'Perf Artist');
"
mysql_exec "
INSERT INTO label (name, country, created_at, updated_at)
SELECT 'Perf Label', 'KR', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM label WHERE name = 'Perf Label');
"
mysql_exec "
INSERT INTO genre (name, created_at, updated_at)
SELECT 'Perf Jazz', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM genre WHERE name = 'Perf Jazz');
"
mysql_exec "
INSERT INTO genre (name, created_at, updated_at)
SELECT 'Perf Rock', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM genre WHERE name = 'Perf Rock');
"

PERF_ARTIST_ID=$(mysql_exec "SELECT id FROM artist WHERE name = 'Perf Artist' LIMIT 1;")
PERF_LABEL_ID=$(mysql_exec "SELECT id FROM label WHERE name = 'Perf Label' LIMIT 1;")
PERF_JAZZ_ID=$(mysql_exec "SELECT id FROM genre WHERE name = 'Perf Jazz' LIMIT 1;")
PERF_ROCK_ID=$(mysql_exec "SELECT id FROM genre WHERE name = 'Perf Rock' LIMIT 1;")

EXISTING_COUNT=$(mysql_exec "SELECT COUNT(*) FROM product WHERE title LIKE 'Perf Album %';")

if [ "${EXISTING_COUNT}" -lt "${SEED_PRODUCT_COUNT}" ]; then
	TO_INSERT=$((SEED_PRODUCT_COUNT - EXISTING_COUNT))
	echo "  기존 ${EXISTING_COUNT}건, ${TO_INSERT}건 추가 적재"

	START_INDEX=$((EXISTING_COUNT + 1))
	END_INDEX=${SEED_PRODUCT_COUNT}

	VALUES_SQL=""
	for i in $(seq "${START_INDEX}" "${END_INDEX}"); do
		PRICE=$((20000 + (i * 991) % 50000))
		if [ -n "${VALUES_SQL}" ]; then
			VALUES_SQL="${VALUES_SQL},"
		fi
		VALUES_SQL="${VALUES_SQL}('Perf Album ${i}', ${PERF_ARTIST_ID}, ${PERF_LABEL_ID}, '180g', 'Black', ${PRICE}, 'ON_SALE', NOW(), NOW())"
	done

	mysql_exec "
	INSERT INTO product (title, artist_id, label_id, pressing_info, color_variant, price, status, created_at, updated_at)
	VALUES ${VALUES_SQL};
	"

	mysql_exec "
	INSERT INTO product_genre (product_id, genre_id)
	SELECT p.id, IF(p.id % 2 = 0, ${PERF_JAZZ_ID}, ${PERF_ROCK_ID})
	FROM product p
	WHERE p.title LIKE 'Perf Album %'
		AND NOT EXISTS (SELECT 1 FROM product_genre pg WHERE pg.product_id = p.id);
	"

	mysql_exec "
	INSERT INTO product_image (product_id, image_url, sort_order, created_at, updated_at)
	SELECT p.id, CONCAT('https://cdn.groove.local/perf/', p.id, '.jpg'), 0, NOW(), NOW()
	FROM product p
	WHERE p.title LIKE 'Perf Album %'
		AND NOT EXISTS (SELECT 1 FROM product_image pi WHERE pi.product_id = p.id AND pi.sort_order = 0);
	"
else
	echo "  이미 ${EXISTING_COUNT}건 적재되어 있어 건너뜀"
fi

echo "[2/3] 요청 실행 (워밍업 ${WARMUP_REQUESTS}회 + 케이스당 ${REQUESTS_PER_CASE}회)"

CASES=(
	"/api/v1/products"
	"/api/v1/products?keyword=Perf"
	"/api/v1/products?sort=priceAsc&minPrice=30000&maxPrice=50000"
	"/api/v1/products?genreIds=${PERF_JAZZ_ID}&sort=rating"
	"/api/v1/products?keyword=Album&sort=popular&page=1&size=10"
)

curl_once() {
	local path="$1"
	local response
	response=$(curl -s -o /dev/null -w '%{http_code} %{time_total}' "${BASE_URL}${path}")
	local status="${response%% *}"
	local time_total="${response##* }"
	if [ "${status}" != "200" ]; then
		echo "요청 실패: ${path} (status=${status})" >&2
		exit 1
	fi
	echo "${time_total}"
}

for path in "${CASES[@]}"; do
	for _ in $(seq 1 "${WARMUP_REQUESTS}"); do
		curl_once "${path}" > /dev/null
	done
done

TIMES_FILE=$(mktemp)
trap 'rm -f "${TIMES_FILE}"' EXIT

for path in "${CASES[@]}"; do
	for _ in $(seq 1 "${REQUESTS_PER_CASE}"); do
		curl_once "${path}" >> "${TIMES_FILE}"
	done
done

echo "[3/3] p95 계산"

TOTAL_REQUESTS=$(wc -l < "${TIMES_FILE}" | tr -d ' ')
P95_INDEX=$(( (TOTAL_REQUESTS * 95 + 99) / 100 ))
P95_SECONDS=$(sort -n "${TIMES_FILE}" | sed -n "${P95_INDEX}p")
P95_MS=$(awk -v s="${P95_SECONDS}" 'BEGIN { printf "%.1f", s * 1000 }')

echo "총 ${TOTAL_REQUESTS}건 중 p95 = ${P95_MS}ms (기준 ${P95_THRESHOLD_MS}ms)"

if awk -v p="${P95_MS}" -v t="${P95_THRESHOLD_MS}" 'BEGIN { exit !(p > t) }'; then
	echo "p95 가 기준을 초과했습니다." >&2
	exit 1
fi

echo "통과"
