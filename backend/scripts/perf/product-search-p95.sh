#!/usr/bin/env bash
# GET /api/v1/products 의 p95 응답 시간을 측정한다.
# docker-compose.yml 의 mysql 서비스에 시드 데이터를 멱등하게 채운 뒤, 8가지 조합을 각 20회 호출해 p95(ms)를 구한다.
# 300ms 를 초과하면 실패(exit 1)한다.
# 시드 데이터(Perf 장르/앨범/상품/아티스트/레이블)는 종료 시 cleanup 으로 지운다. 그대로 두면
# GET /api/v1/genres 응답에 섞여 취향 설정 화면 칩에 노출된다.
#
# 이 cleanup 이 추가되기 전에 실행해 이미 오염된 DB 라면 아래 SQL 을 순서대로 직접 실행해 지운다
# (product_genre/product_image → product → album → genre/artist/label 순, FK 때문에 역순으로 지우면 안 된다):
#
#   DELETE pg FROM product_genre pg
#   LEFT JOIN product p ON p.id = pg.product_id
#   LEFT JOIN genre g ON g.id = pg.genre_id
#   WHERE p.title LIKE 'Perf Album %' OR g.name IN ('Perf Jazz', 'Perf Rock');
#
#   DELETE pi FROM product_image pi
#   JOIN product p ON p.id = pi.product_id
#   WHERE p.title LIKE 'Perf Album %';
#
#   DELETE FROM product WHERE title LIKE 'Perf Album %';
#   DELETE FROM album WHERE title = 'Perf Album';
#   DELETE FROM genre WHERE name IN ('Perf Jazz', 'Perf Rock');
#   DELETE FROM artist WHERE name = 'Perf Artist';
#   DELETE FROM label WHERE name = 'Perf Label';
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-groove-mysql}"
MYSQL_USER="${MYSQL_USER:-groove}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-groove1234}"
MYSQL_DATABASE="${MYSQL_DATABASE:-groove}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
SEED_PRODUCT_COUNT=50
WARMUP_REQUESTS=5
REQUESTS_PER_CASE=20
P95_THRESHOLD_MS=300

# container_name 이 고정이라 docker compose exec 대신 컨테이너 이름으로 직접 붙는다.
# (compose 프로젝트 디렉터리가 여러 개라도, 예: 워크트리에서 실행해도 항상 같은 컨테이너를 찾는다.)
mysql_exec() {
	docker exec -i -e MYSQL_PWD="${MYSQL_PASSWORD}" "${MYSQL_CONTAINER}" \
		mysql -u"${MYSQL_USER}" "${MYSQL_DATABASE}" -N -B -e "$1"
}

# 정상 종료/실패/중단(Ctrl+C) 모든 경로에서 Perf 시드 데이터를 지운다. FK 때문에 자식 테이블부터 지워야 한다.
cleanup() {
	local exit_code=$?
	rm -f "${TIMES_FILE:-}"
	mysql_exec "
	DELETE pg FROM product_genre pg
	LEFT JOIN product p ON p.id = pg.product_id
	LEFT JOIN genre g ON g.id = pg.genre_id
	WHERE p.title LIKE 'Perf Album %' OR g.name IN ('Perf Jazz', 'Perf Rock');
	" || true
	mysql_exec "
	DELETE pi FROM product_image pi
	JOIN product p ON p.id = pi.product_id
	WHERE p.title LIKE 'Perf Album %';
	" || true
	mysql_exec "DELETE FROM product WHERE title LIKE 'Perf Album %';" || true
	mysql_exec "DELETE FROM album WHERE title = 'Perf Album';" || true
	mysql_exec "DELETE FROM genre WHERE name IN ('Perf Jazz', 'Perf Rock');" || true
	mysql_exec "DELETE FROM artist WHERE name = 'Perf Artist';" || true
	mysql_exec "DELETE FROM label WHERE name = 'Perf Label';" || true
	exit "${exit_code}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

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

# product.album_id 가 NOT NULL 이라 프레싱을 넣기 전에 앨범을 먼저 멱등하게 만든다.
mysql_exec "
INSERT INTO album (title, artist_id, original_release_year, created_at, updated_at)
SELECT 'Perf Album', ${PERF_ARTIST_ID}, 2020, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM album WHERE title = 'Perf Album' AND artist_id = ${PERF_ARTIST_ID});
"
PERF_ALBUM_ID=$(mysql_exec \
	"SELECT id FROM album WHERE title = 'Perf Album' AND artist_id = ${PERF_ARTIST_ID} LIMIT 1;")

EXISTING_COUNT=$(mysql_exec "SELECT COUNT(*) FROM product WHERE title LIKE 'Perf Album %';")

if [ "${EXISTING_COUNT}" -lt "${SEED_PRODUCT_COUNT}" ]; then
	TO_INSERT=$((SEED_PRODUCT_COUNT - EXISTING_COUNT))
	echo "  기존 ${EXISTING_COUNT}건, ${TO_INSERT}건 추가 적재"

	START_INDEX=$((EXISTING_COUNT + 1))
	END_INDEX=${SEED_PRODUCT_COUNT}

	VALUES_SQL=""
	for i in $(seq "${START_INDEX}" "${END_INDEX}"); do
		PRICE=$((20000 + (i * 991) % 50000))
		COUNTRY=$([ $((i % 2)) -eq 0 ] && echo "US" || echo "KR")
		PRESSING_YEAR=$((1990 + (i % 30)))
		EDITION_TYPE=$([ $((i % 5)) -eq 0 ] && echo "LIMITED" || echo "STANDARD")
		BARCODE=$(printf '%013d' $((8800000000000 + i)))
		CATALOG_NO="PERF-${i}"
		CATALOG_NO_NORMALIZED="PERF${i}"
		if [ -n "${VALUES_SQL}" ]; then
			VALUES_SQL="${VALUES_SQL},"
		fi
		VALUES_SQL="${VALUES_SQL}('Perf Album ${i}', ${PERF_ALBUM_ID}, ${PERF_ARTIST_ID}, ${PERF_LABEL_ID}, \
'180g', 'Black', '${COUNTRY}', ${PRESSING_YEAR}, '${CATALOG_NO}', '${CATALOG_NO_NORMALIZED}', '${BARCODE}', \
'${EDITION_TYPE}', ${PRICE}, 'ON_SALE', NOW(), NOW())"
	done

	mysql_exec "
	INSERT INTO product (title, album_id, artist_id, label_id, pressing_info, color_variant, country,
		pressing_year, catalog_no, catalog_no_normalized, barcode, edition_type, price, status, created_at,
		updated_at)
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

PERF_SAMPLE_BARCODE=$(printf '%013d' $((8800000000000 + 7)))

echo "[2/3] 요청 실행 (워밍업 ${WARMUP_REQUESTS}회 + 케이스당 ${REQUESTS_PER_CASE}회)"

CASES=(
	"/api/v1/products"
	"/api/v1/products?keyword=Perf"
	"/api/v1/products?sort=priceAsc&minPrice=30000&maxPrice=50000"
	"/api/v1/products?genreIds=${PERF_JAZZ_ID}&sort=rating"
	"/api/v1/products?keyword=Album&sort=popular&page=1&size=10"
	"/api/v1/products?keyword=${PERF_SAMPLE_BARCODE}"
	"/api/v1/products?keyword=PERF-7"
	"/api/v1/products?albumId=${PERF_ALBUM_ID}&country=US&pressingYearFrom=2000"
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
