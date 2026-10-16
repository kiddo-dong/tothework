# ToTheWork — Modular Monolith

기존 백엔드(`albam`)를 MSA로 갈 수 있는 모듈러 모놀리스로 다시 짓는 프로젝트.
배포 단위는 하나이고, 나중에 서비스로 떼어 낼 단위(서비스 후보)마다 코드·스키마·마이그레이션을 따로 둔다.

## 기존 구조와 무엇이 다른가

이 저장소의 브랜치는 리팩토링의 세 시점이다. 같은 기능, 같은 API(117개)를 세 가지 구조로 담고 있다.

| 브랜치 | 구조 | 한 줄 요약 |
|---|---|---|
| [`layered`](https://github.com/kiddo-dong/tothework/tree/layered) | 레이어드 | 출발점. 기능 패키지 안에 controller·service·repository·entity |
| [`modulith`](https://github.com/kiddo-dong/tothework/tree/modulith) | 모듈 경계를 건 레이어드 | 같은 코드에 Spring Modulith로 경계를 걸고 위반 962건 → 0건 |
| `main` | 모듈러 모놀리스 (이 문서) | 서비스 후보별로 코드·스키마·빌드를 나눠 다시 지음 |

| | `layered` | `main` |
|---|---|---|
| 빌드 | Maven 단일 모듈 | Gradle 멀티 프로젝트 — 서비스마다 `api`·`core` |
| 패키지 | `domain/<기능>/{controller,service,repository,entity,dto}` 16개 + 공용 `global` | `<서비스>/api`(계약) · `<서비스>/core`(구현) |
| 경계 | 없음. 어느 기능이든 다른 기능의 엔티티·저장소를 쓸 수 있다 | 다른 서비스의 `core` 의존은 빌드 설정 단계에서 실패 |
| 영속성 | JPA, 엔티티 사이 연관관계(`StoreMember → Store → …`) | Spring Data JDBC, 애그리거트 사이는 id 참조 |
| DB | 한 스키마(`al-bam`), 테이블 사이 외래키·CASCADE·JOIN | 서비스마다 스키마(`tw_*`), 서비스를 넘는 외래키·JOIN 없음 |
| 서비스 간 협력 | 다른 기능의 서비스·엔티티 직접 호출, 같은 트랜잭션 | `api`의 인터페이스·레코드, 비동기 이벤트(발행 기록 저장) |

모듈 경계를 처음 측정했을 때(`modulith` 브랜치의 첫 커밋) `layered`의 코드에는 경계 위반이 962건, 그중 순환이
27건 있었다. `modulith`에서 그 위반을 0으로 줄였지만, 그 위에서도 남는 결합이 있어 다시 짓기로 했다.

- **한 엔티티에 두 서비스의 데이터.** `StoreMember`에 소속(역할·상태)과 고용 조건(시급·공제·주휴일)이 함께 있었다.
  `main`에서는 소속은 `store`, 고용 조건은 `workforce`가 같은 memberId로 따로 갖는다.
- **다른 서비스의 행을 잠그는 동시성 제어.** 스케줄 쓰기를 줄 세우려고 store의 소속 행을 잠갔다. `main`에서는
  workforce가 가진 고용 조건 행을 잠근다.
- **서비스를 넘는 외래키·CASCADE·JOIN.** 예를 들어 매장 삭제가 CASCADE로 근태·급여까지 지웠다. `main`에서는 store가
  삭제 이벤트를 내고 각 서비스가 자기 데이터를 지운다.

다시 지으며 찾아 고친 문제(같은 1초에 발급한 토큰이 똑같아지는 버그, 동시 요청의 교착, 목록 API의 N+1 등)는 각
설계 문서에 있다. 기존 DB는 [migration/](migration/README.md)의 스크립트로 옮긴다.

## 서비스 후보

| 서비스 | 성격 | 스키마 |
|---|---|---|
| `identity` | 가입·인증·프로필 | `tw_identity` |
| `store` | 매장·소속·권한·가입 승인 | `tw_store` |
| `workforce` | 스케줄·근태·연차·급여·고용 조건 | `tw_workforce` |
| `operations` | 체크리스트·공지·인수인계·매뉴얼·거래처·메뉴 | `tw_operations` |
| `laborqa` | AI 근로 상담 | `tw_laborqa` (+ 벡터 DB) |
| `bff` | 화면용 조합 API. 서비스가 아니다 | 없음 |

## 구조

```
<service>/api    서비스 간 계약 — 레코드·이벤트·인터페이스만
<service>/core   구현 — Spring Data JDBC, 자기 스키마의 Flyway 마이그레이션
shared           공통 기반 (도메인 개념 없음)
bff              여러 서비스의 api를 조합
app              모두를 하나의 프로세스로 조립하는 실행 모듈
```

## 경계 규칙

| 규칙 | 강제하는 곳 |
|---|---|
| core는 다른 서비스의 core를 볼 수 없다. 다른 서비스는 api로만 쓴다 | Gradle (`tothework.boundary-rules`) — 빌드 설정 단계에서 실패 |
| api는 어떤 프로젝트에도 의존하지 않는다 | Gradle |
| 서비스 사이에 순환이 없다 | Spring Modulith (`ModularityTests`) |
| 서비스마다 스키마가 따로라 서로의 테이블에 외래키·JOIN이 없다 | 스키마 분리 (`ServiceSchema`) |

## 기술 스택

Java 25 · Spring Boot 4.1 · Spring Data JDBC · Spring Modulith 2.1 · MySQL 8 (서비스별 스키마) · Flyway ·
Gradle 9 (Kotlin DSL, convention plugins) · JUnit 5 · Testcontainers

영속성은 JPA 대신 **Spring Data JDBC**를 쓴다. 애그리거트 단위로 저장하고 애그리거트 사이는 ID로만 참조하며,
지연 로딩·변경 감지·프록시가 없어 객체 그래프를 타고 다른 서비스로 결합이 새어 나가지 않는다.
조회는 필요하면 SQL을 직접 쓴다.

## 서비스 간 이벤트

다른 서비스의 이벤트는 `@ApplicationModuleListener`로 받는다. 발행한 쪽 트랜잭션이 커밋된 뒤 **별도 트랜잭션에서
비동기로** 실행되므로, 같은 트랜잭션을 전제하지 않는다. 처리하지 못한 이벤트는 `tw_shared.EVENT_PUBLICATION`에
남아 재시작할 때 다시 전달된다 (Spring Modulith Event Publication Registry).

예: identity가 탈퇴를 커밋하면 `UserWithdrawnEvent`를 받은 laborqa가 그 사용자의 상담 이력을 지운다.

## 이식 현황

| 서비스 | 상태 |
|---|---|
| laborqa | ✅ 대화 세션·RAG 답변·지식베이스 적재·탈퇴 정리. API 경로와 응답은 기존과 같다 |
| identity | ✅ 가입·이메일 인증·로그인·소셜 로그인·토큰 재발급(회전)·비밀번호·내 정보·탈퇴·프로필 사진 |
| store | ✅ 매장·영업시간·온보딩·초대 코드·소유권 이전·삭제(유예 후 완전 삭제)·소속·가입 신청 |
| workforce | ✅ 고용 조건·스케줄(템플릿·반복·AI 초안)·근태(리포트)·연차·급여(명세서·예상 급여)·대시보드 |
| bff | ✅ 멤버 상세 조회·수정 조합, 홈 화면(내 하루 + 관리자 섹션) |
| operations | ✅ 체크리스트·공지·인수인계·매뉴얼(이미지)·거래처(요일별 발주량)·메뉴 원가 |

기존 백엔드의 API 117개(메서드·경로)가 모두 같은 자리에 있다. 기존 DB는 [migration/](migration/README.md)의
스크립트로 서비스별 스키마에 옮긴다(로컬 리허설 완료, 운영 데이터 리허설 전).

홈 화면은 bff가 권한을 한 번 확인한 뒤 각 서비스의 조회 계약(`Schedules`·`Attendances`·`Payrolls`·`Notices`·
`Checklists`·`HandoverNotes`·`Suppliers`·`JoinRequests`)을 부른다. 계약은 권한을 다시 확인하지 않는다. 트랜잭션으로
묶지 않으므로 서비스마다 조금 다른 시점의 값일 수 있다(화면용 조회라 괜찮다).

설계 문서: [store](docs/design/store.md), [workforce](docs/design/workforce.md), [operations](docs/design/operations.md)

## 인증

| 하는 일 | 위치 | 이유 |
|---|---|---|
| 토큰 형식·검증, 보안 필터, CORS, 401/403 응답 | `shared.security` | 서비스가 나뉘어도 각자 토큰을 검증할 수 있게 |
| 토큰 발급, 사용자 저장, 요청마다 주체 채우기(`AuthenticatedUserLoader`) | `identity` | 사용자 데이터를 가진 곳 |
| 로그인 없이 열 경로 | 각 서비스가 `PublicEndpoints` 빈으로 등록 | 공통 계층이 다른 서비스의 URL을 알 필요가 없다 |
| 다른 서비스에 사용자 정보 제공 | `identity-api`의 `UserDirectory` → `UserSummary` | 엔티티 대신 필요한 값만 |

토큰 형식(subject=userId, `email`·`type` 클레임)과 환경변수 이름은 기존과 같아, 전환해도 로그인한 사용자가 끊기지
않는다. 여기에 토큰마다 고유 `jti`를 더했다. 없으면 같은 1초 안에 발급한 두 토큰이 똑같아져 회전이 무력해지고
동시 로그인이 실패한다(기존 백엔드에도 있던 문제를 통합 테스트가 찾아냈다).

## 실행

```bash
./gradlew build          # 테스트 포함. DB 테스트는 Docker(Testcontainers)로 빈 MySQL을 띄운다
./gradlew :app:bootRun   # DB_URL, DB_USERNAME, DB_PASSWORD 환경변수로 MySQL 접속
```

Docker가 없으면 로컬 MySQL로 테스트할 수 있다. 테이블은 `tw_*` 스키마에만 생긴다.

```bash
SPRING_PROFILES_ACTIVE=localdb DB_PASSWORD=... ./gradlew build
```
