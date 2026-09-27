#!/usr/bin/env bash
# 운영 nginx 의 로그인·회원가입 rate limit(10r/m, burst 5) 허용 목록에 내 공인 IP 를 넣고 뺀다.
# k6 setup 은 한 IP 에서 VU 수만큼 로그인하므로 허용 목록 없이는 7번째 요청부터 429 가 난다.
# IP 는 SSH 접속의 출발지(원격 $SSH_CLIENT)로 정한다 — HTTPS 요청과 같은 경로로 나가기 때문이다.
# 허용 목록 파일은 서버에만 있고 저장소에는 없다(infra/nginx/nginx.conf 의 geo 참고).
# shellcheck disable=SC2086 # SSH_OPTS 는 여러 -o 플래그를 담는 문자열이라 의도적으로 언쿼팅
set -euo pipefail

SSH_KEY="${SSH_KEY:?SSH_KEY(pem 경로)를 지정하세요}"
SSH_HOST="${SSH_HOST:?SSH_HOST(예: ubuntu@<EC2-IP>)를 지정하세요}"
SSH_OPTS="${SSH_OPTS:--o ConnectTimeout=8 -o BatchMode=yes}"

ALLOW_FILE="/etc/nginx/groove-ratelimit-allow.conf"

usage() {
	cat <<EOF
사용법:
  $(basename "$0") on       내 공인 IP 를 허용 목록에 넣고 nginx reload
  $(basename "$0") off      허용 목록을 비우고 nginx reload
  $(basename "$0") status   허용 목록 내용 출력

환경변수: SSH_KEY(필수, pem 경로), SSH_HOST(필수, 예: ubuntu@<EC2-IP>), SSH_OPTS
EOF
}

remote() {
	ssh $SSH_OPTS -i "$SSH_KEY" "$SSH_HOST" "$@"
}

case "${1:-}" in
on)
	remote "set -e
		ip=\${SSH_CLIENT%% *}
		[ -n \"\$ip\" ] || { echo 'SSH_CLIENT 에서 IP 를 얻지 못했다' >&2; exit 1; }
		printf '%s 1;\n' \"\$ip\" | sudo tee $ALLOW_FILE > /dev/null
		sudo nginx -t -q && sudo systemctl reload nginx
		echo \"허용 목록: \$ip\""
	;;
off)
	remote "set -e
		sudo truncate -s 0 $ALLOW_FILE
		sudo nginx -t -q && sudo systemctl reload nginx
		echo '허용 목록 비움'"
	;;
status)
	remote "cat $ALLOW_FILE"
	;;
*)
	usage
	exit 2
	;;
esac
