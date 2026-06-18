#!/usr/bin/env bash
#
# TLS(HTTPS :443) 스택 구동 래퍼 (이슈 #270).
#
#   인증서 선생성 + NGINX_CONF 고정 + override 합성을 한곳에서 처리한다. 이 둘이 분리돼 있으면:
#   ① certs/ 없이 compose 를 먼저 띄우면 Docker 가 ./certs 를 root 소유 빈 디렉토리로 만들어
#      nginx cert 로드가 실패하고, 이후 gen 스크립트까지 권한오류로 막힌다.
#   ② NGINX_CONF 를 빠뜨리면 base 가 nginx.conf(80 only)를 마운트해 443 만 발행되고 리스너가 없다.
#   래퍼가 항상 (인증서 보장 → conf 고정 → compose)를 보장해 두 footgun 을 모두 차단한다.
#
# 사용법:
#   ./scripts/up-tls.sh                 # up -d (기본)
#   ./scripts/up-tls.sh logs -f nginx   # 임의 compose 서브커맨드 전달
#   ./scripts/up-tls.sh down            # 동일 파일셋으로 정리(teardown 도 -f 일관)
#
# CERT_HOST=example.com ./scripts/up-tls.sh   # 인증서 호스트명 변경
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# 1) 인증서 보장 — 없을 때만 생성(있으면 gen 스크립트가 멱등 종료).
if [[ ! -f certs/cert.pem || ! -f certs/key.pem ]]; then
  ./scripts/gen-self-signed-cert.sh "${CERT_HOST:-localhost}"
fi

# 2) 서브커맨드 기본값 up -d.
if [[ $# -eq 0 ]]; then
  set -- up -d
fi

# 3) NGINX_CONF 고정 + 두 compose 파일 합성(up/down/logs 모두 동일 파일셋).
NGINX_CONF=nginx-tls.conf exec docker compose \
  -f docker-compose.yml -f docker-compose.tls.yml "$@"
