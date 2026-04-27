#!/bin/bash

# Renk kodları
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${RED}🛑 Docker servisleri durduruluyor...${NC}"

# Docker compose ile durdur ve yetim container'ları temizle
docker compose down --remove-orphans

echo -e "${GREEN}✅ Servisler başarıyla durduruldu.${NC}"
