#!/usr/bin/env bash
#
# 측정용 대규모 시드 적재 (이슈 #140).
#
#   db/seed/generate_seed.py 로 seed.sql 을 합성한 뒤, FK-safe TRUNCATE + 멀티로우 INSERT 로 MySQL 에 적재한다.
#   데모 시더(LocalDataSeeder)와 별개 경로이며, W9 측정(검색 슬로우 쿼리·flash-sale)용 데이터셋을 만든다.
#
# 사용법:
#   ./scripts/seed.sh --yes            # 로컬 MySQL (DB_HOST=localhost:3306)
#   ./scripts/seed.sh --docker --yes   # docker-compose 의 mysql 컨테이너로 적재
#
# 전제: 스키마는 Flyway 가 이미 생성했어야 한다(앱 1회 부팅 또는 docker compose up). 이 스크립트는 데이터만 적재한다.
# 주의: 멱등성을 위해 카탈로그/트랜잭션/회원 테이블을 TRUNCATE 후 재적재하는 파괴적 작업이다 — --yes 필수.
#
# 접속 정보(.env 또는 환경 변수): DB_HOST(기본 localhost) DB_PORT(3306) DB_NAME(groove) DB_USERNAME(groove) DB_PASSWORD
# 규모 오버라이드: ALBUM_COUNT, MEMBER_COUNT, SEED 등 (db/seed/README.md 참고)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEED_DIR="$ROOT_DIR/db/seed"
SQL_FILE="$SEED_DIR/seed.sql"
VENV_DIR="$SEED_DIR/.venv"

DOCKER=false
CONFIRM=false
for arg in "$@"; do
  case "$arg" in
    --docker) DOCKER=true ;;
    --yes|-y) CONFIRM=true ;;
    -h|--help)
      sed -n '3,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "알 수 없는 옵션: $arg" >&2; exit 2 ;;
  esac
done

# .env 로딩 (docker 프로파일 자격증명 — 있으면)
if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a; . "$ROOT_DIR/.env"; set +a
fi

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-groove}"
DB_USERNAME="${DB_USERNAME:-groove}"
DB_PASSWORD="${DB_PASSWORD:-}"

# ── 운영 차단 가드 ─────────────────────────────────────────────────────
if [[ "$CONFIRM" != true ]]; then
  echo "✋ TRUNCATE 후 재적재하는 파괴적 작업입니다. 확인하려면 --yes 를 붙여 실행하세요." >&2
  exit 1
fi
case "$DB_HOST" in
  localhost|127.0.0.1|mysql|"") ;;
  *)
    if [[ "${SEED_FORCE:-0}" != "1" ]]; then
      echo "✋ DB_HOST='$DB_HOST' 는 로컬/측정 대상이 아닐 수 있습니다. 의도한 경우 SEED_FORCE=1 로 재실행하세요." >&2
      exit 1
    fi ;;
esac
if [[ -z "$DB_PASSWORD" ]]; then
  echo "✋ DB_PASSWORD 가 비어 있습니다(.env 또는 환경 변수로 주입하세요)." >&2
  exit 1
fi

# ── docker compose 커맨드 탐지 ─────────────────────────────────────────
compose() {
  if docker compose version >/dev/null 2>&1; then docker compose "$@";
  else docker-compose "$@"; fi
}

# ── mysql 클라이언트 래퍼 (MYSQL_PWD 로 비밀번호 경고/노출 회피) ──────────
run_mysql() {
  if [[ "$DOCKER" == true ]]; then
    compose exec -T -e MYSQL_PWD="$DB_PASSWORD" mysql \
      mysql -u"$DB_USERNAME" "$DB_NAME" "$@"
  else
    MYSQL_PWD="$DB_PASSWORD" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" "$DB_NAME" "$@"
  fi
}

# ── 1) Python venv 준비 ────────────────────────────────────────────────
echo "▶ [1/3] Python 가상환경 준비 ($VENV_DIR)"
if [[ ! -d "$VENV_DIR" ]]; then
  python3 -m venv "$VENV_DIR"
fi
# shellcheck disable=SC1091
. "$VENV_DIR/bin/activate"
pip install --quiet --upgrade pip
pip install --quiet -r "$SEED_DIR/requirements.txt"

# ── 2) seed.sql 생성 ──────────────────────────────────────────────────
echo "▶ [2/3] seed.sql 생성"
OUT="$SQL_FILE" python "$SEED_DIR/generate_seed.py"

# ── 3) MySQL 적재 ─────────────────────────────────────────────────────
echo "▶ [3/3] MySQL 적재 (docker=$DOCKER host=$DB_HOST db=$DB_NAME)"
run_mysql < "$SQL_FILE"

# ── 적재 요약 ─────────────────────────────────────────────────────────
echo "── 적재 요약 ──"
run_mysql -N -e "
  SELECT CONCAT('album         = ', COUNT(*)) FROM album
  UNION ALL SELECT CONCAT('  is_limited   = ', COUNT(*)) FROM album WHERE is_limited = 1
  UNION ALL SELECT CONCAT('  stock=1      = ', COUNT(*)) FROM album WHERE stock = 1
  UNION ALL SELECT CONCAT('artist        = ', COUNT(*)) FROM artist
  UNION ALL SELECT CONCAT('label         = ', COUNT(*)) FROM label
  UNION ALL SELECT CONCAT('genre         = ', COUNT(*)) FROM genre
  UNION ALL SELECT CONCAT('member        = ', COUNT(*)) FROM member
  UNION ALL SELECT CONCAT('  ADMIN       = ', COUNT(*)) FROM member WHERE role = 'ADMIN';"

cat <<'EOF'
✅ 완료. 테스트 계정: loadtest001@groove.test … (USER) / loadtest-admin@groove.test (ADMIN), 비밀번호 'Test1234!'
   검색 계획 확인: EXPLAIN SELECT * FROM album WHERE status='SELLING' AND MATCH(title, artist_name) AGAINST('"love"' IN BOOLEAN MODE) > 0 LIMIT 20;  → type=fulltext (ft_album_keyword)
EOF
