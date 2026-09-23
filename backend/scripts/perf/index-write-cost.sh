#!/usr/bin/env bash
# V11(query_index_tuning)이 orders 에서 idx_orders_status 를 지우고 idx_orders_status_created /
# idx_orders_created 를 새로 붙였다. 조회 이득은 index-explain.sh 로 쟀지만 쓰기 쪽(INSERT 적재,
# PK 단건 상태 UPDATE, 인덱스 자체 크기)은 재지 않았다. 이 스크립트는 index-explain.sh 가 세운
# groove_perf 스키마를 그대로 이어받아 V11 전(before)/후(after) 스키마에서 같은 워크로드를 반복해
# 그 세 가지를 비교한다. 개발 DB(groove)는 건드리지 않는다.
#
# 1회차 측정에서 before/after 차이가 같은 스키마끼리의 회차간 편차보다 작아 결론을 못 낼 만큼
# 흔들렸다. 원인은 두 가지였다: (1) 문장마다 커밋(autocommit)하는 단건 UPDATE 는 커밋마다
# redo 로그 fsync(innodb_flush_log_at_trx_commit=1)가 걸려 호스트 디스크 지연이 인덱스 차이를
# 덮는다. (2) after 측정 직전에 DDL 로 새 인덱스를 만든 직후라 더티 페이지 플러시가 진행 중이고
# 버퍼 풀 캐시 상태도 before 와 다르다. 그래서 UPDATE 를 앱 패턴(커밋 포함, fsync 여러 번)과
# 인덱스 유지 비용(대상 전체를 트랜잭션 1개로 묶어 fsync 1회)으로 나눠 재고, 각 단계 측정 전에
# 더티 페이지가 가라앉기를 기다린 뒤 인덱스를 버퍼 풀에 예열하고 나서 잰다.
#
# 사용법:
#   # groove_perf 를 V11 전 상태로 새로 시드하면서 측정
#   index-write-cost.sh --prepare [--runs N] [--insert-rows N] [--update-rows N] [--out 파일] [--keep]
#
#   # index-explain.sh --after-ddl <V11> --phase before --keep 로 이미 준비된 groove_perf 를 재사용
#   index-write-cost.sh [--runs N] [--insert-rows N] [--update-rows N] [--out 파일] [--keep]
set -euo pipefail

# OrbStack 은 DOCKER_HOST 를 별도로 export 해야 docker CLI 가 데몬을 찾는다.
if [ -z "${DOCKER_HOST:-}" ] && [ -S "${HOME}/.orbstack/run/docker.sock" ]; then
	export DOCKER_HOST="unix://${HOME}/.orbstack/run/docker.sock"
fi

MYSQL_CONTAINER="${MYSQL_CONTAINER:-groove-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root1234}"
readonly PERF_SCHEMA="groove_perf"

# 스키마명이 실수로 바뀌어도(예: 복사/치환 과정에서) 개발 DB 를 건드리지 않도록 방어한다.
if [ "${PERF_SCHEMA}" != "groove_perf" ]; then
	echo "[가드] PERF_SCHEMA 가 groove_perf 가 아닙니다. 중단합니다." >&2
	exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MIGRATION_DIR="${BACKEND_DIR}/src/main/resources/db/migration"
INDEX_EXPLAIN="${SCRIPT_DIR}/index-explain.sh"
V11_FILE="${MIGRATION_DIR}/V11__query_index_tuning.sql"
RESULTS_DIR="${SCRIPT_DIR}/results"

PREPARE=false
RUNS=5
INSERT_ROWS=50000
UPDATE_ROWS=20000
OUT_FILE=""
KEEP=false

while [ $# -gt 0 ]; do
	case "$1" in
	--prepare)
		PREPARE=true
		shift
		;;
	--runs)
		RUNS="$2"
		shift 2
		;;
	--insert-rows)
		INSERT_ROWS="$2"
		shift 2
		;;
	--update-rows)
		UPDATE_ROWS="$2"
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
	*)
		echo "알 수 없는 옵션: $1" >&2
		exit 1
		;;
	esac
done

if ! [[ "${RUNS}" =~ ^[1-9][0-9]*$ ]]; then
	echo "--runs 값이 올바르지 않습니다: ${RUNS}" >&2
	exit 1
fi
if ! [[ "${INSERT_ROWS}" =~ ^[1-9][0-9]*$ ]]; then
	echo "--insert-rows 값이 올바르지 않습니다: ${INSERT_ROWS}" >&2
	exit 1
fi
if ! [[ "${UPDATE_ROWS}" =~ ^[1-9][0-9]*$ ]]; then
	echo "--update-rows 값이 올바르지 않습니다: ${UPDATE_ROWS}" >&2
	exit 1
fi
# wc_numbers 는 index-explain.sh 의 numbers 헬퍼와 같은 1000 x 1000 교차조인 방식이라 상한이 같다.
if [ "${INSERT_ROWS}" -gt 1000000 ]; then
	echo "--insert-rows 값이 너무 큽니다(최대 1,000,000): ${INSERT_ROWS}" >&2
	exit 1
fi
# --insert-rows 와 같은 상한을 둔다(회차당 대상 수가 과도해지는 걸 막는다).
if [ "${UPDATE_ROWS}" -gt 1000000 ]; then
	echo "--update-rows 값이 너무 큽니다(최대 1,000,000): ${UPDATE_ROWS}" >&2
	exit 1
fi

if [ -z "${OUT_FILE}" ]; then
	OUT_FILE="${RESULTS_DIR}/index-write-cost-$(date '+%Y%m%d-%H%M%S').md"
fi

REPORT_TMP=""

cleanup() {
	local exit_code=$?
	rm -f "${REPORT_TMP:-}"
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
	# 마이그레이션/프로시저 정의 파일을 그대로 stdin 으로 흘려보낸다(DELIMITER 등 다중 문장 포함 가능).
	docker exec -i -e MYSQL_PWD="${MYSQL_PASSWORD}" "${MYSQL_CONTAINER}" \
		mysql -u"${MYSQL_USER}" "${PERF_SCHEMA}" < "$1"
}

# 마이크로초 단위 date +%N 을 지원하지 않는 date(일부 macOS 기본 /bin/date)에서는 그대로 쓰면
# "1758..N" 처럼 리터럴 N 이 섞여 나온다. 자릿수/숫자 여부로 걸러 python3 로 낮춘다.
now_ms() {
	local raw
	raw="$(date +%s%N 2>/dev/null || true)"
	if [[ "${raw}" =~ ^[0-9]{19,}$ ]]; then
		echo $((raw / 1000000))
	elif command -v python3 > /dev/null 2>&1; then
		python3 -c 'import time; print(int(time.time() * 1000))'
	else
		echo $(($(date +%s) * 1000))
	fi
}

# 공백으로 구분된 숫자 목록의 중앙값. 짝수 개면 가운데 두 값의 평균.
median() {
	printf '%s\n' "$@" | sort -n | awk '
		{ a[NR] = $1 }
		END {
			if (NR % 2 == 1) {
				printf "%.2f", a[(NR + 1) / 2]
			} else {
				printf "%.2f", (a[NR / 2] + a[NR / 2 + 1]) / 2
			}
		}'
}

# 공백으로 구분된 숫자 목록의 "최소~최대" 문자열.
min_max() {
	printf '%s\n' "$@" | sort -n | awk '
		NR == 1 { mn = $1 }
		{ mx = $1 }
		END { printf "%s~%s", mn, mx }'
}

join_values() {
	local IFS=','
	echo "$*"
}

compute_qps() {
	awk -v c="$1" -v ms="$2" 'BEGIN {
		if (ms > 0) { printf "%.1f", c * 1000 / ms } else { print "inf" }
	}'
}

buffer_pool_reads() {
	mysql_perf "SHOW GLOBAL STATUS LIKE 'Innodb_buffer_pool_reads';" | awk '{ print $2 }'
}

buffer_pool_dirty_pages() {
	mysql_perf "SHOW GLOBAL STATUS LIKE 'Innodb_buffer_pool_pages_dirty';" | awk '{ print $2 }'
}

check_gitignore() {
	if git -C "${SCRIPT_DIR}" check-ignore -q "${RESULTS_DIR}/x.md" 2>/dev/null; then
		echo "[안내] ${RESULTS_DIR} 는 gitignore 대상입니다."
	else
		echo "[경고] ${RESULTS_DIR} 가 gitignore 대상이 아닙니다. 결과 파일을 커밋하지 않도록 주의하세요." >&2
	fi
}

# groove_perf.orders 가 V11 이전 스키마(idx_orders_status 있음, idx_orders_status_created 없음)인지
# 확인한다. --prepare 없이 실행할 때 엉뚱한 상태의 groove_perf 를 그대로 측정하지 않게 막는 가드다.
check_before_state() {
	local schema_exists has_old has_new
	schema_exists=$(mysql_root "SELECT COUNT(*) FROM information_schema.schemata
		WHERE schema_name = '${PERF_SCHEMA}';")
	if [ "${schema_exists}" != "1" ]; then
		echo "[안내] ${PERF_SCHEMA} 스키마가 없습니다. --prepare 로 먼저 시드하세요." >&2
		exit 2
	fi
	has_old=$(mysql_perf "SELECT COUNT(*) FROM information_schema.statistics
		WHERE table_schema = '${PERF_SCHEMA}' AND table_name = 'orders' AND index_name = 'idx_orders_status';")
	has_new=$(mysql_perf "SELECT COUNT(*) FROM information_schema.statistics
		WHERE table_schema = '${PERF_SCHEMA}' AND table_name = 'orders'
		AND index_name = 'idx_orders_status_created';")
	if [ "${has_old}" = "0" ] || [ "${has_new}" != "0" ]; then
		echo "[안내] ${PERF_SCHEMA}.orders 가 V11 이전 상태가 아닙니다" \
			"(idx_orders_status 는 있어야 하고 idx_orders_status_created 는 없어야 함)." >&2
		echo "       --prepare 옵션으로 다시 시드하세요." >&2
		exit 2
	fi
}

# wc_numbers(1..n) 를 준비한다. index-explain.sh 의 numbers 헬퍼(seq1k 자기 교차조인)와 같은 방식이다.
ensure_wc_numbers() {
	local n="$1"
	echo "  - wc_numbers(${n}행) 준비"
	mysql_perf "
		SET SESSION cte_max_recursion_depth = 1010;
		DROP TABLE IF EXISTS wc_seq1k;
		CREATE TABLE wc_seq1k (n INT PRIMARY KEY) ENGINE=InnoDB;
		INSERT INTO wc_seq1k (n)
		WITH RECURSIVE seq(n) AS (
			SELECT 1
			UNION ALL
			SELECT n + 1 FROM seq WHERE n < 1000
		)
		SELECT n FROM seq;
		DROP TABLE IF EXISTS wc_numbers;
		CREATE TABLE wc_numbers (n BIGINT PRIMARY KEY) ENGINE=InnoDB;
		INSERT INTO wc_numbers (n)
		SELECT (a.n - 1) * 1000 + b.n
		FROM wc_seq1k a CROSS JOIN wc_seq1k b
		WHERE (a.n - 1) * 1000 + b.n <= ${n};
		DROP TABLE wc_seq1k;
	" > /dev/null
}

# member.id 는 InnoDB 가 INSERT...SELECT 벌크 통계 추정으로 auto_increment 를 앞서 예약했다가 못 쓴
# 만큼 버려서(문서화된 정상 동작) 군데군데 크게 비어 있다 - 시드 직후에도 MAX(id) 가 COUNT(*) 의
# 몇 배가 되는 일이 흔하다. 그래서 "((n * 소수) % MAX(id)) + 1" 처럼 id 를 조밀하다고 가정해 직접
# 계산하면 존재하지 않는 id 를 만들어 FK 위반이 난다. seq(1..count) -> 실제 id 매핑 테이블을 만들어
# INSERT 마다 JOIN 으로 실제 id 만 골라 쓴다.
prepare_member_pool() {
	echo "  - wc_member_ids(${MEMBER_COUNT}건) 준비 (member.id 의 auto_increment 갭 우회)"
	mysql_perf "
		DROP TABLE IF EXISTS wc_member_ids;
		CREATE TABLE wc_member_ids (seq BIGINT PRIMARY KEY, id BIGINT NOT NULL) ENGINE=InnoDB;
		INSERT INTO wc_member_ids (seq, id)
		SELECT ROW_NUMBER() OVER (ORDER BY id) AS seq, id FROM member;
	" > /dev/null
}

SETTLE_LOG=()

# 더티 페이지 플러시가 진행 중인 상태로 재면 디스크 I/O 흔들림이 지표 차이를 덮는다. 워크로드
# 그룹(INSERT, UPDATE 앱 패턴, UPDATE 트랜잭션)마다 시작 전에 불러 Innodb_buffer_pool_pages_dirty 가
# 가라앉을 때까지 2초 간격으로 기다리고(최대 180초, 넘기면 경고만 남기고 진행), 안정된 뒤에도
# 10초를 더 둔다. 그룹별 소요 시간은 합산하지 않고 SETTLE_LOG 에 목록으로 남긴다.
wait_for_settle() {
	local phase="$1" label="$2" start now dirty elapsed
	start=$(date +%s)
	echo "  - ${phase} 단계 [${label}]: dirty page 정착 대기(Innodb_buffer_pool_pages_dirty <= 100)"
	while true; do
		dirty=$(buffer_pool_dirty_pages)
		now=$(date +%s)
		elapsed=$((now - start))
		if [ "${dirty}" -le 100 ]; then
			break
		fi
		if [ "${elapsed}" -ge 180 ]; then
			echo "  [경고] ${phase} 단계 [${label}]: 180초 안에 dirty page 가 안 줄었습니다" \
				"(마지막 값 ${dirty}). 그대로 진행합니다." >&2
			break
		fi
		sleep 2
	done
	sleep 10
	now=$(date +%s)
	elapsed=$((now - start))
	echo "  - ${phase} 단계 [${label}]: 정착 대기 ${elapsed}초(10초 고정 대기 포함)"
	SETTLE_LOG+=("${phase} / ${label}: ${elapsed}초")
}

# orders 의 인덱스를 전부 한 번씩 훑어 버퍼 풀에 올린다. 캐시가 비어 있으면 before/after 가
# 서로 다른 캐시 상태에서 재는 셈이라 I/O 패턴 차이가 인덱스 차이처럼 보인다.
warm_cache() {
	local phase="$1" name
	echo "  - ${phase} 단계: orders 인덱스 예열"
	while IFS= read -r name; do
		[ -z "${name}" ] && continue
		mysql_perf "SELECT COUNT(*) FROM orders FORCE INDEX (\`${name}\`);" > /dev/null
	done < <(mysql_perf "
		SELECT DISTINCT index_name FROM information_schema.statistics
		WHERE table_schema = '${PERF_SCHEMA}' AND table_name = 'orders';
	")
}

# UPDATE 를 두 방식으로 잰다.
# - wc_update_targets: 앱의 JPA 더티 체킹(트랜잭션마다 PK 단건 UPDATE 1건, 문장마다 커밋)을
#   그대로 흉내 낸다. innodb_flush_log_at_trx_commit=1 이면 커밋마다 redo fsync 가 걸려
#   호스트 디스크 지연이 그대로 섞여 들어간다("앱 패턴" 지표, 참고용).
# - wc_update_targets_txn: 같은 대상을 트랜잭션 1개로 묶어 커밋도 한 번만 한다. fsync 가 1회뿐이라
#   커밋 지연이 거의 사라지고 인덱스 유지 비용(페이지 조회/쓰기) 차이가 남는다("인덱스 유지 비용"
#   지표, 판정 대상).
# mysql -e 는 DELIMITER 를 못 받아들이므로 파일로 흘려보낸다. heredoc 을 'SQL' 로 따옴표 처리해
# $$ 가 셸 PID 로 치환되지 않게 한다.
create_wc_procedures() {
	local proc_file
	proc_file=$(mktemp)
	cat > "${proc_file}" <<-'SQL'
		DROP PROCEDURE IF EXISTS wc_update_targets;
		DROP PROCEDURE IF EXISTS wc_update_targets_txn;

		DELIMITER $$
		CREATE PROCEDURE wc_update_targets()
		BEGIN
			DECLARE done INT DEFAULT 0;
			DECLARE target_id BIGINT;
			DECLARE cur CURSOR FOR SELECT id FROM wc_targets ORDER BY seq;
			DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

			OPEN cur;
			read_loop: LOOP
				FETCH cur INTO target_id;
				IF done THEN
					LEAVE read_loop;
				END IF;
				UPDATE orders SET status = 'PAID', updated_at = NOW(6) WHERE id = target_id;
			END LOOP;
			CLOSE cur;
		END$$

		CREATE PROCEDURE wc_update_targets_txn()
		BEGIN
			DECLARE done INT DEFAULT 0;
			DECLARE target_id BIGINT;
			DECLARE cur CURSOR FOR SELECT id FROM wc_targets ORDER BY seq;
			DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

			START TRANSACTION;
			OPEN cur;
			read_loop: LOOP
				FETCH cur INTO target_id;
				IF done THEN
					LEAVE read_loop;
				END IF;
				UPDATE orders SET status = 'PAID', updated_at = NOW(6) WHERE id = target_id;
			END LOOP;
			CLOSE cur;
			COMMIT;
		END$$
		DELIMITER ;
	SQL
	mysql_perf_file "${proc_file}"
	rm -f "${proc_file}"
}

# UPDATE 대상 id 를 before 진입 전에 한 번만 무작위로 뽑아 고정한다. before/after 가 같은 행을
# 써야 비교가 의미 있어서, 단계가 바뀌어도 wc_targets 는 다시 뽑지 않는다(스크립트 끝에서만
# 정리한다). PENDING 이 --update-rows 보다 적으면 무작위로 골라 먼저 PENDING 으로 맞춘다(이 보충은
# 측정 대상이 아니다).
prepare_fixed_targets() {
	local pending_count shortfall
	pending_count=$(mysql_perf "SELECT COUNT(*) FROM orders WHERE status = 'PENDING';")
	if [ "${pending_count}" -lt "${UPDATE_ROWS}" ]; then
		shortfall=$((UPDATE_ROWS - pending_count))
		echo "  - PENDING 부족(${pending_count}/${UPDATE_ROWS}), ${shortfall}건 보충"
		mysql_perf "
			UPDATE orders
			SET status = 'PENDING'
			WHERE status <> 'PENDING'
			ORDER BY RAND()
			LIMIT ${shortfall};
		" > /dev/null
	fi
	echo "  - wc_targets(${UPDATE_ROWS}건) 무작위 고정(before/after 공용)"
	mysql_perf "
		DROP TABLE IF EXISTS wc_targets;
		CREATE TABLE wc_targets (seq INT PRIMARY KEY, id BIGINT NOT NULL) ENGINE=InnoDB;
		INSERT INTO wc_targets (seq, id)
		SELECT ROW_NUMBER() OVER () AS seq, t.id
		FROM (
			SELECT id FROM orders WHERE status = 'PENDING' ORDER BY RAND() LIMIT ${UPDATE_ROWS}
		) t;
	" > /dev/null
}

# 단계가 바뀌면(특히 V11 DDL 적용 직후) wc_targets 행이 이전 단계의 UPDATE 테스트가 남긴 상태로
# 있을 수 있어 각 단계 진입 시 다시 한 번 PENDING 으로 맞춘다. 이미 PENDING 이면 WHERE 절 덕분에
# 사실상 no-op 이다. 측정에서 뺀다.
ensure_targets_pending() {
	echo "  - wc_targets 를 PENDING 으로 확인/보정(측정 제외)"
	mysql_perf "
		UPDATE orders o
		JOIN wc_targets t ON o.id = t.id
		SET o.status = 'PENDING'
		WHERE o.status <> 'PENDING';
	" > /dev/null
}

restore_update_targets() {
	mysql_perf "
		UPDATE orders o
		JOIN wc_targets t ON o.id = t.id
		SET o.status = 'PENDING';
	" > /dev/null
}

LAST_DUR_MS=0
LAST_BUF_DELTA=0

# INSERT ... SELECT 한 문장으로 --insert-rows 건을 적재한다. order_number 는 단계/회차로 유일하게
# 만들어 회차 간 충돌 없이 곧바로 지울 수 있게 한다. member_id/created_at 은 소수 배수로 흩뿌려
# 단조 증가를 피한다(실제 분포처럼 여러 페이지에 걸쳐 쓰게 하려는 목적).
run_insert_round() {
	local phase="$1" run="$2"
	local sql buf0 buf1 t0 t1
	sql="
		INSERT INTO orders (order_number, member_id, total_amount, discount_amount, final_amount, status,
			zip_code, phone, recipient_name, address1, address2, member_coupon_id, expires_at, created_at,
			updated_at)
		SELECT
			CONCAT('WC-${phase}-${run}-', wn.n),
			wm.id,
			15000 + (wn.n * 777) % 200000,
			0,
			15000 + (wn.n * 777) % 200000,
			'PENDING',
			LPAD(wn.n % 100000, 5, '0'),
			CONCAT('010-9999-', LPAD(wn.n % 10000, 4, '0')),
			CONCAT('WC Recipient ', wn.n),
			CONCAT('WC Address ', wn.n),
			NULL,
			NULL,
			DATE_ADD(NOW(6), INTERVAL 1 DAY),
			DATE_SUB(NOW(6), INTERVAL ((wn.n * 48271 + ${run} * 17) % 730) DAY),
			NOW(6)
		FROM wc_numbers wn
		JOIN wc_member_ids wm ON wm.seq = ((wn.n * 104729 + ${run}) % ${MEMBER_COUNT}) + 1
		WHERE wn.n <= ${INSERT_ROWS};
	"
	buf0=$(buffer_pool_reads)
	t0=$(now_ms)
	mysql_perf "${sql}" > /dev/null
	t1=$(now_ms)
	buf1=$(buffer_pool_reads)
	LAST_DUR_MS=$((t1 - t0))
	LAST_BUF_DELTA=$((buf1 - buf0))

	# 원복(측정 제외): 이번 회차 접두사로 넣은 행만 지운다.
	mysql_perf "DELETE FROM orders WHERE order_number LIKE 'WC-${phase}-${run}-%';" > /dev/null
}

# wc_targets 를 프로시저 한 번 호출로 전부 UPDATE 한다. 네트워크 왕복은 CALL 한 번뿐이라 행
# 수만큼 왕복하는 앱 트래픽과 달리 순수 서버 처리 시간만 잰다. mode=app 은 문장마다 커밋(앱 패턴,
# fsync 여러 번), mode=txn 은 트랜잭션 1개로 묶어 커밋 1번(인덱스 유지 비용, 판정 지표).
run_update_round() {
	local mode="$1"
	local proc_name buf0 buf1 t0 t1
	if [ "${mode}" = "txn" ]; then
		proc_name="wc_update_targets_txn"
	else
		proc_name="wc_update_targets"
	fi
	buf0=$(buffer_pool_reads)
	t0=$(now_ms)
	mysql_perf "CALL ${proc_name}();" > /dev/null
	t1=$(now_ms)
	buf1=$(buffer_pool_reads)
	LAST_DUR_MS=$((t1 - t0))
	LAST_BUF_DELTA=$((buf1 - buf0))

	# 원복(측정 제외): 대상 행을 PENDING 으로 되돌린다.
	restore_update_targets
}

# InnoDB 의 OPTIMIZE TABLE 은 테이블을 통째로 재생성한다(ALTER TABLE ... ENGINE=InnoDB 와 동일,
# 끝에서 ANALYZE 까지 자동으로 돈다). before 단계는 seed 직후라 조밀하지만, after 단계는 V11 DDL
# 로 인덱스만 다시 만든 상태라 그 전 INSERT/DELETE 로 생긴 파편이 그대로 남아 있다. 두 단계를
# 같은 조밀한 상태에서 시작하게 맞추고, 인덱스 크기도 이 직후 값으로 잰다.
optimize_orders_table() {
	local phase="$1"
	echo "  - ${phase} 단계: OPTIMIZE TABLE orders (재생성 + 재조밀화)"
	mysql_perf "OPTIMIZE TABLE orders;" > /dev/null
}

# mysql.innodb_index_stats 는 개발 DB 를 건드리지 않는 읽기 전용 조회다(database_name 으로
# groove_perf 만 필터). ANALYZE TABLE 로 통계를 먼저 갱신해야 stat_value 가 최신이다.
measure_index_sizes() {
	local phase="$1" name size
	mysql_perf "ANALYZE TABLE orders;" > /dev/null
	while IFS=$'\t' read -r name size; do
		[ -z "${name}" ] && continue
		if ! is_known_index "${name}"; then
			INDEX_NAMES+=("${name}")
		fi
		printf -v "IDXSIZE_${phase}_${name}" '%s' "${size}"
	done < <(mysql_perf "
		SELECT index_name, ROUND(stat_value * 16384 / 1024, 1)
		FROM mysql.innodb_index_stats
		WHERE database_name = '${PERF_SCHEMA}' AND table_name = 'orders' AND stat_name = 'size'
		ORDER BY index_name;
	")
}

INDEX_NAMES=()

is_known_index() {
	local target="$1" candidate
	if [ ${#INDEX_NAMES[@]} -eq 0 ]; then
		return 1
	fi
	for candidate in "${INDEX_NAMES[@]}"; do
		if [ "${candidate}" = "${target}" ]; then
			return 0
		fi
	done
	return 1
}

get_idx_size() {
	local varname="IDXSIZE_$1_$2"
	if [ -n "${!varname+set}" ]; then
		printf '%s' "${!varname}"
	else
		printf '%s' "-"
	fi
}

INSERT_MS_BEFORE=()
INSERT_MS_AFTER=()
BUF_INSERT_BEFORE=()
BUF_INSERT_AFTER=()

UPDATE_APP_MS_BEFORE=()
UPDATE_APP_MS_AFTER=()
UPDATE_APP_QPS_BEFORE=()
UPDATE_APP_QPS_AFTER=()
BUF_UPDATE_APP_BEFORE=()
BUF_UPDATE_APP_AFTER=()

UPDATE_TXN_MS_BEFORE=()
UPDATE_TXN_MS_AFTER=()
UPDATE_TXN_QPS_BEFORE=()
UPDATE_TXN_QPS_AFTER=()
BUF_UPDATE_TXN_BEFORE=()
BUF_UPDATE_TXN_AFTER=()

# mode(app|txn) 별 라운드 결과를 해당 단계/지표 배열에 담는다. bash 3.2 에는 연관 배열/nameref 가
# 없어 case 분기로 8개 배열 중 하나를 고른다.
record_update_result() {
	local phase="$1" mode="$2" qps="$3"
	if [ "${phase}" = "before" ] && [ "${mode}" = "app" ]; then
		UPDATE_APP_MS_BEFORE+=("${LAST_DUR_MS}")
		UPDATE_APP_QPS_BEFORE+=("${qps}")
		BUF_UPDATE_APP_BEFORE+=("${LAST_BUF_DELTA}")
	elif [ "${phase}" = "before" ] && [ "${mode}" = "txn" ]; then
		UPDATE_TXN_MS_BEFORE+=("${LAST_DUR_MS}")
		UPDATE_TXN_QPS_BEFORE+=("${qps}")
		BUF_UPDATE_TXN_BEFORE+=("${LAST_BUF_DELTA}")
	elif [ "${mode}" = "app" ]; then
		UPDATE_APP_MS_AFTER+=("${LAST_DUR_MS}")
		UPDATE_APP_QPS_AFTER+=("${qps}")
		BUF_UPDATE_APP_AFTER+=("${LAST_BUF_DELTA}")
	else
		UPDATE_TXN_MS_AFTER+=("${LAST_DUR_MS}")
		UPDATE_TXN_QPS_AFTER+=("${qps}")
		BUF_UPDATE_TXN_AFTER+=("${LAST_BUF_DELTA}")
	fi
}

run_phase() {
	local phase="$1" i qps

	optimize_orders_table "${phase}"

	echo "  - ${phase} 단계: 인덱스 크기 측정(OPTIMIZE 직후)"
	measure_index_sizes "${phase}"

	wait_for_settle "${phase}" "INSERT"
	warm_cache "${phase}"

	echo "  - ${phase} 단계: INSERT 워밍업 1회"
	run_insert_round "${phase}" 0

	echo "  - ${phase} 단계: INSERT ${RUNS}회"
	for ((i = 1; i <= RUNS; i++)); do
		run_insert_round "${phase}" "${i}"
		if [ "${phase}" = "before" ]; then
			INSERT_MS_BEFORE+=("${LAST_DUR_MS}")
			BUF_INSERT_BEFORE+=("${LAST_BUF_DELTA}")
		else
			INSERT_MS_AFTER+=("${LAST_DUR_MS}")
			BUF_INSERT_AFTER+=("${LAST_BUF_DELTA}")
		fi
		echo "    회차 ${i}: ${LAST_DUR_MS}ms (buffer_pool_reads +${LAST_BUF_DELTA})"
	done

	echo "  - ${phase} 단계: UPDATE 대상 확인"
	ensure_targets_pending

	wait_for_settle "${phase}" "UPDATE 앱 패턴"

	echo "  - ${phase} 단계: UPDATE(앱 패턴, 커밋 포함) 워밍업 1회"
	run_update_round app

	echo "  - ${phase} 단계: UPDATE(앱 패턴, 커밋 포함) ${RUNS}회"
	for ((i = 1; i <= RUNS; i++)); do
		run_update_round app
		qps=$(compute_qps "${UPDATE_ROWS}" "${LAST_DUR_MS}")
		record_update_result "${phase}" app "${qps}"
		echo "    회차 ${i}: ${LAST_DUR_MS}ms, ${qps}건/초 (buffer_pool_reads +${LAST_BUF_DELTA})"
	done

	wait_for_settle "${phase}" "UPDATE 인덱스 유지 비용"

	echo "  - ${phase} 단계: UPDATE(인덱스 유지 비용, 트랜잭션 1건) 워밍업 1회"
	run_update_round txn

	echo "  - ${phase} 단계: UPDATE(인덱스 유지 비용, 트랜잭션 1건) ${RUNS}회"
	for ((i = 1; i <= RUNS; i++)); do
		run_update_round txn
		qps=$(compute_qps "${UPDATE_ROWS}" "${LAST_DUR_MS}")
		record_update_result "${phase}" txn "${qps}"
		echo "    회차 ${i}: ${LAST_DUR_MS}ms, ${qps}건/초 (buffer_pool_reads +${LAST_BUF_DELTA})"
	done
}

list_v11_statements() {
	grep -iE '^(create|drop|alter)[[:space:]]' "${V11_FILE}"
}

# 표 1 의 한 행을 찍는다. bash 3.2 는 배열 nameref 가 없어 값 자체를 인자로 받는다.
report_row() {
	local phase="$1" label="$2" buf_note="$3"
	shift 3
	echo "| ${phase} | ${label} | $(median "$@") | $(min_max "$@") | $(join_values "$@") | ${buf_note} |"
}

write_report() {
	local mysql_version buffer_pool_size flush_setting applied_ddl name settle_entry
	mysql_version=$(mysql_perf "SELECT VERSION();")
	buffer_pool_size=$(mysql_perf "SHOW VARIABLES LIKE 'innodb_buffer_pool_size';" | awk '{ print $2 }')
	flush_setting=$(mysql_perf "SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';" | awk '{ print $2 }')
	applied_ddl=$(list_v11_statements)

	REPORT_TMP=$(mktemp)
	{
		echo "# 상태 인덱스 쓰기 비용 리포트 (V11)"
		echo
		echo "## 조건"
		echo
		echo "- 생성 시각: $(date '+%Y-%m-%d %H:%M:%S %Z')"
		echo "- 회차: ${RUNS} (단계별 워밍업 1회 별도, 미집계)"
		echo "- INSERT 행 수(회차당): ${INSERT_ROWS}"
		echo "- UPDATE 행 수(회차당): ${UPDATE_ROWS}"
		echo "- MySQL 버전: ${mysql_version}"
		echo "- innodb_buffer_pool_size: ${buffer_pool_size}"
		echo "- innodb_flush_log_at_trx_commit: ${flush_setting}"
		echo "- 단계 시작 전 OPTIMIZE TABLE orders (before/after 를 같은 조밀한 상태에서 시작)"
		echo "- 정착 대기(dirty page <= 100 목표, 10초 고정 대기 포함, 그룹별 목록):"
		if [ ${#SETTLE_LOG[@]} -gt 0 ]; then
			for settle_entry in "${SETTLE_LOG[@]}"; do
				echo "  - ${settle_entry}"
			done
		fi
		echo "- 적용한 DDL ($(basename "${V11_FILE}")):"
		echo '```sql'
		echo "${applied_ddl}"
		echo '```'
		echo
		echo "## 표 1: INSERT / UPDATE 처리 비용"
		echo
		echo "UPDATE(앱 패턴, 커밋 포함)은 문장마다 커밋해 fsync 지연이 섞인 참고용 지표다." \
			"UPDATE(인덱스 유지 비용, 트랜잭션 1건)이 판정 지표다."
		echo
		echo "| 단계 | 지표 | 중앙값 | 최소~최대 | 원값(회차별) | buffer_pool_reads 델타(회차별) |"
		echo "|---|---|---|---|---|---|"
		report_row before "INSERT ${INSERT_ROWS}건 적재 ms" "$(join_values "${BUF_INSERT_BEFORE[@]}")" \
			"${INSERT_MS_BEFORE[@]}"
		report_row after "INSERT ${INSERT_ROWS}건 적재 ms" "$(join_values "${BUF_INSERT_AFTER[@]}")" \
			"${INSERT_MS_AFTER[@]}"
		report_row before "UPDATE 처리량 건/초 (앱 패턴, 커밋 포함)" \
			"$(join_values "${BUF_UPDATE_APP_BEFORE[@]}")" "${UPDATE_APP_QPS_BEFORE[@]}"
		report_row after "UPDATE 처리량 건/초 (앱 패턴, 커밋 포함)" \
			"$(join_values "${BUF_UPDATE_APP_AFTER[@]}")" "${UPDATE_APP_QPS_AFTER[@]}"
		report_row before "UPDATE ${UPDATE_ROWS}건 총 ms (앱 패턴, 커밋 포함)" "-" "${UPDATE_APP_MS_BEFORE[@]}"
		report_row after "UPDATE ${UPDATE_ROWS}건 총 ms (앱 패턴, 커밋 포함)" "-" "${UPDATE_APP_MS_AFTER[@]}"
		report_row before "UPDATE 처리량 건/초 (인덱스 유지 비용, 트랜잭션 1건)" \
			"$(join_values "${BUF_UPDATE_TXN_BEFORE[@]}")" "${UPDATE_TXN_QPS_BEFORE[@]}"
		report_row after "UPDATE 처리량 건/초 (인덱스 유지 비용, 트랜잭션 1건)" \
			"$(join_values "${BUF_UPDATE_TXN_AFTER[@]}")" "${UPDATE_TXN_QPS_AFTER[@]}"
		report_row before "UPDATE ${UPDATE_ROWS}건 총 ms (인덱스 유지 비용, 트랜잭션 1건)" "-" \
			"${UPDATE_TXN_MS_BEFORE[@]}"
		report_row after "UPDATE ${UPDATE_ROWS}건 총 ms (인덱스 유지 비용, 트랜잭션 1건)" "-" \
			"${UPDATE_TXN_MS_AFTER[@]}"
		echo
		echo "## 표 2: 인덱스 크기(KB)"
		echo
		echo "| 인덱스 | before | after |"
		echo "|---|---|---|"
		if [ ${#INDEX_NAMES[@]} -gt 0 ]; then
			for name in "${INDEX_NAMES[@]}"; do
				echo "| ${name} | $(get_idx_size before "${name}") | $(get_idx_size after "${name}") |"
			done
		fi
	} > "${REPORT_TMP}"

	mkdir -p "$(dirname "${OUT_FILE}")"
	cp "${REPORT_TMP}" "${OUT_FILE}"
	echo "리포트: ${OUT_FILE}"
	cat "${REPORT_TMP}"
}

MEMBER_COUNT=0

main() {
	check_gitignore

	if [ "${PREPARE}" = true ]; then
		echo "[준비] index-explain.sh 로 V11 이전 상태 시드"
		"${INDEX_EXPLAIN}" --after-ddl "${V11_FILE}" --phase before --keep
	fi

	echo "[검증] ${PERF_SCHEMA} 가 V11 이전 상태인지 확인"
	check_before_state

	MEMBER_COUNT=$(mysql_perf "SELECT COUNT(*) FROM member;")
	if [ -z "${MEMBER_COUNT}" ] || [ "${MEMBER_COUNT}" = "0" ]; then
		echo "[안내] ${PERF_SCHEMA}.member 가 비어 있습니다. --prepare 로 먼저 시드하세요." >&2
		exit 2
	fi
	echo "[정보] member ${MEMBER_COUNT}건 (INSERT 시드가 이 안에서 member_id 를 흩뿌린다)"

	echo "[준비] wc_numbers(${INSERT_ROWS}행), wc_member_ids, wc_update_targets(_txn) 프로시저, wc_targets"
	ensure_wc_numbers "${INSERT_ROWS}"
	prepare_member_pool
	create_wc_procedures
	prepare_fixed_targets

	echo "[1/3] before 단계 측정"
	run_phase before

	echo "[적용] V11 DDL 적용: $(basename "${V11_FILE}")"
	mysql_perf_file "${V11_FILE}"

	echo "[2/3] after 단계 측정"
	run_phase after

	echo "[3/3] 리포트 작성"
	write_report

	echo "[정리] wc_targets 삭제"
	mysql_perf "DROP TABLE IF EXISTS wc_targets;" > /dev/null
}

main "$@"
