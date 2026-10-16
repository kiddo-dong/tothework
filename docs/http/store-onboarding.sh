#!/bin/bash
# 매장 생성 온보딩 흐름 (업종 프리셋 일괄 등록)
# 사용법: ./store-onboarding.sh {accessToken}
#
# 확인하려는 것: 매장 생성 1회 + 벌크 2회, 총 3회로 온보딩이 끝나는가.
# 예전에는 매장 생성 → 영업시간 PATCH → 체크리스트 10회 → 근무유형 3회로 13~14회였다.
BASE_URL="http://localhost:8080"
TOKEN="$1"

if [ -z "$TOKEN" ]; then
  echo "사용법: $0 {accessToken}"
  echo '  TOKEN=$(curl -s -X POST -H "Content-Type: application/json" \'
  echo '    -d "{\"email\":\"...\",\"password\":\"...\"}" \'
  echo '    http://localhost:8080/api/v1/auth/login | python3 -c "import sys,json;print(json.load(sys.stdin)[\"data\"][\"accessToken\"])")'
  exit 1
fi

echo "== 1. 매장 생성 (영업시간·휴게정책을 함께 보낸다 — 별도 PATCH 불필요) =="
STORE_ID=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
        "name": "테스트 카페",
        "address": "서울시 어딘가",
        "category": "CAFE",
        "smallBusiness": true,
        "breakPolicy": "STATUTORY",
        "payday": 10,
        "businessHours": {
          "MONDAY":    {"openTime": "08:00", "closeTime": "22:00", "closed": false},
          "TUESDAY":   {"openTime": "08:00", "closeTime": "22:00", "closed": false},
          "WEDNESDAY": {"openTime": "08:00", "closeTime": "22:00", "closed": false},
          "THURSDAY":  {"openTime": "08:00", "closeTime": "22:00", "closed": false},
          "FRIDAY":    {"openTime": "08:00", "closeTime": "23:00", "closed": false},
          "SATURDAY":  {"openTime": "10:00", "closeTime": "23:00", "closed": false},
          "SUNDAY":    {"openTime": "10:00", "closeTime": "20:00", "closed": true}
        }
      }' \
  "$BASE_URL/api/v1/stores" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "storeId=$STORE_ID"

echo ""
echo "== 2. 체크리스트 일괄 등록 (배열 순서가 곧 화면 순서) =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"items": [
        {"type": "OPEN",  "content": "포스기 시재 확인"},
        {"type": "OPEN",  "content": "에스프레소 머신 예열"},
        {"type": "OPEN",  "content": "원두 잔량 확인"},
        {"type": "OPEN",  "content": "매장 청소"},
        {"type": "OPEN",  "content": "오픈 간판 켜기"},
        {"type": "CLOSE", "content": "시재 정산"},
        {"type": "CLOSE", "content": "머신 청소"},
        {"type": "CLOSE", "content": "재고 확인"},
        {"type": "CLOSE", "content": "쓰레기 배출"},
        {"type": "CLOSE", "content": "전원·문단속"}
      ]}' \
  "$BASE_URL/api/v1/stores/$STORE_ID/checklist-items/bulk" | python3 -m json.tool

echo ""
echo "== 3. 근무 유형 일괄 등록 =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"items": [
        {"name": "오픈", "startTime": "08:00", "endTime": "15:00", "breakMinutes": 30},
        {"name": "미들", "startTime": "12:00", "endTime": "18:00", "breakMinutes": 30},
        {"name": "마감", "startTime": "15:00", "endTime": "22:00", "breakMinutes": 30}
      ]}' \
  "$BASE_URL/api/v1/stores/$STORE_ID/shift-templates/bulk" | python3 -m json.tool

echo ""
echo "== 4. 순서대로 저장됐는지 조회로 확인 =="
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/stores/$STORE_ID/checklist-items" | python3 -m json.tool

echo ""
echo "== 5. 온보딩 진행 상태 (처음엔 비어 있다) =="
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/stores/$STORE_ID/onboarding" | python3 -m json.tool

echo ""
echo "== 6. 사장님이 단계를 확인 처리 (같은 단계를 두 번 보내도 결과가 같다) =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/stores/$STORE_ID/onboarding/steps/checklist" > /dev/null
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/stores/$STORE_ID/onboarding/steps/checklist" | python3 -m json.tool

echo ""
echo "== 7. 온보딩 마침 =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/stores/$STORE_ID/onboarding/complete" | python3 -m json.tool

echo ""
echo "== 8. 같은 이름 근무 유형은 거부된다 (전부 롤백) =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"items": [
        {"name": "새벽", "startTime": "05:00", "endTime": "09:00", "breakMinutes": 0},
        {"name": "오픈", "startTime": "08:00", "endTime": "15:00", "breakMinutes": 30}
      ]}' \
  "$BASE_URL/api/v1/stores/$STORE_ID/shift-templates/bulk" | python3 -m json.tool
echo "   위가 409면, '새벽'도 등록되지 않았어야 한다:"
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/stores/$STORE_ID/shift-templates" | python3 -m json.tool
