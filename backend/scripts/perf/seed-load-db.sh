#!/usr/bin/env bash
# k6 부하 시나리오(scripts/k6/batch-interference.js, admin-dashboard.js)용 합성 데이터 시더.
#
# index-explain.sh 는 groove_perf 스키마가 하드코딩돼 있고 개발 DB(groove)를 절대 건드리지 않는
# 안전장치가 걸려 있다. 그 스크립트를 고치지 않고, 시드 SQL(seed/backfill 함수 본문)만 텍스트로
# 추출해 이 스크립트의 대상 스키마에 대해 실행한다. index-explain.sh 를 source 하면 파일 맨 끝의
# main "$@" 이 그대로 실행되며 groove_perf 를 드롭/생성해버리므로, 함수 정의만 골라 뽑아 eval 한다.
#
# 대상 스키마는 인자로만 받는다(기본값 없음). "groove"(개발 DB)는 거부한다 — 부하 측정용 스키마는
# 새로 만드는 groove_load 를 쓴다.
#
# 사용법:
#   seed-load-db.sh <schema> [--scale N] [--keep]
#
# 예:
#   backend/scripts/perf/seed-load-db.sh groove_load --scale 0.2
set -euo pipefail

if [ -z "${DOCKER_HOST:-}" ] && [ -S "${HOME}/.orbstack/run/docker.sock" ]; then
	export DOCKER_HOST="unix://${HOME}/.orbstack/run/docker.sock"
fi

MYSQL_CONTAINER="${MYSQL_CONTAINER:-groove-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root1234}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MIGRATION_DIR="${BACKEND_DIR}/src/main/resources/db/migration"
INDEX_EXPLAIN_SH="${SCRIPT_DIR}/index-explain.sh"

usage() {
	cat >&2 <<-USAGE
		사용법: $(basename "$0") <schema> [--scale N] [--keep] [--admin-email E] [--admin-password P]

		  <schema>            대상 스키마명 (필수, 기본값 없음). "groove"(개발 DB)는 거부한다.
		                      부하 측정용으로는 groove_load 를 쓴다.
		  --scale N           index-explain.sh 와 같은 스케일 계수 (기본 0.2). 1.0 이 원본 규모.
		  --keep              종료 후 스키마를 남긴다 (기본: 남긴다. 이 스크립트는 항상 스키마를 유지한다).
		  --admin-email E     관리자 계정 이메일 (기본 admin@groove.com)
		  --admin-password P  관리자 계정 평문 비밀번호 (기본 admin1234!, htpasswd -bnBC 로 BCrypt 인코딩해 저장)
	USAGE
	exit 1
}

if [ $# -lt 1 ]; then
	usage
fi

TARGET_SCHEMA="$1"
shift

if [ "${TARGET_SCHEMA}" = "groove" ]; then
	echo "[거부] 개발 DB(groove)는 대상으로 지정할 수 없습니다. groove_load 처럼 별도 스키마를 쓰세요." >&2
	exit 1
fi

SCALE="0.2"
ADMIN_EMAIL="admin@groove.com"
ADMIN_PASSWORD="admin1234!"

while [ $# -gt 0 ]; do
	case "$1" in
	--scale)
		SCALE="$2"
		shift 2
		;;
	--keep)
		# 이 스크립트는 index-explain.sh 와 달리 항상 스키마를 남긴다(부하 테스트 대상이므로).
		# 옵션은 사용법 문서와의 일관성을 위해서만 받아준다.
		shift
		;;
	--admin-email)
		ADMIN_EMAIL="$2"
		shift 2
		;;
	--admin-password)
		ADMIN_PASSWORD="$2"
		shift 2
		;;
	*)
		echo "알 수 없는 옵션: $1" >&2
		usage
		;;
	esac
done

if ! [[ "${SCALE}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
	echo "--scale 값이 올바르지 않습니다: ${SCALE}" >&2
	exit 1
fi

if [ ! -f "${INDEX_EXPLAIN_SH}" ]; then
	echo "index-explain.sh 를 찾을 수 없습니다: ${INDEX_EXPLAIN_SH}" >&2
	exit 1
fi

if ! command -v htpasswd >/dev/null 2>&1; then
	echo "htpasswd(apache2-utils) 가 필요합니다. 관리자 계정 비밀번호를 BCrypt 로 인코딩하는 데 쓴다." >&2
	exit 1
fi

mysql_root() {
	docker exec -i -e MYSQL_PWD="${MYSQL_PASSWORD}" "${MYSQL_CONTAINER}" \
		mysql -u"${MYSQL_USER}" -N -B --raw -e "$1"
}

mysql_target() {
	docker exec -i -e MYSQL_PWD="${MYSQL_PASSWORD}" "${MYSQL_CONTAINER}" \
		mysql -u"${MYSQL_USER}" -N -B --raw "${TARGET_SCHEMA}" -e "$1"
}

mysql_target_file() {
	docker exec -i -e MYSQL_PWD="${MYSQL_PASSWORD}" "${MYSQL_CONTAINER}" \
		mysql -u"${MYSQL_USER}" "${TARGET_SCHEMA}" < "$1"
}

apply_migrations() {
	local file base
	for file in $(find "${MIGRATION_DIR}" -maxdepth 1 -name 'V*.sql' | sort -V); do
		base=$(basename "${file}")
		echo "  적용: ${base}"
		mysql_target_file "${file}"
	done
}

# index-explain.sh 를 고치지 않고 함수 정의 텍스트만 뽑아온다. 이 함수들은 모두
# mysql_perf/mysql_root/PERF_SCHEMA/SCALE 같은 이름을 몸체 안에서만 참조하므로, 여기서 같은
# 이름으로(mysql_perf 는 mysql_target 를 감싼 별칭으로) 재정의해 eval 하면 groove_perf 대신
# TARGET_SCHEMA 를 대상으로 동작한다.
extract_function() {
	local name="$1"
	awk -v fn="$name" '
		$0 ~ "^" fn "\\(\\) \\{" { capture = 1 }
		capture { print }
		capture && /^}/ { capture = 0 }
	' "${INDEX_EXPLAIN_SH}"
}

load_shared_seed_functions() {
	local fn extracted
	# mysql_perf 는 원본 스크립트에서 PERF_SCHEMA 를 씀. 그 이름 그대로 재정의해 TARGET_SCHEMA 로 돌린다.
	# shellcheck disable=SC2317
	mysql_perf() { mysql_target "$1"; }
	# backfill_* 함수 몸체가 정보스키마 조회에 ${PERF_SCHEMA} 를 리터럴로 참조한다(추출한 텍스트를
	# 고치지 않았으므로). 같은 이름의 전역 변수를 TARGET_SCHEMA 로 채워 그 참조를 그대로 만족시킨다.
	PERF_SCHEMA="${TARGET_SCHEMA}"
	for fn in scaled_count backfill_sold_quantity backfill_sales_daily analyze_tables seed; do
		extracted="$(extract_function "${fn}")"
		if [ -z "${extracted}" ]; then
			echo "index-explain.sh 에서 ${fn}() 함수를 찾지 못했습니다. 원본 구조가 바뀌었을 수 있습니다." >&2
			exit 1
		fi
		eval "${extracted}"
	done
}

seed_admin_account() {
	local encoded_password existing
	existing=$(mysql_target "SELECT COUNT(*) FROM member WHERE email = '${ADMIN_EMAIL}';")
	if [ "${existing}" != "0" ]; then
		echo "  관리자 계정이 이미 있어 건너뜁니다: ${ADMIN_EMAIL}"
		return 0
	fi
	# Member.createAdmin 과 같은 필드(role=ADMIN, status=ACTIVE)로 직접 INSERT 한다.
	# SecurityConfig 의 PasswordEncoder 가 BCryptPasswordEncoder() 라 $2a/$2y 어느 prefix 든 검증된다.
	encoded_password=$(htpasswd -bnBC 10 "" "${ADMIN_PASSWORD}" | cut -d: -f2)
	mysql_target "
		INSERT INTO member (email, password, nickname, role, status, created_at, updated_at)
		VALUES ('${ADMIN_EMAIL}', '${encoded_password}', '관리자', 'ADMIN', 'ACTIVE', NOW(6), NOW(6));
	"
	echo "  관리자 계정 생성: ${ADMIN_EMAIL} / ${ADMIN_PASSWORD}"
}

main() {
	echo "[1/5] ${TARGET_SCHEMA} 스키마 초기화 (개발 DB groove 는 건드리지 않는다)"
	mysql_root "CREATE DATABASE IF NOT EXISTS ${TARGET_SCHEMA} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

	local existing_tables
	existing_tables=$(mysql_root "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${TARGET_SCHEMA}';")
	if [ "${existing_tables}" != "0" ]; then
		echo "  이미 테이블이 있습니다(${existing_tables}개). 기존 스키마를 그대로 재사용합니다." >&2
		echo "  깨끗한 상태로 다시 시드하려면 먼저 DROP DATABASE ${TARGET_SCHEMA} 를 실행하세요." >&2
		exit 1
	fi

	echo "[2/5] Flyway 마이그레이션(V1~V15) 순서대로 적용"
	apply_migrations

	echo "[3/5] index-explain.sh 의 시드 함수 재사용 (source 대신 함수 텍스트만 추출)"
	load_shared_seed_functions

	echo "[4/5] 합성 데이터 적재 (scale=${SCALE})"
	local seed_start seed_end
	seed_start=$(date +%s)
	SCALE="${SCALE}" TARGET_MEMBER_ID=1 seed
	seed_end=$(date +%s)
	echo "  시드 소요 시간: $((seed_end - seed_start))초"

	echo "  - product.sold_quantity / sales_daily / sales_daily_product 백필"
	backfill_sold_quantity
	backfill_sales_daily

	echo "  - 관리자 계정 시드"
	seed_admin_account

	echo "[5/5] 테이블 통계 갱신"
	analyze_tables

	echo "완료. 대상 스키마: ${TARGET_SCHEMA}"
	echo "정리하려면: docker exec -i -e MYSQL_PWD=${MYSQL_PASSWORD} ${MYSQL_CONTAINER} mysql -u${MYSQL_USER} -e 'DROP DATABASE ${TARGET_SCHEMA};'"
}

main
