#!/bin/bash
# 1. Register/Login
TOKEN=$(curl -s -X POST http://localhost:8082/api/auth/register -H "Content-Type: application/json" -d '{"username":"admin","password":"password"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
    TOKEN=$(curl -s -X POST http://localhost:8082/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"password"}' | grep -o '"token":"[^"]*' | cut -d'"' -f4)
fi
echo "Token: $TOKEN"
# 2. Get Files
curl -s -X GET http://localhost:8082/api/transcode/files -H "Authorization: Bearer $TOKEN"
