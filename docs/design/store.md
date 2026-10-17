# store 설계

2026-10-16 확정. 기존 백엔드에서 결합의 근본 원인이던 `StoreMember`를 나누는 것이 핵심이다.

## 문제

기존 `StoreMember`에는 성격이 다른 정보가 섞여 있었다.

- **소속**: 역할, 직함, 재직 상태, 입사일 — 권한 확인과 연차에 쓰인다
- **고용 조건**: 시급, 공제 방식, 주휴일, 근무 가능 요일 — **workforce(급여·스케줄)만** 읽는다

그래서 workforce가 시급 하나를 읽으려고 `StoreMember` 엔티티 전체를 끌어갔고, 서비스 경계 위반 326건 중
`StoreMember` 참조가 210건이었다.

## 결정

1. `StoreMember`를 나눈다. 소속은 store(`tw_store.store_members`), 고용 조건은 workforce
   (`tw_workforce.employment_terms`). 같은 `memberId`로 연결하고 서로 외래키는 없다.
2. 다른 서비스에는 엔티티 대신 레코드를 준다. 권한 확인은 `Membership`을 돌려준다.
3. 서비스를 넘는 외래키·CASCADE·JOIN·락을 없앤다. 이벤트와 ID 참조로 대신한다.
4. 프론트 API(URL·JSON)는 그대로 두고, 두 서비스에 걸친 멤버 API는 bff가 나눠 맡긴다.

## store-api

| 계약 | 내용 |
|---|---|
| `StoreAccess` | `requireMember` / `requireOwnerOrManager` / `requireOwner` → `Membership` |
| `Memberships` | 매장 소속 멤버 조회, 매장 멤버 목록 |
| `StorePolicies` | `StorePolicy` — 5인 미만 여부, 휴게 정책, 영업시간, 급여일 |
| 이벤트 | `MemberJoinedEvent`, `MemberLeftEvent`, `StorePurgedEvent` |

store는 identity의 `WithdrawalGuard`를 구현하고 `UserWithdrawnEvent`를 받는다.
의존 방향: workforce·operations·bff → store → identity. store는 workforce를 모른다.

## 멤버 API

| 엔드포인트 | 처리 |
|---|---|
| `GET /members/summary`, `DELETE /members/me`, `DELETE /members/{id}` | store |
| `GET /members` | bff가 조합 (store 소속 + workforce 고용 조건 + identity 이름·사진) |
| `PATCH /members/{id}` | bff가 요청을 나눠 전달 |
| `PATCH /members/me/available-days` | bff → workforce. 응답이 전체 멤버 정보라 조합이 필요하다 |

`PATCH /members/{id}`: ① 양쪽 검증만(store: 역할·상태 규칙, workforce: 최저임금·자기 시급 변경 금지)
→ ② store 적용 → ③ workforce 적용. **두 서비스를 한 트랜잭션으로 묶지 않는다.** 묶으면 MSA로 가는 순간 깨지는
전제를 숨기게 된다. 검증을 먼저 끝내 적용 단계의 실패 가능성을 없앤다. 규칙은 데이터를 가진 쪽에 있고 bff는
나누고 합치기만 한다.

## 경계를 넘던 결합의 대체

| 기존 | 새 설계 |
|---|---|
| 스케줄 저장 전 store의 `store_members` 행 잠금 | workforce의 `employment_terms` 행 잠금 |
| 매장 완전 삭제 시 DB CASCADE가 모든 서비스 데이터 삭제 | `StorePurgedEvent` → 각 서비스가 자기 데이터 삭제 |
| 삭제된 매장을 JOIN + `@SQLRestriction`으로 숨김 | 모든 요청이 `StoreAccess`로 먼저 확인 → 삭제된 매장은 404 |
| `getStoreMember().getUser().getName()` | `UserDirectory`로 일괄 조회 |
| 가입 승인 시 `StoreMember`에 시급 0 저장 | `MemberJoinedEvent` → workforce가 기본 고용 조건 생성. 생성 전 조회는 기본값 |

## 남는 한계

- 권한 확인과 `StorePolicies`는 요청마다 동기 호출이다. 서비스를 나눌 때 토큰에 소속 정보를 담거나 이벤트로
  복제하는 방식으로 바꾼다. 인터페이스가 분리돼 있어 구현만 바꾸면 된다.
