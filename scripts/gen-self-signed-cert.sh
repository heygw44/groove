#!/usr/bin/env bash
#
# self-signed TLS 인증서 생성 (이슈 #270) — TLS(HTTPS :443) 데모용.
#
#   nginx-tls.conf 가 참조하는 certs/cert.pem · certs/key.pem 을 생성한다. 운영에서는 Let's Encrypt/
#   certbot 발급 인증서를 같은 경로에 두면 된다(이 스크립트는 로컬·시연 전용 self-signed).
#
# 사용법:
#   ./scripts/gen-self-signed-cert.sh                # CN/SAN=localhost, 825일
#   ./scripts/gen-self-signed-cert.sh example.com    # 다른 호스트명으로
#
# 이후: NGINX_CONF=nginx-tls.conf docker compose -f docker-compose.yml -f docker-compose.tls.yml up
# 상세 절차: docs/ARCHITECTURE.md §10.6
#
# 산출물 certs/*.pem 는 .gitignore(*.pem/*.key) 로 커밋되지 않는다. self-signed 라 브라우저/curl 은
# 신뢰 경고를 띄우므로 검증 시 curl -k 를 쓴다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERT_DIR="$ROOT_DIR/certs"
HOST="${1:-localhost}"
DAYS=825   # 공개 신뢰 CA 의 최대 수명 관행에 맞춘 상한

mkdir -p "$CERT_DIR"

if [[ -f "$CERT_DIR/cert.pem" && -f "$CERT_DIR/key.pem" ]]; then
  echo "이미 존재: $CERT_DIR/{cert,key}.pem — 재생성하려면 먼저 삭제하세요."
  exit 0
fi

echo "self-signed 인증서 생성 → $CERT_DIR (CN/SAN=$HOST, ${DAYS}일)"
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$CERT_DIR/key.pem" \
  -out "$CERT_DIR/cert.pem" \
  -days "$DAYS" \
  -subj "/CN=$HOST" \
  -addext "subjectAltName=DNS:$HOST,DNS:localhost,IP:127.0.0.1"

chmod 600 "$CERT_DIR/key.pem"
echo "완료: $CERT_DIR/cert.pem, $CERT_DIR/key.pem"
