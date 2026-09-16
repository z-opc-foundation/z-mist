#!/usr/bin/env bash
# FEATURE022: z-mist 端到端 demo
# 覆盖：存 → 取（密文）→ 列表 → 限流触发 → 审计日志落库

set -e

HOST="${MIST_HOST:-http://localhost:8080}"
KEY="${MIST_KEY:-demo_db_password}"
GROUP="${MIST_GROUP:-demo}"
NS="${MIST_NS:-dev}"

echo "=== z-mist E2E Demo ==="
echo "HOST=$HOST  KEY=$KEY  GROUP=$GROUP  NS=$NS"
echo

echo "[1/5] POST /api/secret (create) ..."
curl -sS -X POST "$HOST/api/secret" \
  -H "Content-Type: application/json" \
  -H "X-Staff-No: demo-user" \
  -d "{
    \"secretKey\": \"$KEY\",
    \"group\": \"$GROUP\",
    \"namespace\": \"$NS\",
    \"appName\": \"demo\",
    \"encryptedValue\": \"super-secret-pwd-12345\",
    \"secretType\": \"password\"
  }" | python3 -m json.tool
echo

echo "[2/5] GET /api/secret/get ..."
curl -sS "$HOST/api/secret/get?secretKey=$KEY&group=$GROUP&namespace=$NS" \
  -H "X-Staff-No: demo-user" | python3 -m json.tool
echo

echo "[3/5] GET /api/secret/list?namespace=$NS ..."
curl -sS "$HOST/api/secret/list?namespace=$NS" \
  -H "X-Staff-No: demo-user" | python3 -m json.tool
echo

echo "[4/5] Flood GET to trigger rate limit (70 quick calls, limit 60/min) ..."
for i in $(seq 1 70); do
  status=$(curl -s -o /dev/null -w "%{http_code}" \
    "$HOST/api/secret/get?secretKey=$KEY&group=$GROUP&namespace=$NS" \
    -H "X-Staff-No: flood-bot")
  if [ "$status" != "200" ]; then
    echo "Call $i: HTTP $status (rate-limited) ✓"
    break
  fi
done
echo

echo "[5/5] Verify audit log in MySQL (need mysql client + access) ..."
echo "Run: SELECT op_type, operator, operator_ip, success, gmt_create"
echo "     FROM z_mist.z_mist_secret_access_log"
echo "     WHERE secret_key='$KEY' ORDER BY gmt_create DESC LIMIT 10;"
echo
echo "Expected: 4 rows (1 PUT, 2 GET, 1 LIST), 1 row with success=0 (rate limit)"
echo

echo "=== Demo done ==="
