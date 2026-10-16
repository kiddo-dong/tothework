# operations 설계

2026-10-16. 체크리스트·공지·인수인계·매뉴얼·거래처·메뉴 원가를 옮기는 기준이다. 여섯 모두 "매장 운영 지식"이라 한
서비스로 묶는다. 서로 참조하지 않으므로 안쪽 패키지끼리도 의존이 없다.

## 다른 서비스에서 가져오는 것

| 필요한 것 | 쓰는 곳 | 가져올 곳 |
|---|---|---|
| 권한 확인 (멤버 / 관리자) | 모든 API | store `StoreAccess` |
| 작성자·확인자 이름, 사진 | 공지·인수인계·매뉴얼·체크 기록 | store `Memberships`(memberId → userId) + identity `UserDirectory` |
| 재직 멤버 목록 | 공지 확인 현황 | store `Memberships` |

## 결정

1. **작성자는 memberId로 참조하고 이름은 응답을 만들 때 채운다.** 예전에는 `StoreMember → User`를 JOIN으로
   따라갔다. 목록은 memberId를 모아 store·identity에 한 번씩 묻는다(`MemberProfiles`).
2. **컬럼 이름은 기존 그대로 두고** `store_member_id`만 workforce와 맞춰 `member_id`로 바꾼다. 다른 서비스의 행을
   가리키는 외래키(stores, store_members)는 없앤다. operations 안의 외래키와 CASCADE는 둔다.
3. **API로 개별 수정되는 것은 각자 애그리거트다.** Spring Data JDBC는 애그리거트를 저장할 때 자식 행을 지우고 다시
   넣어 id가 바뀐다. 발주 품목은 `itemId`로 따로 수정되므로 거래처의 자식이 아니라 별도 애그리거트(`SupplierItem`,
   `supplierId` 참조)다. 반면 레시피 줄·매뉴얼 이미지·요일별 발주량은 늘 통째로 교체되므로 자식 컬렉션으로 둔다.
4. **자식 컬렉션이 있는 애그리거트의 목록은 직접 읽는다.** 루트마다 자식을 따로 조회하는 N+1을 피하려고 매뉴얼
   목록·거래처 목록·메뉴 목록·오늘 발주 품목은 루트 한 번·자식 한 번으로 읽는다(workforce에서 배운 것).
5. 레시피 줄은 순서가 있어 `line_order` 열을 더한다(예전엔 자동 증가 id 순서였다).
6. 다른 서비스(bff 홈)에 주는 것은 operations-api의 인터페이스 넷이다: `Checklists`, `Notices`, `HandoverNotes`,
   `Suppliers`. 권한 확인은 부르는 쪽이 먼저 한다(기존 `*Query`와 같다).

## 이벤트

| 받는 이벤트 | 처리 |
|---|---|
| `StorePurgedEvent` | 그 매장의 모든 운영 데이터 삭제 |
| `UserWithdrawnEvent` | 처리하지 않음. 작성 기록은 남기고 이름은 identity가 익명화한다 |

## 옮기면서 알게 된 것

- **재료 → 레시피 줄은 CASCADE가 아니라 막아 둔다.** 레시피 줄은 메뉴의 순서 있는 자식(`line_order`)이라, 재료를 지울 때
  외래키 CASCADE로 줄만 지우면 순서 번호에 구멍이 생긴다. 서비스가 그 재료를 쓰는 메뉴를 애그리거트로 다시 저장해
  줄을 뺀 뒤 재료를 지운다. 매장 삭제는 메뉴를 먼저 지운다.
- 체크리스트 구분은 MySQL ENUM 정의 순서(CLOSE, OPEN)로 정렬된다. 기존 스키마도 같아서 바뀐 것은 없다.
- 매장을 완전히 삭제해도 매뉴얼 이미지의 S3 파일은 남는다. 기존 백엔드도 같았다(별도 과제).
- 안 읽은 공지 수는 공지 id 목록을 가져와 세던 것을 같은 스키마 안의 LEFT JOIN 한 번으로 바꿨다.

`OperationsQueryCountTest`는 거래처 목록을 일부러 품목마다 조회하게 바꾸면 실패하는 것(9 → 12)을 확인했다.
`OperationsPackagesTest`가 여섯 기능 사이의 참조를 막는다.
