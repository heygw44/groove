#!/usr/bin/env bash
# 멀티노드 카탈로그 캐시 일관성 Before/After 재현 (#366).
#
# 노드 A 의 admin write(@CacheEvict)가 노드 B 의 캐시에 반영되는지 측정한다. nginx LB 는 매 요청 라운드로빈이라
# "어느 노드가 write 를 받고 어느 노드가 read 하는지" 고정할 수 없으므로, app replica 를 docker compose exec
# --index 로 직접 타깃팅한다(app 은 호스트 포트 미발행 — 컨테이너 내부 curl localhost:8080 사용).
#
# 시퀀스: 핫 앨범 id 확보 → 노드1·노드2 각각 GET(양쪽 캐시 적재) → 노드1 admin adjustStock(+delta, evict)
#         → 즉시 노드2 GET → stock 일치 여부 판정.
#   - After (SPRING_CACHE_TYPE=redis, 기본): 공유 redis 라 노드2 즉시 일관(새 stock).
#   - Before(SPRING_CACHE_TYPE=caffeine):    노드2 로컬 캐시는 stale → 최대 TTL(60s) 동안 옛 stock.
#
# 선행:
#   docker compose -f docker-compose.yml -f docker-compose.scale.yml up -d --build   # app 2 replica + redis + nginx
#   ./backend ... scripts/seed.sh 또는 데모 시드로 admin(loadtest-admin@groove.test / Test1234!) + SELLING 앨범 적재
#
# Before 측정: 위 up 명령에 -e SPRING_CACHE_TYPE=caffeine 을 주어(또는 .env) app 을 caffeine 으로 띄운 뒤 이 스크립트 실행.
# After  측정: 기본(redis)으로 띄운 뒤 실행.
set -euo pipefail

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.scale.yml"
ADMIN_EMAIL="${ADMIN_EMAIL:-loadtest-admin@groove.test}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Test1234!}"
DELTA="${DELTA:-7}"          # 노드1 에서 더할 재고 증분(0 아님)
POLL_SECONDS="${POLL_SECONDS:-3}"   # 노드2 stale 지속 관측 시간

# 컨테이너 내부에서 curl 실행(app:8080). $1=replica index, 나머지=curl 인자.
# -f: 4xx/5xx 면 curl 이 non-zero 로 종료 → set -e/pipefail 이 잡아 에러 바디를 stock 으로 오파싱하지 않는다.
node() { local idx="$1"; shift; $COMPOSE exec -T --index="$idx" app curl -fsS "$@"; }

json() { grep -oE "\"$1\":[^,}\"]+" | head -1 | cut -d: -f2; }       # 숫자 필드
jstr() { grep -oE "\"$1\":\"[^\"]*\"" | head -1 | cut -d: -f2 | tr -d '"'; }  # 문자열 필드

echo "▶ replica 수 확인"
running=$($COMPOSE ps app --format '{{.Name}}' | wc -l | tr -d ' ')
if [ "$running" -lt 2 ]; then
  echo "✗ app replica 가 2개 미만($running). docker-compose.scale.yml 로 scale=2 기동 필요." >&2
  exit 1
fi

echo "▶ admin 로그인(노드1)"
LOGIN_BODY=$(printf '{"email":"%s","password":"%s"}' "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
TOKEN=$(node 1 -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -d "$LOGIN_BODY" | jstr accessToken)
[ -n "${TOKEN:-}" ] || { echo "✗ admin 로그인 실패 — 시드/자격증명 확인($ADMIN_EMAIL)" >&2; exit 1; }

echo "▶ 핫 SELLING 앨범 id 확보"
ALBUM_ID="${ALBUM_ID:-$(node 1 http://localhost:8080/api/v1/albums?size=1 | grep -oE '"id":[0-9]+' | head -1 | cut -d: -f2)}"
[ -n "${ALBUM_ID:-}" ] || { echo "✗ 앨범을 찾지 못함 — 시드 확인" >&2; exit 1; }

echo "▶ 노드1·노드2 캐시 적재(GET /albums/$ALBUM_ID)"
OLD1=$(node 1 "http://localhost:8080/api/v1/albums/$ALBUM_ID" | json stock)
OLD2=$(node 2 "http://localhost:8080/api/v1/albums/$ALBUM_ID" | json stock)
echo "  적재 직후 stock: 노드1=$OLD1 노드2=$OLD2"

echo "▶ 노드1 에서 adjustStock(delta=$DELTA) → 노드1 캐시 evict"
NEW1=$(node 1 -X PATCH "http://localhost:8080/api/v1/admin/albums/$ALBUM_ID/stock" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"delta\":$DELTA}" | json stock)
EXPECTED=$((OLD1 + DELTA))
echo "  노드1 변경 후 stock=$NEW1 (기대=$EXPECTED)"
# write 결과를 baseline 으로 쓰기 전에 검증한다 — 누락/불일치면 이후 일관성 판정이 무의미하므로 중단.
if [ -z "${NEW1:-}" ] || [ "$NEW1" != "$EXPECTED" ]; then
  echo "✗ 노드1 adjustStock 결과 불일치(NEW1='${NEW1:-}', 기대=$EXPECTED) — 측정 중단" >&2
  exit 1
fi

echo "▶ 즉시 노드2 재조회(${POLL_SECONDS}s 동안 반복) — 노드2 가 새 stock 을 보는가?"
deadline=$((SECONDS + POLL_SECONDS))
stale_seen=0; consistent=0; last2=""
while [ $SECONDS -le $deadline ]; do
  last2=$(node 2 "http://localhost:8080/api/v1/albums/$ALBUM_ID" | json stock)
  if [ "$last2" = "$EXPECTED" ]; then consistent=1; break; else stale_seen=1; fi
  sleep 0.3
done

echo
echo "================= 결과 ================="
echo " 앨범 id=$ALBUM_ID  delta=$DELTA  기대 stock=$EXPECTED"
echo " 노드2 마지막 관측 stock=$last2"
if [ "$consistent" = 1 ] && [ "$stale_seen" = 0 ]; then
  echo " ✅ After(일관): 노드1 write 직후 노드2 가 즉시 새 stock — 공유 Redis 분산 캐시"
elif [ "$consistent" = 1 ]; then
  echo " ⚠ 결국 일관해졌으나 일시 stale 관측 — 캐시 모드/타이밍 확인"
else
  echo " ❌ Before(stale): 노드2 가 ${POLL_SECONDS}s 내내 옛 stock($last2≠$EXPECTED) — 노드 로컬 Caffeine(최대 60s TTL 후 자가치유)"
fi
echo "========================================"
