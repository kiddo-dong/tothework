#!/bin/bash
# 근로기준법·세무 Q&A 테스트 (매장 무관 전역 기능)
# 사용법: ./labor-qa.sh {accessToken} [adminIngestToken]
#
# 지식베이스 적재는 기동 시 자동으로 돌지 않는다. 문서를 고쳤을 때 관리자가 직접 부른다.
# adminIngestToken은 서버의 ADMIN_INGEST_TOKEN 환경변수와 같아야 하며,
# 서버에 그 값이 없으면 적재 API 자체가 닫혀 있다.
BASE_URL="http://localhost:8080"
TOKEN="$1"
ADMIN_TOKEN="$2"

# 긴 토큰을 붙여넣다 터미널에서 줄이 접히면 인자가 잘려 전부 401이 된다.
# 그 상태로 끝까지 실행하면 원인이 안 보이므로 여기서 멈춘다.
if [ -z "$TOKEN" ]; then
  echo "accessToken이 비었다. 토큰을 직접 붙여넣지 말고 아래처럼 셸 변수로 받아 쓰는 편이 안전하다:"
  echo ""
  echo '  TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \'
  echo '    -d "{\"email\":\"test@albam.dev\",\"password\":\"Test1234!\"}" \'
  echo '    http://localhost:8080/api/v1/auth/login | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"accessToken\"])")'
  echo ""
  echo "  $0 \"\$TOKEN\" {adminIngestToken}"
  exit 1
fi

if [ -z "$ADMIN_TOKEN" ]; then
  echo "(adminIngestToken이 없어 0번 적재는 건너뛴다. 지식베이스가 비어 있으면 질문에 답할 근거가 없다.)"
  echo ""
fi

if [ -n "$ADMIN_TOKEN" ]; then
  echo "== 0. 지식베이스 적재 (관리자) =="
  echo "   청크 ID가 내용 기반이라 여러 번 불러도 중복되지 않고 덮어쓴다."
  curl -s -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Admin-Token: $ADMIN_TOKEN" \
    "$BASE_URL/api/v1/labor-qa/admin/ingest" | python3 -m json.tool
  echo ""
fi

echo "== 1. 단발 질문 (대화 이력 없음) =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"주휴수당은 언제 받을 수 있나요?"}' \
  "$BASE_URL/api/v1/labor-qa/ask" | python3 -m json.tool

echo ""
echo "== 2. 범위 밖 질문 (근로·급여 밖이면 답하지 않고 안내만 한다) =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"김치찌개 맛있게 끓이는 법 알려줘"}' \
  "$BASE_URL/api/v1/labor-qa/ask" | python3 -m json.tool

echo ""
echo "== 3. 대화 세션 생성 =="
SESSION_ID=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/labor-qa/sessions" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "sessionId=$SESSION_ID"

echo ""
echo "== 4. 세션 안에서 질문 =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"연장근로 수당은 얼마인가요?"}' \
  "$BASE_URL/api/v1/labor-qa/sessions/$SESSION_ID/ask" | python3 -m json.tool

echo ""
echo "== 5. 후속 질문 (앞 질문 맥락을 이어받는지 확인) =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"그럼 5인 미만은요?"}' \
  "$BASE_URL/api/v1/labor-qa/sessions/$SESSION_ID/ask" | python3 -m json.tool

echo ""
echo "== 6. 대화 상세 (질문·답변 이력이 쌓였는지) =="
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/labor-qa/sessions/$SESSION_ID" | python3 -m json.tool

echo ""
echo "== 7. 적재 결과를 DB에서 직접 확인하려면 =="
echo "   PGPASSWORD=\$VECTOR_DB_PASSWORD psql -h 127.0.0.1 -U albam_app -d albam_vector \\"
echo "     -c \"SELECT count(*) AS 청크수, min(id) AS 샘플ID FROM vector_store;\""
