# 채팅 배포 준비: ALB + EC2 2대 + Redis

EC2 1대에서 **ALB + EC2 2대(프라이빗 서브넷)** 로 옮길 때 해야 할 일을 정리한다. 이 문서는 작업 목록이다. AWS
리소스나 서버 설정은 아직 아무것도 바꾸지 않았다.

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

## 4. 서버 2대로 늘리기 전에 확인할 기존 동작 (채팅과 별개)

채팅을 만들다 발견한 것들이다. 서버가 2대가 되면 바로 영향을 받는다.

1. **이벤트 재발행**: `application.yml`의 `spring.modulith.events.republish-outstanding-events-on-restart: true`.
   설정 주석에도 "여러 대로 늘리면 끌 것"이라고 적혀 있다. 두 서버가 재시작할 때마다 같은 미완료 이벤트를 각자 다시
   보내 중복 처리될 수 있다. 끄고, 미완료 이벤트는 한 서버에서만 재전달하도록 바꾼다.
2. **예약 작업이 두 서버에서 모두 돈다**: `StorePurgeJob`(04:45), `TokenCleanupJobs`(04:00, 04:30). 삭제 작업이라
   대체로 결과는 같지만, 같은 시각에 두 번 돈다. ShedLock 같은 DB 락을 쓰거나, 설정값으로 한 서버에서만 켠다.
3. **호출 제한이 서버 메모리에 있다**: `RateLimitInterceptor`. 라운드로빈이라 한도가 사실상 서버 수만큼 느슨해진다.
   가입·로그인 대입 방지와 AI 비용 보호가 목적이라, 이번에 들어온 Redis로 카운터를 옮기는 것을 검토한다.
4. **벡터 DB(pgvector)가 EC2 안에 있다**: `albam.env.example` 기준으로 각 EC2의 `localhost:5432`다. 2대면 서버마다
   따로 설치하고 지식베이스를 따로 적재해야 하고, 둘의 내용이 어긋날 수 있다. RDS PostgreSQL로 옮기고 `VECTOR_DB_URL`만
   바꾸는 쪽을 검토한다.
5. **로그가 각 서버의 파일에 있다**: `/var/log/albam`. 장애를 볼 때 두 서버를 다 뒤져야 한다. CloudWatch Agent 등으로
   한곳에 모은다. 채팅 연결 로그의 `server=`(SERVER_ID)로 어느 서버인지 가른다.
6. **예약 작업의 스케줄러**: WebSocket 설정이 자체 스케줄러 빈을 만들어, Spring Boot의 기본 `taskScheduler`가 생기지
   않는다. 예약 작업은 Spring의 단일 스레드 기본 스케줄러에서 돈다. 예전 Boot 기본값도 스레드 1개라 실행 방식은 같지만,
   Boot의 스케줄러 설정(`spring.task.scheduling.*`, 종료 대기)은 더 이상 적용되지 않는다.

## 5. 배포 순서 (제안)

1. ElastiCache를 만들고 EC2에서 `PING`을 확인한다.
2. 지금 서버 1대에 Redis 변수와 `SERVER_ID`를 넣고 배포한다. 채팅이 Redis를 거쳐 동작하는지 먼저 1대에서 본다.
3. 4번 항목(특히 1·2)을 정리한다.
4. 두 번째 EC2를 같은 설정(`SERVER_ID`만 다르게)으로 띄우고, ALB 대상 그룹에 두 대를 등록한다.
5. 로컬 재현의 시나리오 2·4를 운영에서 한 번 해 본다. 탭 두 개가 서로 다른 서버에 붙었는지는 테스트 페이지 대신
   서버 로그의 `STOMP connected server=...`로 확인한다.
