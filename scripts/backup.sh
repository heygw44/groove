#!/usr/bin/env bash
# GROOVE 운영 MySQL 일일 백업 스크립트 (docs/09-deployment.md 5.1절)
set -euo pipefail

PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
umask 077

BACKUP_DIR="${BACKUP_DIR:-/opt/groove/backups}"
ENV_FILE="${ENV_FILE:-/opt/groove/.env}"
CONTAINER="${CONTAINER:-groove-mysql}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
MIN_BYTES="${MIN_BYTES:-20480}"
MIN_FREE_KB="${MIN_FREE_KB:-512000}"
MIN_TABLES="${MIN_TABLES:-40}"

log() {
	printf '%s %s\n' "$(date '+%F %T')" "$*"
}

fail() {
	log "FAIL $*"
	exit 1
}

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

LOCK="$BACKUP_DIR/.backup.lock"
if [ "${BACKUP_LOCKED:-}" != "1" ]; then
	# exec 로 통째로 치환하면 락 충돌로 인한 flock 자체 종료와 실제 작업 실패가 똑같이
	# exit 1 로 남아 구분이 안 된다. exec 대신 자식으로 실행해 -E 전용 코드만 골라
	# exit 0(정상 종료)으로 바꾸고, 그 외 코드는 그대로 전달한다.
	set +e
	BACKUP_LOCKED=1 flock -n -E 99 "$LOCK" "$0" "$@"
	rc=$?
	set -e
	if [ "$rc" -eq 99 ]; then
		log "SKIP 이전 회차가 아직 실행 중이다"
		exit 0
	fi
	exit "$rc"
fi

# .env 는 CD 가 GitHub Secret 을 통째로 부어넣는 파일이라 값에 $·백틱·따옴표가 섞일 수
# 있다. source 로 읽으면 셸이 그 값을 해석해 compose 와 다르게 갈라지거나 임의 명령
# 실행으로 이어질 수 있어, 필요한 키만 grep/cut 으로 뽑는다.
read_env() {
	# 키가 없으면 grep 이 1 을 반환한다. set -e 아래 command substitution 대입에서
	# 그대로 두면 FAIL 로그를 남기기 전에 스크립트가 죽으므로 || true 로 흡수하고
	# 판단은 호출부의 빈 문자열 검사에 맡긴다.
	grep -m1 "^$1=" "$ENV_FILE" | cut -d= -f2- | tr -d '\r' || true
}

[ -f "$ENV_FILE" ] || fail "$ENV_FILE 이 없다"

database=$(read_env MYSQL_DATABASE)
root_password=$(read_env MYSQL_ROOT_PASSWORD)

[ -n "$database" ] && [ -n "$root_password" ] \
	|| fail "$ENV_FILE 에 MYSQL_DATABASE/MYSQL_ROOT_PASSWORD 가 없다"

if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" != "true" ]; then
	fail "mysql 컨테이너가 실행 중이 아니다 ($CONTAINER)"
fi

# 정리를 덤프보다 앞에 둔다. 덤프 뒤에 지우면 디스크가 이미 찬 상태에서
# 공간을 못 만들어 정리 자체가 실패할 수 있다.
find "$BACKUP_DIR" -maxdepth 1 -type f \( -name 'groove-*.sql.gz' -o -name 'groove_*.sql.gz' \) \
	-mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.sql.gz.tmp' -mtime +1 -delete

free_kb=$(df -Pk "$BACKUP_DIR" | awk 'NR==2{print $4}')
[ "$free_kb" -ge "$MIN_FREE_KB" ] \
	|| fail "디스크 여유 공간 부족 (${free_kb}KB < ${MIN_FREE_KB}KB)"

TS=$(date '+%Y%m%d-%H%M')
DEST="$BACKUP_DIR/groove-$TS.sql.gz"
TMP="$DEST.tmp"
ERR=$(mktemp)
trap 'rm -f "$TMP" "$ERR"' EXIT

export MYSQL_PWD="$root_password"
set +e
# -e MYSQL_PWD 는 값 없이 이름만 넘겨 호스트에 이미 export 된 값을 컨테이너로 전달한다.
# 값을 붙이면(-e MYSQL_PWD=...) 호스트 ps 출력에 비밀번호가 그대로 노출된다.
# -t/-it 는 붙이지 않는다. TTY 가 붙으면 출력에 CR 이 섞여 gzip 스트림이 깨진다.
docker exec -e MYSQL_PWD "$CONTAINER" \
	mysqldump -uroot --single-transaction --no-tablespaces \
		--default-character-set=utf8mb4 --databases "$database" \
	2>"$ERR" | gzip -6 > "$TMP"
dump_status=$?
set -e
unset MYSQL_PWD

[ "$dump_status" -eq 0 ] || fail "mysqldump 실패: $(head -n 5 "$ERR")"

# 압축 파일을 한 번만 읽어(gzip -dc) 테이블 수와 마지막 줄을 awk 로 동시에 뽑는다.
# t3.micro 에서 같은 스트림을 두 번 읽는 CPU 낭비를 피한다.
verify_output=$(gzip -dc "$TMP" | awk '
	/^CREATE TABLE/ { tables++ }
	{ last = $0 }
	END { print tables + 0; print last }
')
table_count=$(printf '%s\n' "$verify_output" | sed -n '1p')
last_line=$(printf '%s\n' "$verify_output" | sed -n '2,$p')

case "$last_line" in
	*"-- Dump completed"*) ;;
	*) fail "덤프가 중간에 끊겼다 (마지막 줄에 -- Dump completed 없음)" ;;
esac

[ "$table_count" -ge "$MIN_TABLES" ] \
	|| fail "테이블 수 부족 ($table_count < $MIN_TABLES)"

size=$(wc -c < "$TMP" | tr -d ' ')
[ "$size" -ge "$MIN_BYTES" ] \
	|| fail "파일 크기 부족 (${size} < ${MIN_BYTES})"

mv "$TMP" "$DEST"
TMP=""
date '+%F %T' > "$BACKUP_DIR/.last-success"
log "OK $(basename "$DEST") ${size} bytes"
