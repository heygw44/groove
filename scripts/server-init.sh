#!/usr/bin/env bash
# GROOVE EC2(t3.micro, Ubuntu 24.04) 최초 초기화 스크립트 (docs/09-deployment.md 4.3절)
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
sudo mkdir -p /opt/groove/{uploads,backups,scripts,logs}
sudo mkdir -p /var/www/groove
sudo chown -R "$USER":"$USER" /opt/groove
sudo chown -R "$USER":www-data /var/www/groove

echo "== 7. Nginx + certbot 설치 =="
sudo -E apt-get install -y nginx python3-certbot-nginx
sudo systemctl enable --now nginx

echo "== 완료 =="
