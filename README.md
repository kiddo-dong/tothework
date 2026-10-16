# ToTheWork — Modular Monolith

기존 백엔드(`albam`)를 MSA로 갈 수 있는 모듈러 모놀리스로 다시 짓는 프로젝트.
배포 단위는 하나이고, 나중에 서비스로 떼어 낼 단위(서비스 후보)마다 코드·스키마·마이그레이션을 따로 둔다.

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

## 실행

```bash
./gradlew build          # 테스트 포함. DB 테스트는 Docker(Testcontainers)가 필요하다
./gradlew :app:bootRun   # DB_URL, DB_USERNAME, DB_PASSWORD 환경변수로 MySQL 접속
```
