#!/usr/bin/env bash
# 저장소의 nginx 설정을 서버에 반영한다. 변경이 없으면 아무것도 안 하고,
# nginx -t 가 실패하면 백업으로 되돌려 컨테이너 재기동 단계가 돌지 않게 한다.
# 호출자가 /opt/groove 로 cd 한 뒤 실행한다.
set -euo pipefail

SRC_DIR="${SRC_DIR:-/opt/groove/nginx}"
NGINX_CONF="/etc/nginx/nginx.conf"
SITE_CONF="/etc/nginx/sites-available/groove.conf"

# 이번 실행에서 실제로 덮어쓴 대상만 담는다 — 복원 대상을 이걸로 한정해야
# 이전 배포가 남긴 다른 파일의 .bak 을 엉뚱하게 다시 덮어쓰지 않는다.
CHANGED_DESTS=()

log() {
	printf '%s %s\n' "$(date '+%F %T')" "$*"
}

# $1: 원본, $2: 대상. 다르면 (있으면) 대상을 .bak 으로 백업한 뒤 덮어쓰고 CHANGED_DESTS 에 기록한다. 바뀌었으면 0, 그대로면 1.
deploy_file() {
	local src="$1"
	local dest="$2"

	if [ -f "$dest" ] && cmp -s "$src" "$dest"; then
		log "변경 없음: ${dest}"
		return 1
	fi

	if [ -f "$dest" ]; then
		sudo cp -p "$dest" "${dest}.bak"
	fi
	sudo cp "$src" "$dest"
	CHANGED_DESTS+=("$dest")
	log "적용함: ${dest}"
	return 0
}

# 이번 실행에서 바뀐 대상만 되돌린다. 원래 파일이 없어서 .bak 도 없는 경우엔
# 새로 만든 파일을 지워 "없음" 이던 이전 상태로 돌아간다.
restore_backups() {
	local dest
	for dest in "${CHANGED_DESTS[@]}"; do
		if [ -f "${dest}.bak" ]; then
			sudo cp -p "${dest}.bak" "$dest"
			log "복원함: ${dest}"
		else
			sudo rm -f "$dest"
			log "삭제함(원래 없던 파일): ${dest}"
		fi
	done
}

changed=0
if deploy_file "${SRC_DIR}/nginx.conf" "$NGINX_CONF"; then
	changed=1
fi
if deploy_file "${SRC_DIR}/groove.conf" "$SITE_CONF"; then
	changed=1
fi

sudo ln -sf "$SITE_CONF" /etc/nginx/sites-enabled/groove.conf
sudo rm -f /etc/nginx/sites-enabled/default

if [ "$changed" -eq 0 ]; then
	log "변경 없음: nginx 설정 유지"
	exit 0
fi

if sudo nginx -t; then
	sudo systemctl reload nginx
	if systemctl is-active --quiet nginx; then
		log "nginx reload 성공"
		exit 0
	fi
	log "reload 후 nginx 가 active 상태가 아님"
	exit 1
fi

log "nginx -t 실패, 백업으로 복원 중"
restore_backups

if sudo nginx -t; then
	log "복원 확인됨: nginx -t 통과"
else
	log "복원 후에도 nginx -t 실패 — 수동 확인 필요"
fi

exit 1
