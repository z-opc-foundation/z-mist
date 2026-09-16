#!/usr/bin/env bash
# ============================================================
# z-mist E2E 功能验证脚本 (FEATURE065 T4)
# ============================================================
# 用法: bash e2e-mist.sh [BASE_URL]
# 默认 BASE_URL=http://localhost:8888
# 依赖: curl, jq
# ============================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8888}"
PASS=0; FAIL=0; TOTAL=0

check() {
  local name="$1"; local code="$2"; local body="$3"
  TOTAL=$((TOTAL + 1))
  if [ "$code" = "200" ]; then
    echo "  ✅ $name (HTTP $code)"
    PASS=$((PASS + 1))
  else
    echo "  ❌ $name (HTTP $code)"
    echo "     Body: ${body:0:200}"
    FAIL=$((FAIL + 1))
  fi
}

echo "========================================="
echo " z-mist E2E — $BASE_URL"
echo "========================================="

echo ""
echo "1. Health Check"
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/mist/health")
check "GET /api/mist/health" "$CODE" ""

echo ""
echo "2. Auth (Login)"
LOGIN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' 2>/dev/null || true)
CODE=$(echo "$LOGIN" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '401')" 2>/dev/null || echo "500")
check "POST /api/auth/login" "$CODE" "$LOGIN"

echo ""
echo "3. Secret CRUD"
SAVE=$(curl -s -X POST "$BASE_URL/api/secret" \
  -H 'Content-Type: application/json' \
  -d '{"secretKey":"e2e_test_key","secretName":"E2E Test","encryptedValue":"hello-e2e","group":"e2e","namespace":"","secretType":"text","encryptAlgorithm":"AES"}' 2>/dev/null || true)
CODE=$(echo "$SAVE" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "POST /api/secret (save)" "$CODE" "$SAVE"

GET=$(curl -s "$BASE_URL/api/secret/get?secretKey=e2e_test_key&group=e2e&namespace=" 2>/dev/null || true)
CODE=$(echo "$GET" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '404')" 2>/dev/null || echo "500")
check "GET /api/secret/get" "$CODE" "$GET"

LIST=$(curl -s "$BASE_URL/api/secret/list?group=e2e" 2>/dev/null || true)
CODE=$(echo "$LIST" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "GET /api/secret/list" "$CODE" "$LIST"

echo ""
echo "4. Search"
SEARCH=$(curl -s "$BASE_URL/api/secret/search?keyword=e2e&group=e2e" 2>/dev/null || true)
CODE=$(echo "$SEARCH" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "GET /api/secret/search" "$CODE" "$SEARCH"

echo ""
echo "5. EaaS (Encrypt/Decrypt)"
ENCRYPT=$(curl -s -X POST "$BASE_URL/api/eaas/encrypt" \
  -H 'Content-Type: application/json' \
  -d '{"plainText":"e2e-secret-123","algorithm":"AES"}' 2>/dev/null || true)
CODE=$(echo "$ENCRYPT" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "POST /api/eaas/encrypt" "$CODE" "$ENCRYPT"

CIPHER=$(echo "$ENCRYPT" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',''))" 2>/dev/null || echo "")
if [ -n "$CIPHER" ]; then
  DECRYPT=$(curl -s -X POST "$BASE_URL/api/eaas/decrypt" \
    -H 'Content-Type: application/json' \
    -d "{\"cipherText\":\"$CIPHER\",\"algorithm\":\"AES\"}" 2>/dev/null || true)
  DECRYPTED=$(echo "$DECRYPT" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',''))" 2>/dev/null || echo "")
  if [ "$DECRYPTED" = "e2e-secret-123" ]; then
    check "POST /api/eaas/decrypt (roundtrip)" "200" "$DECRYPT"
  else
    check "POST /api/eaas/decrypt (roundtrip FAIL)" "500" "expected 'e2e-secret-123', got '$DECRYPTED'"
  fi
fi

echo ""
echo "6. Dynamic Secret"
DYN=$(curl -s -X POST "$BASE_URL/api/secret/dynamic/generate?ttlSeconds=60" 2>/dev/null || true)
CODE=$(echo "$DYN" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "POST /api/secret/dynamic/generate" "$CODE" "$DYN"

echo ""
echo "7. History"
HIST=$(curl -s "$BASE_URL/api/secret/history/list?secretKey=e2e_test_key&group=e2e" 2>/dev/null || true)
CODE=$(echo "$HIST" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "GET /api/secret/history/list" "$CODE" "$HIST"

echo ""
echo "8. Rotate"
ROTATE=$(curl -s -X POST "$BASE_URL/api/secret/rotate?secretKey=e2e_test_key&group=e2e&namespace=&newValueLength=32" 2>/dev/null || true)
CODE=$(echo "$ROTATE" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "POST /api/secret/rotate" "$CODE" "$ROTATE"

echo ""
echo "9. App CRUD"
APP=$(curl -s -X POST "$BASE_URL/api/app" \
  -H 'Content-Type: application/json' \
  -d '{"appName":"e2e-test-app","appType":"server","namespace":"e2e"}' 2>/dev/null || true)
CODE=$(echo "$APP" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "POST /api/app (create)" "$CODE" "$APP"

echo ""
echo "10. ACL Check"
ACL=$(curl -s -X POST "$BASE_URL/api/acl" \
  -H 'Content-Type: application/json' \
  -d '{"secretKey":"e2e_test_key","group":"e2e","authorizedApp":"e2e-test-app","authorizedEnv":"dev","permissionLevel":"read"}' 2>/dev/null || true)
CODE=$(echo "$ACL" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "POST /api/acl (create)" "$CODE" "$ACL"

CHECK=$(curl -s "$BASE_URL/api/acl/check?secretKey=e2e_test_key&group=e2e&authorizedApp=e2e-test-app&authorizedEnv=dev" 2>/dev/null || true)
CODE=$(echo "$CHECK" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "GET /api/acl/check" "$CODE" "$CHECK"

echo ""
echo "11. Stats"
STATS=$(curl -s "$BASE_URL/api/stats/overview" 2>/dev/null || true)
CODE=$(echo "$STATS" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "GET /api/stats/overview" "$CODE" "$STATS"

echo ""
echo "12. Access Log"
LOG=$(curl -s "$BASE_URL/api/log/recent?limit=5" 2>/dev/null || true)
CODE=$(echo "$LOG" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "GET /api/log/recent" "$CODE" "$LOG"

echo ""
echo "13. Bulk Export"
EXPORT=$(curl -s "$BASE_URL/api/bulk/export?group=e2e" 2>/dev/null || true)
CODE=$(echo "$EXPORT" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "GET /api/bulk/export" "$CODE" "$EXPORT"

echo ""
echo "14. Envelope Encryption"
ENVELOPE=$(curl -s -X POST "$BASE_URL/api/master-key/envelope?plainText=e2e-envelope-test" 2>/dev/null || true)
CODE=$(echo "$ENVELOPE" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "POST /api/master-key/envelope" "$CODE" "$ENVELOPE"

# Roundtrip
WK=$(echo "$ENVELOPE" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('wrappedKey',''))" 2>/dev/null || echo "")
CT=$(echo "$ENVELOPE" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('cipherText',''))" 2>/dev/null || echo "")
if [ -n "$WK" ] && [ -n "$CT" ]; then
  UNENVELOPE=$(curl -s -X POST "$BASE_URL/api/master-key/unenvelope?wrappedKey=$(python3 -c "import urllib.parse;print(urllib.parse.quote('$WK'))")&cipherText=$(python3 -c "import urllib.parse;print(urllib.parse.quote('$CT'))")" 2>/dev/null || true)
  UNENVELOPED=$(echo "$UNENVELOPE" | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',''))" 2>/dev/null || echo "")
  if [ "$UNENVELOPED" = "e2e-envelope-test" ]; then
    check "POST /api/master-key/unenvelope (roundtrip)" "200" "$UNENVELOPE"
  else
    check "POST /api/master-key/unenvelope (roundtrip FAIL)" "500" "expected 'e2e-envelope-test', got '$UNENVELOPED'"
  fi
fi

echo ""
echo "15. Cleanup"
DEL=$(curl -s -X DELETE "$BASE_URL/api/secret?secretKey=e2e_test_key&group=e2e&namespace=" 2>/dev/null || true)
CODE=$(echo "$DEL" | python3 -c "import sys,json;d=json.load(sys.stdin);print('200' if d.get('success') else '500')" 2>/dev/null || echo "500")
check "DELETE /api/secret (cleanup)" "$CODE" "$DEL"

echo ""
echo "========================================="
echo " Results: $PASS passed / $FAIL failed / $TOTAL total"
echo "========================================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
