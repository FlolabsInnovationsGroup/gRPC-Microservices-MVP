#!/bin/bash

echo "=== Test 1: HTTP entry point ==="
curl http://localhost:8081/ping
echo -e "\n"

echo "=== Test 2: Python service should NOT be externally accessible ==="
curl http://localhost:50051 2>&1 || echo "✓ Connection refused (expected)"
echo -e "\n"

echo "=== Test 3: Check Python logs (bidirectional gRPC) ==="
docker-compose logs python-service | grep -E "(connecting|got from Java)"
echo -e "\n"

echo "=== Test 4: Environment variable configuration ==="
docker-compose exec java-service env | grep SERVICE
docker-compose exec python-service env | grep SERVICE
