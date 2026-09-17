#!/usr/bin/env bash
# GROOVE EC2(t3.micro, Ubuntu 24.04) 최초 초기화 스크립트
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
export NEEDRESTART_MODE=a

echo "== 1. 시스템 업데이트 =="
sudo -E apt-get update -y
sudo -E apt-get upgrade -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold"

echo "== 2. 타임존 설정 (Asia/Seoul) =="
sudo timedatectl set-timezone Asia/Seoul

echo "== 3. swap 2GB 생성 (1GB RAM 보완) =="
if [ ! -f /swapfile ]; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi
sudo sysctl vm.swappiness=10
grep -q '^vm.swappiness' /etc/sysctl.conf || echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
free -h

echo "== 3-1. SYN 큐 상한 조정 (기본 128 은 1000+ VU 러시에서 syncookie 로 넘어간다) =="
sudo tee /etc/sysctl.d/99-groove.conf > /dev/null <<'EOF'
net.ipv4.tcp_max_syn_backlog = 4096
net.core.somaxconn = 4096
EOF
sudo sysctl --system

echo "== 4. Docker + Compose plugin 설치 =="
sudo -E apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo -E apt-get update -y
sudo -E apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"

echo "== 5. ufw 방화벽 설정 =="
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw --force enable
sudo ufw status verbose

echo "== 6. 운영 디렉터리 생성 =="
sudo mkdir -p /opt/groove/{uploads,backups,scripts,logs,nginx}
sudo mkdir -p /var/www/groove
sudo chown -R "$USER":"$USER" /opt/groove
sudo chown -R "$USER":www-data /var/www/groove

echo "== 7. Nginx + certbot 설치 =="
sudo -E apt-get install -y nginx python3-certbot-nginx
sudo systemctl enable --now nginx

echo "== 8. Nginx 설정 디렉터리 정리 =="
sudo mkdir -p /opt/groove/nginx && sudo chown "$USER":"$USER" /opt/groove/nginx
sudo rm -f /etc/nginx/sites-enabled/default
echo "Nginx 설정은 배포 워크플로가 infra/scripts/deploy-nginx.sh 로 올린다(첫 설치는 infra/nginx/ 디렉터리의 설정을 참고)"

echo "== 완료 =="
