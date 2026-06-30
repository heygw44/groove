#!/usr/bin/env bash
# 멀티노드 rate-limit 분산 Before/After 재현.
#
# 동일 출처에서 로그인(POST /api/v1/auth/login)을 한도 초과로 쇄도시켜 429 차단이 인스턴스 수와 무관하게
# 일관되는지 측정한다. nginx LB 가 매 요청 라운드로빈으로 두 app 노드에 흩뿌리므로:
#   - Before(RATE_LIMIT_STORE=caffeine): 노드마다 로컬 버킷 → 실효 한도 ≈ 2×CAP (절반씩 흡수해 429 가 적다)
#   - After (RATE_LIMIT_STORE=redis, 기본): 공유 버킷 → 노드와 무관하게 ≈ CAP 만 통과, 나머지 429
# rate-limit 키는 getRemoteAddr(=nginx 컨테이너 IP)라 단일 출처면 한 버킷으로 모인다.
#
# 선행:
#   AUTH_RATE_LIMIT_LOGIN_CAPACITY=$CAP docker compose -f docker-compose.yml -f docker-compose.scale.yml up -d --build
#   (app 2 replica + redis + nginx). 자격증명은 틀려도 됨 — rate-limit 은 인증보다 앞서 적용된다.
#
# Before 측정: 위 up 에 -e RATE_LIMIT_STORE=caffeine 를 주어 app 을 caffeine 으로 띄운 뒤 실행.
# After  측정: 기본(redis)으로 띄운 뒤 실행.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:80}"
CAP="${CAP:-5}"                 # 앱에 설정한 AUTH_RATE_LIMIT_LOGIN_CAPACITY 와 같은 값
REQUESTS="${REQUESTS:-$((CAP * 4))}"   # 한도의 4배를 쏴 차단 여부를 또렷하게
LOGIN_BODY='{"email":"loadtest-probe@groove.test","password":"WrongPass123!"}'

echo "▶ 로그인 ${REQUESTS}회 쇄도 (CAP=$CAP, BASE_URL=$BASE_URL)"
allowed=0; blocked=0
for _ in $(seq 1 "$REQUESTS"); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/v1/auth/login" \
    -H 'Content-Type: application/json' -d "$LOGIN_BODY")
  if [ "$code" = "429" ]; then blocked=$((blocked + 1)); else allowed=$((allowed + 1)); fi
done

echo
echo "================= 결과 ================="
echo " 요청=$REQUESTS  통과(비-429)=$allowed  차단(429)=$blocked  (한도 CAP=$CAP)"
# 노드 수만큼 한도가 늘면 통과 수가 CAP 를 크게 넘어선다(라운드로빈 분산으로 ~2×CAP).
if [ "$allowed" -le $((CAP + 1)) ]; then
  echo " ✅ After(유지): 통과 ≈ CAP — 공유 Redis 버킷이라 노드 수와 무관하게 한도 일관"
else
  echo " ❌ Before(N배): 통과 $allowed > CAP — 노드 로컬 Caffeine 버킷이 분리돼 실효 한도가 인스턴스 수에 비례"
fi
echo "========================================"
