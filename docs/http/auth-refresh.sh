#!/bin/bash
# 리프레시 토큰 폐기 동작 확인
# 사용법: ./auth-refresh.sh {email} {password}
#
# 확인하려는 것: 로그아웃한 뒤에도 예전 리프레시 토큰으로 재발급이 되는가.
# 이 테이블이 생기기 전에는 됐다(쿠키만 지웠으므로). 이제는 거부돼야 한다.
BASE_URL="http://localhost:8080"
EMAIL="$1"
PASSWORD="$2"
COOKIE_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR"' EXIT

if [ -z "$EMAIL" ] || [ -z "$PASSWORD" ]; then
  echo "사용법: $0 {email} {password}"
  exit 1
fi

echo "== 1. 로그인 (리프레시 토큰이 쿠키로 내려온다) =="
curl -s -c "$COOKIE_JAR" -X POST -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" \
  "$BASE_URL/api/v1/auth/login" | python3 -m json.tool

echo ""
echo "== 2. 발급받은 리프레시 토큰을 따로 보관 =="
# 로그아웃 뒤에도 이 값이 통하는지 봐야 하므로 쿠키 파일을 복사해 둔다.
STOLEN_COOKIES=$(mktemp)
cp "$COOKIE_JAR" "$STOLEN_COOKIES"
echo "보관 완료"

echo ""
echo "== 3. 재발급 (정상 동작 확인) =="
curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST \
  "$BASE_URL/api/v1/auth/refresh" | python3 -m json.tool

echo ""
echo "== 4. 소비된 토큰을 다시 사용 -> 거부되고, 재사용 탐지로 세션이 전부 끊긴다 =="
curl -s -b "$STOLEN_COOKIES" -X POST \
  "$BASE_URL/api/v1/auth/refresh" | python3 -m json.tool
echo "   (서버 로그에 '이미 사용된 리프레시 토큰이 다시 들어와...' 경고가 남는다)"

echo ""
echo "== 5. 3번에서 받은 정상 토큰도 이제 무효다 (4번이 전부 끊었으므로) =="
curl -s -b "$COOKIE_JAR" -X POST \
  "$BASE_URL/api/v1/auth/refresh" | python3 -m json.tool

echo ""
echo "== 6. 로그아웃 (이미 세션이 없어도 성공해야 한다) =="
curl -s -b "$COOKIE_JAR" -X POST "$BASE_URL/api/v1/auth/logout" | python3 -m json.tool

rm -f "$STOLEN_COOKIES"
echo ""
echo "== 7. 저장된 토큰 확인 (재사용 탐지가 돌았다면 비어 있다) =="
echo "   mysql -u root -p al-bam -e \"SELECT id, user_id, LEFT(token_hash,16) AS hash, expires_at FROM refresh_tokens;\""
