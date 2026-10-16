#!/usr/bin/env bash
# 기존 al-bam DB를 서비스별 스키마로 옮긴다: 전제 확인 → 이전(한 트랜잭션) → 행 수·합계 대조.
#
# 사용:
#   MYSQL_PWD=... migration/migrate.sh [--source al-bam] [--target-prefix tw_] [--host localhost] [--port 3306] [--user root]
#
# 비밀번호는 인자로 받지 않는다(프로세스 목록·셸 기록에 남는다). MYSQL_PWD 환경변수로만 넘긴다.
# 대상 스키마는 새 앱을 한 번 띄워 Flyway로 만들어 둔 빈 테이블이어야 하고, 이전하는 동안 앱은 멈춰 둔다.
# --target-prefix를 바꾸면 다른 이름의 스키마(예: 리허설용 twdry_)로 옮긴다. 그 스키마도 Flyway로 미리 만든다.
set -euo pipefail

SOURCE="al-bam"
PREFIX="tw_"
HOST="localhost"
PORT="3306"
USER_NAME="root"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --source) SOURCE="$2"; shift 2 ;;
    --target-prefix) PREFIX="$2"; shift 2 ;;
    --host) HOST="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --user) USER_NAME="$2"; shift 2 ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
done
if [[ -z "${MYSQL_PWD:-}" ]]; then
  echo "MYSQL_PWD 환경변수로 비밀번호를 넘겨 주세요." >&2
  exit 2
fi

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

render() {
  sed -e "s/\${source}/${SOURCE}/g" \
      -e "s/\${identity}/${PREFIX}identity/g" \
      -e "s/\${store}/${PREFIX}store/g" \
      -e "s/\${workforce}/${PREFIX}workforce/g" \
      -e "s/\${operations}/${PREFIX}operations/g" \
      -e "s/\${laborqa}/${PREFIX}laborqa/g" \
      "$DIR/$1"
}

db() {
  mysql --host="$HOST" --port="$PORT" --user="$USER_NAME" --batch --skip-column-names "$@"
}

echo "▶ 전제 확인 (원본 ${SOURCE} → 대상 ${PREFIX}*)"
read -r version target_rows < <(render precheck.sql | db)
if [[ "$version" == "NULL" || "$version" -lt 11 ]]; then
  echo "원본의 Flyway 버전이 ${version}입니다. 11 이상이어야 합니다." >&2
  exit 1
fi
if [[ "$target_rows" -ne 0 ]]; then
  echo "대상 스키마에 이미 ${target_rows}행이 있습니다. 빈 스키마에만 옮깁니다." >&2
  exit 1
fi
echo "  원본 버전 ${version}, 대상 비어 있음"

echo "▶ 이전"
render albam-to-tothework.sql | db

echo "▶ 대조"
mismatch=0
while IFS=$'\t' read -r name expected actual; do
  mark="ok"
  if [[ "$expected" != "$actual" ]]; then mark="다름"; mismatch=1; fi
  printf '  %-28s %10s → %-10s %s\n' "$name" "$expected" "$actual" "$mark"
done < <(render verify.sql | db)

if [[ "$mismatch" -ne 0 ]]; then
  echo "대조에 실패한 항목이 있습니다. 새 앱을 띄우기 전에 원인을 확인하세요." >&2
  exit 1
fi
echo "✔ 완료"
