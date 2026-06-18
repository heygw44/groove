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
# 보통은 ./scripts/up-tls.sh 가 필요 시 자동 호출한다. 상세 절차: docs/ARCHITECTURE.md §10.6
#
# 산출물 certs/*.pem 는 .gitignore(*.pem/*.key) 로 커밋되지 않는다. self-signed 라 브라우저/curl 은
# 신뢰 경고를 띄우므로 검증 시 curl -k 를 쓴다.
#
# 이식성: SAN 을 -addext 대신 임시 config 로 넘긴다. -addext 는 OpenSSL 1.1.1+ 전용이라
# 맥 기본 LibreSSL(/usr/bin/openssl)·구버전에서 실패한다.
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

# SAN — HOST 가 localhost 면 중복 추가하지 않는다.
san="DNS:${HOST},IP:127.0.0.1"
[[ "$HOST" != "localhost" ]] && san="DNS:${HOST},DNS:localhost,IP:127.0.0.1"

conf="$(mktemp)"
# 실패 시 임시 config·부분 산출물(.tmp)을 정리 — certs/ 를 all-or-nothing 으로 유지.
trap 'rm -f "$conf" "$CERT_DIR/cert.pem.tmp" "$CERT_DIR/key.pem.tmp"' EXIT
cat > "$conf" <<EOF
[req]
distinguished_name = dn
x509_extensions = v3
prompt = no
[dn]
CN = ${HOST}
[v3]
subjectAltName = ${san}
EOF

echo "self-signed 인증서 생성 → $CERT_DIR (CN/SAN=$HOST, ${DAYS}일)"
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$CERT_DIR/key.pem.tmp" \
  -out "$CERT_DIR/cert.pem.tmp" \
  -days "$DAYS" \
  -config "$conf"

# openssl 성공 후에만 제자리로 이동 — 중간 실패 시 실파일은 건드리지 않는다.
chmod 600 "$CERT_DIR/key.pem.tmp"
mv "$CERT_DIR/key.pem.tmp" "$CERT_DIR/key.pem"
mv "$CERT_DIR/cert.pem.tmp" "$CERT_DIR/cert.pem"
echo "완료: $CERT_DIR/cert.pem, $CERT_DIR/key.pem"
