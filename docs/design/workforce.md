# workforce 설계

2026-10-16 확정. 스케줄·근태·연차·급여를 옮기는 기준이다. 고용 조건(`employment`)은 store 단계에서 먼저 옮겼다.

## 다른 서비스에서 가져오는 것

| 필요한 것 | 쓰는 곳 | 가져올 곳 |
|---|---|---|
| 이름, 프로필 사진 | 스케줄·근태·급여 응답 | identity `UserDirectory` (일괄 조회) |
| 생년월일 (연소자 판정) | 스케줄 검증, AI 초안, 주간 대시보드 | identity `UserSummary.birthDate` |
| 5인 미만 여부, 휴게 정책, 영업시간 | 스케줄 검증, 근태, 급여 | store `StorePolicies` |
| 재직 상태, 역할, 입사일 | 권한 확인, 배정 가능 여부, 연차 산정 | store `StoreAccess` / `Memberships` |

## 결정

1. **모든 테이블에 `store_id`를 둔다 (비정규화).** 기존 쿼리는 `store_members`와 JOIN해 매장별로 걸렀는데, 서비스가
   나뉘면 불가능하다. 멤버의 소속 매장은 바뀌지 않으므로(다른 매장에 가면 새 memberId) 값이 어긋나지 않는다.
2. **애그리거트는 모두 따로**이고 멤버는 `memberId`로 참조한다: `Shift`, `ShiftTemplate`, `Attendance`,
   `LeaveUsage`, `Payroll`. workforce 안의 테이블끼리는 JOIN해도 된다.
3. **동시성 보장은 유지하고 잠그는 대상만 바꾼다.** 같은 멤버의 스케줄 쓰기는 `employment_terms` 행을 잠가 줄
   세운다(READ COMMITTED). 근무 중 출근 기록 1개, 월 급여 1개 제약은 그대로다.
4. **서비스마다 자기 `ChatClient`를 이름 붙여 갖고 `@Qualifier`로 주입한다.**
5. **쿼리 수 테스트는 `datasource-proxy`로 실제 SQL 실행 횟수를 센다.** Hibernate 통계가 없어졌다.
6. 지연 로딩 프록시가 없으므로 `ResponseDetachmentTest`는 옮기지 않는다.

## 이벤트

| 받는 이벤트 | 처리 |
|---|---|
| `MemberJoinedEvent` | 기본 고용 조건 생성 |
| `StorePurgedEvent` | 그 매장의 스케줄·템플릿·근태·연차·급여·고용 조건 삭제 |
| `UserWithdrawnEvent` | 처리하지 않음. 근무·급여 기록은 남기고, 이름은 identity가 이미 익명화한다 |

## 안쪽 구조

`employment`, `shift`, `attendance`, `leave`, `payroll`, `labor` 패키지. 한 서비스라 서로 직접 호출하되 의존 방향은
아래 한 방향이고 `WorkforceLayersTest`(ArchUnit)가 지킨다. shift와 leave는 서로 모른다. 응답의 이름·사진은
workforce가 `UserDirectory`로 직접 채운다.

```
payroll → attendance → shift · leave → employment → labor
```

## 옮기면서 알게 된 것

- **INSERT IGNORE는 교착을 만든다.** 잠글 고용 조건 행이 없을 때를 대비해 매번 `INSERT IGNORE` 후 `FOR UPDATE`를
  했더니, 동시 요청 8건에서 교착 상태가 났다. MySQL은 INSERT IGNORE가 중복 키를 만나면 그 행에 공유 락을 걸고,
  공유 락을 함께 쥔 트랜잭션들이 쓰기 락을 요청하며 서로를 기다린다. 이제 먼저 `FOR UPDATE`로 잠그고, 행이 없을
  때만 `INSERT ... ON DUPLICATE KEY UPDATE`(중복 시 배타 락)로 만든 뒤 다시 잠근다.
- **자식 컬렉션이 있는 애그리거트는 목록에서 N+1이 된다.** Spring Data JDBC는 `Store`의 영업시간·온보딩 단계,
  `MemberEmployment`의 근무 가능 요일을 루트마다 따로 조회한다(내 매장 목록: 매장 1개당 +2, 멤버 목록: 멤버 1명당
  +1). 실험 기능인 single query loading은 Map 컬렉션과 파생 쿼리를 지원하지 않아서, 목록은 `StoreListReader`·
  `EmploymentTermsReader`가 루트 한 번·자식 한 번으로 직접 읽는다. 쓰기는 계속 애그리거트로 한다.
  `QueryCountTest`가 데이터를 늘리기 전후의 쿼리 수가 같은지 본다.
- 다른 매장의 멤버에게 스케줄을 배정하면 이제 404 "멤버를 찾을 수 없습니다"다(기존 400 "해당 매장의 멤버가
  아닙니다"). 소속을 store에 묻는 `getMemberOfStore`가 매장이 다르면 찾지 못한 것으로 답하기 때문이다.

- **급여 기록은 upsert 한 문장으로 저장한다.** 예전의 "찾아서 없으면 새로 저장"은 같은 달 급여를 처음 조회하는
  요청이 겹치면 둘 다 새 행을 넣으려다 유니크 키 위반이 날 수 있었다. 다시 읽는 곳이 없어 엔티티 없이 SQL로 둔다
  (`PayrollRecords`, MySQL 8.0.19+의 `AS alias` 문법).
- 월간 대시보드는 연차 일수를 멤버마다 세던 것(N+1)을 매장 전체 한 번 조회로 바꿨다.
- 급여명세서의 매장 이름을 위해 store-api의 `StorePolicy`에 `name`을 더했다.

동시성 테스트(`WorkforceConcurrencyTest`)는 락을 빼면 8건이 모두 저장되고 넣으면 1건만 저장되는 것을 확인했다.

## 진행

1. ✅ 스케줄(템플릿·반복·AI 초안 포함) + 근태(리포트 포함) + 연차
2. ✅ 급여(명세서·예상 급여 포함) + 대시보드(월간·일간·주간)
