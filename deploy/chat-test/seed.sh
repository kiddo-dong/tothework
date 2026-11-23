#!/usr/bin/env bash
# 서버 2대 재현용 데이터: 사장·알바 계정 둘과 둘이 속한 매장 하나를 만든다.
#
#   deploy/chat-test/seed.sh
#
# 저장소 루트에서 실행한다. 필요: docker compose, curl, jq. 실행할 때마다 새 계정을 만든다.
#
# 재현 환경에는 메일 서버가 없어서, 가입 응답은 "메일 발송에 실패했습니다"(400)다. 계정은 그 전에 저장되므로
# 이메일 인증만 DB에서 직접 표시하고 넘어간다. 재현용 MySQL에만 하는 일이다.
set -euo pipefail

BASE=${BASE:-http://localhost:8088}
ENV_FILE=${ENV_FILE:-deploy/chat-test/.env}
COMPOSE=(docker compose -f docker-compose.chat-test.yml --env-file "$ENV_FILE")
PASSWORD='Test1234!'
RUN=$(date +%s)

# shellcheck disable=SC1090
source "$ENV_FILE"

sql() {
  "${COMPOSE[@]}" exec -T -e MYSQL_PWD="$CHAT_TEST_DB_PASSWORD" mysql mysql -uroot -N -e "$1"
}

# 가입 → 이메일 인증 표시 → 로그인. 액세스 토큰을 출력한다.
account() {
  local email=$1 name=$2 phone=$3
  curl -s -o /dev/null -X POST "$BASE/api/v1/auth/signup" -H 'Content-Type: application/json' -d "$(jq -n \
    --arg email "$email" --arg name "$name" --arg phone "$phone" --arg pw "$PASSWORD" \
    '{email:$email,password:$pw,passwordConfirm:$pw,name:$name,phone:$phone,birthDate:"1995-03-01",agreedToTerms:true}')"
  sql "UPDATE tw_identity.users SET email_verified = 1 WHERE email = '$email'"
  curl -sf -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "$(jq -n --arg email "$email" --arg pw "$PASSWORD" '{email:$email,password:$pw}')" | jq -r '.data.accessToken'
}

owner_email="owner-$RUN@chat-test.local"
staff_email="staff-$RUN@chat-test.local"
owner=$(account "$owner_email" "사장" "010-$((RUN % 9000 + 1000))-0001")
staff=$(account "$staff_email" "알바" "010-$((RUN % 9000 + 1000))-0002")

hours='{"openTime":"08:00","closeTime":"23:00","closed":false}'
store_id=$(curl -sf -X POST "$BASE/api/v1/stores" -H "Authorization: Bearer $owner" -H 'Content-Type: application/json' \
  -d "{\"name\":\"채팅 테스트 매장\",\"businessHours\":{\"MONDAY\":$hours,\"TUESDAY\":$hours,\"WEDNESDAY\":$hours,
       \"THURSDAY\":$hours,\"FRIDAY\":$hours,\"SATURDAY\":$hours,\"SUNDAY\":$hours}}" | jq -r '.data.id')

code=$(curl -sf "$BASE/api/v1/stores/$store_id/invite-code" -H "Authorization: Bearer $owner" | jq -r '.data.code')
request_id=$(curl -sf -X POST "$BASE/api/v1/join-requests" -H "Authorization: Bearer $staff" \
  -H 'Content-Type: application/json' -d "{\"code\":\"$code\"}" | jq -r '.data.id')
curl -sf -o /dev/null -X POST "$BASE/api/v1/stores/$store_id/join-requests/$request_id/approve" \
  -H "Authorization: Bearer $owner" -H 'Content-Type: application/json' -d '{"role":"STAFF"}'

cat <<EOF
준비 완료
  테스트 페이지: $BASE/chat-test.html
  storeId:      $store_id
  사장:         $owner_email / $PASSWORD
  알바:         $staff_email / $PASSWORD
EOF
