#!/bin/bash

# Renk tanımları
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Yaren Transcoder Docker Başlatıcıya Hoş Geldiniz!${NC}"
echo "---------------------------------------------------"

# Port kontrol fonksiyonu
# Port kontrol fonksiyonu
check_and_kill_port() {
    PORT=$1
    SERVICE_NAME=$2
    
    # Portu kullanan PID'i bul (sadece LISTEN)
    PIDS=$(lsof -i tcp:${PORT} | grep LISTEN | awk '{print $2}' | sort -u)
    
    if [ ! -z "$PIDS" ]; then
        echo -e "${YELLOW}⚠️  ${PORT} portu kullanımda (PID: $PIDS). Servis: $SERVICE_NAME${NC}"
        
        for pid in $PIDS; do
            # Check process name to avoid killing Docker itself
            PNAME=$(ps -p $pid -o comm= 2>/dev/null || echo "unknown")
            if [[ "$PNAME" == *"Docker"* ]] || [[ "$PNAME" == *"com.docker"* ]]; then
                 echo -e "${YELLOW}Skipping kill for Docker process (PID: $pid, Name: $PNAME)${NC}"
                 continue
            fi

            echo -e "${RED}🛑 İşlem sonlandırılıyor (PID: $pid)...${NC}"
            kill -9 $pid 2>/dev/null || true
        done
        
        sleep 1
        echo -e "${GREEN}✅ Port ${PORT} kontrolü tamamlandı.${NC}"
    else
        echo -e "${GREEN}✅ Port ${PORT} müsait.${NC}"
    fi
}

echo "🛑 Mevcut konteynerler durduruluyor..."
docker compose down || true

echo "🔍 Port kontrolleri yapılıyor..."

# 1. Backend Portu (8082)
check_and_kill_port 8082 "Java/Backend"

# 2. Frontend Portu (80)
# Sistem servisleri (Apache vs) 80'i kullanabilir, bunu kontrol ediyoruz.
HTTP_PID=$(lsof -i tcp:80 | grep LISTEN | awk '{print $2}')
if [ ! -z "$HTTP_PID" ]; then
    echo -e "${YELLOW}⚠️  80 portu (HTTP) dolu! (PID: $HTTP_PID)${NC}"
    echo -e "${YELLOW}Docker Frontend servisi bu portu kullanıyor. Çakışmayı önlemek için bu servisi durduruyorum...${NC}"
    kill -9 $HTTP_PID 2>/dev/null || true
fi

# 3. Postgres Portu (5432)
# Yerel Postgres varsa durdur
check_and_kill_port 5432 "PostgreSQL"

echo "---------------------------------------------------"
echo -e "${GREEN}🐳 Docker Compose başlatılıyor...${NC}"

# Docker compose başlat
docker compose up -d --build

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo "---------------------------------------------------"
    echo -e "${GREEN}✅ Tüm servisler başarıyla ayağa kalktı!${NC}"
    echo ""
    echo -e "👉 Frontend: ${GREEN}http://localhost${NC}"
    echo -e "👉 Backend API: ${GREEN}http://localhost:8082${NC}"
    echo -e "👉 Veritabanı: localhost:5432"
    echo ""
    echo "📂 Dosya yönetimi:"
    echo "   - Yüklenenler: ./data/uploads"
    echo "   - Çıktılar:    ./data/outputs"
    echo ""
    docker compose ps
else
    echo -e "${RED}❌ Docker başlatılırken bir hata oluştu!${NC}"
    exit 1
fi
