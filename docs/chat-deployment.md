# 채팅 배포 준비: ALB + EC2 2대 + Redis

운영은 **ALB + EC2 2대(프라이빗 서브넷)** 다. 채팅(Redis)을 배포할 때와, 서버 2대에서 문제가 되던 기존 동작을 고친
버전을 배포할 때 해야 할 일을 정리한다. 이 문서는 작업 목록이다. AWS 리소스나 서버 설정은 이 문서가 바꾸지 않는다.

- 채팅 구조: [`chat-architecture.md`](chat-architecture.md)
- 같은 구성의 로컬 재현: [`deploy/chat-test/README.md`](../deploy/chat-test/README.md)

```
인터넷 ─HTTPS─> ALB (퍼블릭 서브넷, ACM 인증서)
                 ├─> EC2 #1 :8080 ─┐
                 └─> EC2 #2 :8080 ─┼─> RDS MySQL (공유)
                                   └─> ElastiCache Redis/Valkey :6379 (chat:stores:* Pub/Sub)
```

## 1. ALB

| 항목 | 값 | 이유 |
|---|---|---|
| WebSocket | 설정 불필요 | ALB는 HTTP/HTTPS 리스너에서 WebSocket 업그레이드를 기본으로 지원한다 |
| 스티키 세션 | **끔** | SockJS를 쓰지 않는다. WebSocket은 연결이 열릴 때 고른 대상에 끝까지 붙어 있고, 서버 간 전달은 Redis가 한다 |
| 유휴 타임아웃 | **120초로 올림** (기본 60초) | STOMP 하트비트가 10초라 채팅에는 60초도 충분하다. 다만 AI 엔드포인트가 최악의 경우 30초 × 2회 = 60초 걸려서 기본값과 겹친다. `OperationalSettingsTest`가 이 120초를 기준으로 AI 타임아웃을 검사한다 |
| 헬스체크 | `GET /health` (트래픽 포트), 성공 코드 200 | 앱 상태와 DB를 본다. DB가 죽었거나 종료 중이면 503이다. Redis는 보지 않는다(Redis가 죽어도 채팅 외 기능은 정상이라 서버를 빼면 안 된다) |
| 등록 해제 지연 | 30초 정도로 줄임 (기본 300초) | 배포할 때 열린 WebSocket이 끊길 때까지 기다리는 시간이다. 클라이언트가 재접속하고 조회로 채우므로 길게 기다릴 필요가 없다 |
| 리스너 | 443(HTTPS, ACM) → 대상 그룹 HTTP:8080. 80은 443으로 리디렉션 | TLS는 ALB에서 끝낸다 |

### ALB 뒤의 앱 설정

ALB가 EC2의 앱(8080)으로 바로 보낸다. 사이에 다른 프록시는 없다.

- `SERVER_ADDRESS=0.0.0.0`. 기본값 `127.0.0.1`이면 ALB가 닿지 못한다.
- EC2 보안 그룹은 8080 인바운드를 **ALB 보안 그룹에서만** 허용한다. 관리 포트 8081은 그대로 루프백 전용이다.
- `FORWARD_HEADERS_STRATEGY=NATIVE`(기본값)는 Tomcat이 사설 대역(10/8, 172.16/12, 192.168/16)에서 온
  `X-Forwarded-*`를 믿는 방식이다. ALB 노드는 VPC 안의 사설 IP라 그대로 맞는다. 클라이언트 IP와 HTTPS 여부가 바르게
  잡혀야 호출 제한과 Secure 쿠키가 동작한다.
- 업로드 한도는 Spring(10MB)이 그대로 지킨다. ALB에는 본문 크기 제한이 없다.

## 2. Redis (ElastiCache)

| 항목 | 값 | 이유 |
|---|---|---|
| 엔진 | Valkey 또는 Redis OSS, **노드 기반, 클러스터 모드 끔** | 앱은 단일 엔드포인트(standalone)로 붙는다. Pub/Sub만 쓰고 저장은 안 한다 |
| 크기 | `cache.t4g.micro` 1노드부터 | 메시지가 지나가기만 한다. 장애 조치가 필요하면 복제본 1개를 둔다(그 순간의 메시지는 놓칠 수 있고, 조회로 복구된다) |
| 서브넷 | 프라이빗 서브넷 그룹 | 인터넷에서 닿지 않게 |
| 보안 그룹 | 6379 인바운드를 **EC2 보안 그룹에서만** 허용 | |
| 전송 중 암호화 | 켬 → `REDIS_SSL=true` | |
| AUTH 토큰 | 켬 → `REDIS_PASSWORD` | `/etc/albam/albam.env`(권한 600)에만 둔다 |
| 백업·지속성 | 불필요 | 데이터가 남지 않는다. Redis가 비어도 잃는 것이 없다 |

연결 확인 (EC2에서):
```bash
redis-cli -h <primary-endpoint> -p 6379 --tls -a "$REDIS_PASSWORD" PING
redis-cli ... CLIENT LIST | grep 'psub=1'     # 앱 기동 후 서버당 1개씩, 2개가 보여야 한다
```

## 3. 환경 변수 (서버마다 `/etc/albam/albam.env`)

새로 생긴 것만 적는다. 나머지는 [`deploy/systemd/albam.env.example`](../deploy/systemd/albam.env.example)에 있다.

| 변수 | 운영 값 | 설명 |
|---|---|---|
| `REDIS_HOST` | ElastiCache primary endpoint | 기본 `localhost` |
| `REDIS_PORT` | `6379` | 기본 `6379` |
| `REDIS_PASSWORD` | AUTH 토큰 | 기본 없음 |
| `REDIS_SSL` | `true` | 기본 `false`. 전송 중 암호화를 켰으면 반드시 `true` |
| `SERVER_ID` | 서버마다 다르게 (`ec2-a`, 인스턴스 ID 등) | **꼭 지정한다.** 기본값은 `$HOSTNAME`인데 systemd 서비스에는 그 변수가 없어서, 지정하지 않으면 두 서버 모두 `local`로 찍힌다 |
| `CHAT_ALLOWED_ORIGINS` | 프론트 주소 (콤마로 여러 개) | 기본은 `CORS_ALLOWED_ORIGINS`와 같다. 따로 둘 때만 지정 |
| `SERVER_ADDRESS` | `0.0.0.0` | ALB가 닿게 모든 인터페이스에 바인딩. 보안 그룹으로 ALB에서만 허용 |
| `CHAT_RELAY` | 지정하지 않음(= `redis`) | `local`은 서버 1대 전용 비교 모드다. 운영에서 쓰면 서버 간 메시지가 끊긴다 |
| `CHAT_TEST_PAGE_ENABLED` | 지정하지 않음(= `false`) | 수동 테스트 페이지. 운영에서는 열지 않는다 |

## 4. 서버 2대에서 문제가 되는 기존 동작 (채팅과 별개)

운영은 이미 ALB 뒤 EC2 2대다. 서버가 한 대라고 가정하고 만든 부분이 있었다.

### 코드로 고친 것

| 문제 | 고친 방법 |
|---|---|
| **재시작 때 이벤트 재발행**: 재시작한 서버가 다른 서버가 처리 중인 이벤트까지 다시 보내 같은 정리가 두 번 돌았다 | `republish-outstanding-events-on-restart: false`. 대신 Modulith의 staleness가 오래 멈춘 이벤트를 `FAILED`로 바꾸고(`published` 10분, `processing` 30분), `FailedEventResubmission`이 5분마다 **한 서버에서만** 다시 보낸다. 5번 실패하면 멈추고 경고 로그를 남긴다 |
| **예약 작업이 두 서버에서 모두 돈다**: 매장 완전 삭제(04:45), 토큰·미인증 계정 정리(04:00, 04:30). 매장 삭제는 두 번 돌면 삭제 이벤트가 두 번 나갔다 | ShedLock. 작업마다 공유 MySQL의 `tw_shared.shedlock` 행을 먼저 잡은 한 대만 실행한다. 락 시각은 DB 시계를 쓴다 |
| **호출 제한이 서버 메모리에 있다**: 라운드로빈이라 가입·로그인·AI 한도가 사실상 2배였다 | Redis(`ratelimit:*` 키)에서 두 서버가 함께 센다. Redis에 닿지 못하면 30초 동안 서버 메모리로 센다(예전과 같은 동작) |

새 테이블 `tw_shared.shedlock`은 Flyway(`shared` V2)가 앱 시작 때 만든다. 호출 제한도 채팅과 같은 Redis를 쓰므로 Redis
환경 변수(3절)가 두 서버 모두에 있어야 한다.

### 인프라로 옮긴 것

**벡터 DB(pgvector)는 RDS for PostgreSQL로 옮겼다.** 예전에는 각 EC2의 `localhost:5432`라 서버마다 따로 적재해야
했고, 한쪽만 적재하면 같은 질문이 서버에 따라 다르게 답했다. 이제 두 서버가 같은 DB를 보므로 지식베이스는 한 번만
적재한다(README "지식베이스 적재"). 두 서버의 `VECTOR_DB_URL`이 같은 RDS 엔드포인트를 가리키는지만 확인한다.

**로그가 각 서버의 파일에 있다.** `/var/log/albam`. CloudWatch Agent로 한곳에 모으고 있다면, 채팅 연결 로그의
`server=`(SERVER_ID)로 어느 서버인지 가른다.

### 참고: 예약 작업의 스케줄러

WebSocket 설정이 자체 스케줄러 빈을 만들어 Spring Boot의 기본 `taskScheduler`가 생기지 않는다. 예약 작업은 Spring의
단일 스레드 기본 스케줄러에서 돈다. 예전 Boot 기본값도 스레드 1개라 실행 방식은 같지만, Boot의 스케줄러 설정
(`spring.task.scheduling.*`, 종료 대기)은 적용되지 않는다.

## 5. 배포 순서

서버 2대가 이미 ALB 뒤에 있으므로, 한 대씩 바꾼다. 나머지 한 대가 트래픽을 받는 동안 배포하므로 중단이 없다.

1. ElastiCache를 만들고 두 EC2에서 `PING`을 확인한다.
2. 두 서버의 `/etc/albam/albam.env`에 Redis 변수와 서로 다른 `SERVER_ID`를 넣는다.
3. 한 대에 새 jar를 배포하고 재시작한다. ALB에서 healthy가 되고 `localhost:8081/actuator/health`에 `redis: UP`이 보이면
   다음 대로 넘어간다. 먼저 뜬 서버가 Flyway로 `tw_chat`과 `tw_shared.shedlock`을 만들고, 뒤의 서버는 건너뛴다.
4. 두 대 모두 끝나면 Redis에서 `CLIENT LIST | grep psub=1`이 2개인지 본다(채팅 구독이 서버당 하나).
5. 다음 날 새벽 4시 이후 `tw_shared.shedlock`의 `locked_by`로 예약 작업이 한 서버에서만 돌았는지 본다.

배포 중 두 서버의 버전이 잠깐 다를 수 있다. 이전 버전 서버는 재시작 때 이벤트를 다시 보내고 락 없이 예약 작업을
돌리지만, 배포가 새벽 4시를 피하면 겹칠 일이 거의 없다.
