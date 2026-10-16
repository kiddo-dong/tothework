# 기존 al-bam DB → 서비스별 스키마 이전

새 백엔드(tothework-modular)는 서비스마다 스키마가 따로다(`tw_identity`, `tw_store`, `tw_workforce`, `tw_operations`,
`tw_laborqa`, 공용 `tw_shared`). 기존 백엔드는 `al-bam` 한 스키마에 모두 있었다. 이 디렉터리는 그 사이를 한 번 옮기는
스크립트다.

| 파일 | 하는 일 |
|---|---|
| `migrate.sh` | 전제 확인 → 이전 → 대조를 차례로 실행한다. 운영자는 이것만 쓴다 |
| `precheck.sql` | 원본의 Flyway 버전(11 이상)과 대상이 비어 있는지 |
| `albam-to-tothework.sql` | 이전 본체. 한 트랜잭션 |
| `verify.sql` | 테이블별 행 수, 시급·급여·근무시간 합계를 원본과 대조 |
| `albam-schema.sql` | 기존 스키마 고정본(V12). `DataMigrationTest`가 원본을 만들 때 쓴다 |

## 무엇이 바뀌나

대부분은 그대로 복사되고 id도 유지된다(서비스 사이의 참조가 모두 기존 id다). 모양이 바뀌는 곳은 이것뿐이다.

- `store_members`의 시급·공제 방식·주휴일 → `tw_workforce.employment_terms`(같은 id = memberId). 근무 가능 요일 →
  `employment_available_days`(NULL 요일 행은 버린다). 퇴사자도 옮긴다.
- 스케줄·근태·연차·급여의 `store_member_id` → `member_id`, 그리고 소속에서 복사한 `store_id`.
- `checklist_completions`에 `store_id`, `notice_reads.store_member_id` → `member_id`.
- 매뉴얼 이미지 순번을 0부터 빈틈없이 다시 매긴다. 레시피 줄은 `line_order`(예전 id 순서).
- 다른 서비스를 가리키던 외래키는 대상에 없다.

옮기지 않는 것: 노무 지식베이스(pgvector). 별도 DB이고 새 앱도 같은 곳(`VECTOR_DB_*`)을 쓴다. 리프레시 토큰은 양쪽
모두 원문의 SHA-256이라 옮기면 로그인이 유지된다.

## 운영 순서

1. RDS 스냅샷을 뜬다. 되돌릴 방법은 이것뿐이다(원본은 읽기만 하지만, 새 앱이 쓰기 시작한 뒤에는 원본과 갈라진다).
2. 기존 앱을 멈춘다. 이후 원본에 쓰기가 없어야 한다.
3. 새 앱을 트래픽 없이 한 번 띄워 Flyway가 `tw_*` 스키마를 만들게 하고 멈춘다. DB 사용자에게 스키마 생성 권한이
   필요하다. `DB_URL`은 `.../tw_shared?createDatabaseIfNotExist=true...`로 바꾼다.
4. 이전한다.

   ```bash
   MYSQL_PWD=... migration/migrate.sh --source al-bam --host <rds-host> --user <user>
   ```

   대상에 한 행이라도 있으면 시작하지 않는다(두 번 옮기기 방지). 실패하면 트랜잭션이 되돌아가므로 원인을 고친 뒤
   대상 스키마를 비우고(3번부터) 다시 한다. 대조가 어긋나면 0이 아닌 코드로 끝난다.
5. 새 앱을 띄우고 트래픽을 돌린다. 프론트는 바꿀 것이 없다(API 117개가 같다).

## 리허설

로컬 원본으로 운영과 같은 `migrate.sh`를 `twrehearsal_*` 스키마에 돌린다. 원본은 읽기만 한다.

```bash
MIGRATION_REHEARSAL_SOURCE=al-bam SPRING_PROFILES_ACTIVE=localdb DB_PASSWORD=... \
  ./gradlew :app:test --tests '*DataMigrationTest.rehearsal*'
```

2026-10-16 로컬 al-bam(V12)으로 리허설해 30개 테이블과 합계 5개가 모두 일치했다. 운영 데이터로는 아직 하지 않았다
— 스냅샷을 복원한 별도 인스턴스에서 한 번 해 보고 걸린 시간을 재 두는 것을 권한다.
