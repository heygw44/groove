#!/usr/bin/env bash
# 백엔드 컨테이너를 새 이미지로 교체하고 컨테이너 헬스체크로 확인한다.
# 실패하면 교체 전 이미지로 자동 롤백한다. 호출자가 /opt/groove 로 cd 한 뒤 실행한다.
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"
CONTAINER="${CONTAINER:-groove-backend}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"
SKIP_PULL="${SKIP_PULL:-0}"
POLL_INTERVAL=5

log() {
	printf '%s %s\n' "$(date '+%F %T')" "$*"
}

# 컨테이너 헬스 상태를 폴링한다. healthy 면 0, unhealthy·exited·restarting·타임아웃이면 1.
wait_healthy() {
	local elapsed=0
	local health state
	while [ "$elapsed" -lt "$HEALTH_TIMEOUT" ]; do
		health=$(docker inspect --type container -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$CONTAINER" 2>/dev/null || true)
		state=$(docker inspect --type container -f '{{.State.Status}}' "$CONTAINER" 2>/dev/null || true)
		log "헬스체크 대기 중: health=${health:-none} state=${state:-none} (${elapsed}s/${HEALTH_TIMEOUT}s)"
		if [ "$health" = "healthy" ]; then
			return 0
		fi
		if [ "$health" = "unhealthy" ]; then
			return 1
		fi
		case "$state" in
			exited | restarting)
				return 1
				;;
		esac
		sleep "$POLL_INTERVAL"
		elapsed=$((elapsed + POLL_INTERVAL))
	done
	return 1
}

prev_tag=""
if docker inspect --type container "$CONTAINER" >/dev/null 2>&1; then
	prev_image=$(docker inspect --type container -f '{{.Config.Image}}' "$CONTAINER")
	prev_tag="${prev_image##*:}"
fi
log "교체 전 이미지 태그: ${prev_tag:-(없음)}"

new_tag=$(grep '^BACKEND_IMAGE_TAG=' "$ENV_FILE" | tail -n1 | cut -d= -f2- | tr -d '\r')
log "새 이미지 태그: ${new_tag:-(없음)}"

if [ "$SKIP_PULL" != "1" ]; then
	log "이미지 pull 중"
	docker compose -f "$COMPOSE_FILE" pull
fi

log "컨테이너 기동 중"
docker compose -f "$COMPOSE_FILE" up -d

if wait_healthy; then
	log "배포 성공: ${CONTAINER} healthy (태그 ${new_tag:-(없음)})"
	exit 0
fi

log "배포 실패: ${CONTAINER} 가 healthy 상태에 도달하지 못함"
docker logs --tail 80 "$CONTAINER" || true

if [ -z "$prev_tag" ] || [ "$prev_tag" = "$new_tag" ]; then
	log "되돌릴 이미지 없음 (이전 태그: ${prev_tag:-없음}, 새 태그: ${new_tag:-없음})"
	exit 1
fi

log "롤백 시작: ${new_tag} -> ${prev_tag}"
# 뒤에 온 값이 이겨야 하므로 새 값을 파일 끝에 덧붙인다. 이후 수동 up -d 가 깨진 이미지로 돌아가지 않는다.
printf 'BACKEND_IMAGE_TAG=%s\n' "$prev_tag" >> "$ENV_FILE"
docker compose -f "$COMPOSE_FILE" up -d backend

if wait_healthy; then
	log "롤백 성공: ${CONTAINER} 가 이전 태그(${prev_tag})로 복구됨"
else
	log "롤백도 실패: ${CONTAINER} 가 이전 태그(${prev_tag})로도 healthy 상태에 도달하지 못함"
	docker logs --tail 80 "$CONTAINER" || true
fi

# 한계: 새 이미지가 Flyway 마이그레이션을 이미 적용했다면 이전 이미지는 validate 에 실패해 롤백도 unhealthy 가 될 수 있다.
exit 1
