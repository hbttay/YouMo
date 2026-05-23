#!/bin/bash
# 余墨 API 全量自动化测试 (含 CRUD)
BASE="http://localhost:8080/api"
PASS=0
FAIL=0
TMP="/tmp/youmo_test.json"

check() {
  local label="$1"; shift
  local code=$(curl -s -o "$TMP" -w "%{http_code}" "$@")
  local body=$(cat "$TMP")
  if [ "$code" = "200" ] || [ "$code" = "201" ]; then
    echo "  [OK] $label"
    PASS=$((PASS + 1))
  else
    echo "  [FAIL] $label (HTTP $code): $(echo "$body" | head -c 200)"
    FAIL=$((FAIL + 1))
  fi
}

echo "========== 余墨 API 全量测试 =========="
echo ""

# ---- Book CRUD ----
echo "--- Book ---"
check "列表"       -X GET "$BASE/books"
check "创建"       -X POST "$BASE/books" -H "Content-Type: application/json" -d '{"title":"Test Book","creation_mode":"LINEAR","length_type":"SHORT"}'
check "详情"       -X GET "$BASE/books/1"
check "更新"       -X PUT "$BASE/books/1" -H "Content-Type: application/json" -d '{"title":"Updated Book"}'

# ---- Character CRUD ----
echo "--- Character ---"
check "列表"       -X GET "$BASE/books/1/characters"
check "创建"       -X POST "$BASE/books/1/characters" -H "Content-Type: application/json" -d '{"name":"Test","gender":"Male","depth_level":"L1"}'
CHAR_ID=$(grep -o '"id":[0-9]*' "$TMP" | head -1 | cut -d: -f2)
check "更新"       -X PUT "$BASE/books/1/characters/$CHAR_ID" -H "Content-Type: application/json" -d '{"name":"Test Updated","gender":"Female","depth_level":"L2"}'
check "删除"       -X DELETE "$BASE/books/1/characters/$CHAR_ID"

# ---- Outline CRUD ----
echo "--- Outline ---"
check "列表"       -X GET "$BASE/books/1/outline"
check "创建"       -X POST "$BASE/books/1/outline/node" -H "Content-Type: application/json" -d '{"title":"Test Vol","node_type":"VOLUME","sequence":0}'
NODE_ID=$(grep -o '"id":[0-9]*' "$TMP" | head -1 | cut -d: -f2)
check "更新"       -X PUT "$BASE/books/1/outline/$NODE_ID" -H "Content-Type: application/json" -d '{"title":"Updated Vol","node_type":"CHAPTER","sequence":1}'
check "删除"       -X DELETE "$BASE/books/1/outline/$NODE_ID"

# ---- World Setting CRUD ----
echo "--- World ---"
check "读取"       -X GET "$BASE/books/1/world-setting"
check "保存"       -X PUT "$BASE/books/1/world-setting" -H "Content-Type: application/json" -d '{"era":"Test","geography":"Test Land","history_events":"[]","politics":"Test","economy":"Test","culture":"Test","military":"Test","core_rule_type":"Test","core_rule_summary":"Summary"}'

echo ""
echo "========== $PASS 通过, $FAIL 失败 =========="
[ "$FAIL" -eq 0 ] && echo ">>> 全部通过, 前端可验证 <<<"
