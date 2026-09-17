<div align="center">

# ToTheWork

**자영업자들을 위한, 간편한 매장 및 인력 관리 서비스**

<br>

![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?style=flat-square)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square)
![pgvector](https://img.shields.io/badge/pgvector-4169E1?style=flat-square)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0-6DB33F?style=flat-square)

</div>

---

## 기술 스택

```
Java 25  ·  Spring Boot 4.1  ·  JPA + Flyway
MySQL (업무 데이터)  ·  PostgreSQL + pgvector (임베딩)
Spring AI 2.0 + OpenAI  ·  AWS S3  ·  JWT
```

---

## 도메인

```
com.example.albam
├── domain/
│   ├── user          회원가입·소셜로그인·인증·프로필
│   ├── store         매장 설정·영업시간·초대코드·온보딩
│   ├── storemember   멤버·역할·직함·시급·근무가능요일
│   ├── invite        초대코드 참여 신청·승인
│   ├── shift         스케줄·근무유형 템플릿·AI 초안
│   ├── attendance    출퇴근·근태 기록·리포트
│   ├── payroll       급여 계산·임금명세서·대시보드
│   ├── leave         연차 사용 기록
│   ├── checklist     오픈·마감 체크리스트
│   ├── notice        공지·확인 현황
│   ├── handover      인수인계 노트
│   ├── manual        매장 매뉴얼
│   ├── supplier      거래처·발주 품목
│   ├── menu          재료·레시피·원가 계산
│   ├── laborqa       근로기준법 Q&A (RAG)
│   └── home          역할별 홈 화면 집계
└── global/
    ├── config        보안·JPA·AI·스케줄링
    ├── security      JWT 발급·검증·인가
    ├── exception     에러 코드·전역 핸들러
    ├── file          S3 업로드·이미지 검증
    ├── labor         근로기준법 상수·판정
    ├── ratelimit     AI 엔드포인트 호출 제한
    └── signup        가입 미완료 계정 차단
```

도메인마다 `controller` / `service` / `repository` / `entity` / `dto` 로 나뉜다.
레이어를 최상위에 두지 않고 **도메인을 먼저 나누는** 구조다.

---

## 시작하기

### 준비물

| | |
|---|---|
| **JDK 25** | `pom.xml`이 25를 타깃으로 한다. JDK 21이면 `-Djava.version=21`을 붙인다 |
| **MySQL 8** | 스키마는 Flyway가 만든다. 빈 DB만 준비하면 된다 |
| **PostgreSQL + pgvector** | AI 상담을 쓸 때만 필요하다 |

### 벡터 DB 준비 (AI 상담용)

```bash
sudo apt install -y postgresql postgresql-16-pgvector

sudo -u postgres psql -c "CREATE USER albam_app WITH PASSWORD '비밀번호';"
sudo -u postgres psql -c "CREATE DATABASE albam_vector OWNER albam_app;"
sudo -u postgres psql -d albam_vector -c "CREATE EXTENSION IF NOT EXISTS vector;"
sudo -u postgres psql -d albam_vector -c "GRANT ALL ON SCHEMA public TO albam_app;"
```

> **확인까지 해야 한다.** 계정이 실제로 붙는지 보지 않으면, 앱이 뜬 뒤 Q&A를 처음 누를 때 실패한다 — 벡터스토어는 지연 생성이라 기동 로그에는 아무 단서도 남지 않는다.
> ```bash
> PGPASSWORD='비밀번호' psql -h 127.0.0.1 -U albam_app -d albam_vector -c "SELECT extname FROM pg_extension WHERE extname='vector';"
> ```

### 환경변수

기본값이 있는 것은 로컬에서 생략해도 된다. 아래 넷은 없으면 기동하지 않는다.

```bash
MYSQL_PASSWORD=...
JWT_SECRET=...              # openssl rand -base64 48
AWS_S3_BUCKET_NAME=...
GMAIL_USERNAME=...  GMAIL_APP_PASSWORD=...
```

선택 — 없으면 해당 기능만 동작하지 않는다.

```bash
OPENAI_API_KEY=...          # AI 상담·스케줄 초안
VECTOR_DB_PASSWORD=...      # AI 상담
ADMIN_INGEST_TOKEN=...      # 없으면 지식베이스 적재 API 자체가 닫힌다
SWAGGER_ENABLED=true        # API 문서. 기본은 꺼져 있다
```

전체 목록은 [`deploy/systemd/albam.env.example`](deploy/systemd/albam.env.example)에 있다.

### 실행

```bash
./mvnw spring-boot:run
```

Flyway가 스키마를 만들고, `ddl-auto=validate`가 엔티티와 대조한다.
API 문서는 `SWAGGER_ENABLED=true`로 띄웠을 때 `/swagger-ui.html`에 나온다.

### 지식베이스 적재 (AI 상담을 쓸 때)

기동할 때 자동으로 하지 않는다. **처음 한 번, 그리고 문서를 고쳤을 때** 직접 부른다.

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "X-Admin-Token: $ADMIN_INGEST_TOKEN" \
  http://localhost:8080/api/v1/labor-qa/admin/ingest
```

적재하지 않으면 상담이 제도 설명만 하고 **최저임금 같은 구체적 숫자는 답하지 못한다.**

---

## 설계에서 고른 것들

<details>
<summary><b>스키마는 Flyway가, 검증은 Hibernate가</b></summary>

<br>

`ddl-auto=validate`라 엔티티와 실제 테이블이 어긋나면 **부팅이 실패한다.** 마이그레이션 파일을 빼먹은 채 배포하는 사고를 막기 위해 일부러 그렇게 뒀다. 엔티티를 고치면 `V{n}__*.sql`도 같이 만들어야 한다.

</details>

<details>
<summary><b>이미지는 URL이 아니라 S3 key로 저장</b></summary>

<br>

버킷·리전·CDN 도메인은 언제든 바뀌는 인프라 설정이다. 행마다 전체 URL을 복사해두면 옮길 때 저장된 링크가 전부 죽는다. 저장은 key로 하고, 공개 URL은 응답을 만들 때 조립한다.

키에 UUID가 들어가 같은 주소의 내용이 바뀌지 않으므로, 업로드 시 1년 `immutable` 캐시를 붙인다.

</details>

<details>
<summary><b>리프레시 토큰은 DB에 기록한다</b></summary>

<br>

순수 JWT면 서버가 토큰을 폐기할 수단이 없어, 로그아웃해도 유출된 토큰이 만료일까지 살아 있다. 기록이 있어야만 재발급되므로 **행을 지우는 것이 곧 폐기**다.

저장하는 값은 토큰이 아니라 SHA-256 해시다. DB가 유출돼도 그대로 쓸 수 있는 자격증명이 함께 넘어가지 않는다. 재발급 때마다 토큰이 교체되고, 이미 쓴 토큰이 다시 오면 유출로 보고 그 사용자의 세션을 전부 끊는다.

</details>

<details>
<summary><b>직함과 권한은 분리한다</b></summary>

<br>

`role`(OWNER/MANAGER/STAFF)이 무엇을 할 수 있는지 정하고, `title`("주방장", "홀팀장")은 화면에 보이는 이름일 뿐이다. 하나로 합치면 직함을 고치다 권한이 함께 바뀌어, 이름을 바꿨다가 급여 정보가 열린다.

</details>

<details>
<summary><b>AI 상담은 설명과 숫자를 다르게 다룬다</b></summary>

<br>

벡터 검색으로 근거를 찾되, 검색이 비어도 거절하지 않는다. 자료가 한정적이라 제도만 물어도 걸리지 않는 질문이 많은데, 그때마다 "자료가 없다"고만 하면 아는 것도 못 알려주는 셈이 된다.

대신 프롬프트가 선을 긋는다 — **제도 설명은 아는 범위에서, 금액·요율·일수 같은 숫자는 자료에 있는 것만.** 법은 해마다 바뀌고, 틀린 숫자로 급여를 계산하면 그 자체가 위반이다. 모른다고 말하는 쪽이 옛 숫자를 자신 있게 말하는 것보다 낫다.

</details>

<details>
<summary><b>인가는 매 메서드 첫 줄에서 명시적으로</b></summary>

<br>

`@PreAuthorize` 대신 `storeAuthorizationService.requireOwner(storeId, userId)` 형태로 서비스 진입부에서 호출한다. 어떤 권한이 필요한지 코드를 읽으면 바로 보이고, 매장 소속 여부까지 한 번에 확인된다.

</details>

---

## 배포

```
tothework.com      →  Vercel (프론트)
api.tothework.com  →  EC2  →  nginx  →  Spring Boot (127.0.0.1:8080)
                              └ PostgreSQL + pgvector (localhost)
                      RDS MySQL  ·  S3
```

nginx 설정과 systemd 유닛은 [`deploy/`](deploy/)에 있다.

**빌드는 로컬에서.** 작은 인스턴스에서 Maven을 돌리면 메모리가 모자란다.

```bash
./mvnw clean package -DskipTests
scp target/albam-0.0.1-SNAPSHOT.jar ubuntu@<host>:/tmp/albam.jar
# 서버에서: mv → chown → systemctl restart albam
```

> **`APP_FRONTEND_URL`을 빠뜨리면 조용히 깨진다.** 기본값이 `localhost:5173`이라 기동은 되지만, 비밀번호 재설정 메일이 사용자 자기 PC를 가리키는 링크를 담아 나간다. 재설정 폼은 그 링크로만 갈 수 있어 계정 복구가 막힌다.

> **`COOKIE_SECURE=true`** 가 아니면 HTTPS에서 리프레시 쿠키가 나가지 않아 로그인이 통째로 깨진다.

**운영 상태 확인** — Actuator는 8081 루프백 전용이라 nginx가 프록시하지 않는다.

```bash
curl localhost:8081/actuator/health    # 서버에 SSH로 들어가서
```

---

## 검증

```bash
./mvnw test
```

**테스트 161개.** 단위 테스트 외에 두 가지를 실제로 확인한다.

- **쿼리 수** — 목록 조회가 행 수에 비례해 쿼리를 늘리지 않는지 Hibernate 통계로 센다. N+1은 코드를 읽어서는 드러나지 않고 데이터 두어 건으로는 체감도 되지 않아, 5건을 넣고 쿼리 수가 따라 늘면 실패시킨다.
- **컨텍스트 기동** — 파생 쿼리 이름, 빈 배선, Flyway, `ddl-auto=validate`는 컴파일로는 잡히지 않는다. 실제로 띄워서 확인한다.

> 이 둘은 MySQL에 붙으므로 `MYSQL_PASSWORD`가 필요하다.

---

## 문서

| | |
|---|---|
| [`docs/backend-prd.md`](docs/backend-prd.md) | 도메인·API·비즈니스 규칙 레퍼런스 |
| [`docs/frontend-role-guide.md`](docs/frontend-role-guide.md) | 역할별 화면·API 매핑 |
| [`src/main/resources/http/`](src/main/resources/http/) | 흐름별 curl 스크립트 |

---

<div align="center">
<sub>코드 식별자는 이전 이름 <code>albam</code>을 유지한다 — 패키지, 서비스 유닛, DB 이름이 얽혀 있다.</sub>
</div>
