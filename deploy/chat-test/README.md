# 매장 채팅: 서버 2대 로컬 재현

운영 구성(ALB + EC2 2대)을 로컬에서 흉내 낸다. ALB 자리에는 로컬 전용 로드밸런서(lb, nginx 이미지)를 둔다. 운영에는
nginx가 없다. 서로 다른 서버에 붙은 두 사람이 채팅을 주고받으려면 Redis Pub/Sub이
왜 필요한지, 서버 하나가 내려갔다 올라올 때 어떻게 복구되는지를 직접 확인하는 용도다. 운영 배포용이 아니다.

```
브라우저 ──> lb :8088 ──────┬──> app1 (SERVER_ID=app1) ──┐
                            └──> app2 (SERVER_ID=app2) ──┼──> MySQL (공유)
                                                         └──> Redis (chat:stores:* Pub/Sub)
```

| 파일 | 역할 |
|---|---|
| `docker-compose.chat-test.yml` (저장소 루트) | app1·app2·MySQL·Redis·lb |
| `deploy/chat-test/local-alb.nginx.conf` | lb 설정. 두 앱 라운드로빈, `/ws` 웹소켓 업그레이드 |
| `deploy/chat-test/seed.sh` | 사장·알바 계정과 둘이 속한 매장 생성 |
| `deploy/chat-test/.env.example` | 재현용 DB 비밀번호·JWT 키 (복사해서 채움) |

두 앱은 반드시 같은 MySQL을 쓴다. 서버마다 DB가 따로면(인메모리 DB 등) 메시지가 서버별로 쌓여 재현이 성립하지 않는다.

## 준비

필요: Docker, Docker Compose v2, curl, jq.

```bash
./gradlew :app:bootJar                                   # app/build/libs/app-0.0.1-SNAPSHOT.jar
cp deploy/chat-test/.env.example deploy/chat-test/.env
# .env에 CHAT_TEST_DB_PASSWORD(openssl rand -hex 16), CHAT_TEST_JWT_SECRET(openssl rand -base64 48)를 채운다

alias chat-compose='docker compose -f docker-compose.chat-test.yml --env-file deploy/chat-test/.env'
```

재현 환경에는 메일 서버가 없다. `seed.sh`는 가입 후 이메일 인증을 DB에서 직접 표시하고 넘어간다.

## 어느 서버에 붙었는지 보는 법

- 테스트 페이지(`http://localhost:8088/chat-test.html`) 상태 표시: `연결됨 (서버: app1)`
- STOMP로 `/app/server-info`를 구독하면 `{"serverId":"app1"}`이 한 번 온다
- 앱 로그: `STOMP connected server=app1 session=... user=...`
- REST 응답 헤더 `X-Upstream`: 응답한 앱의 주소 (lb가 붙인다)

lb는 연결이 열릴 때 라운드로빈으로 서버를 고르므로, 탭을 새로 열면 번갈아 붙는다. 같은 서버에 붙었으면 탭을 닫고
다시 연다.

## 검증 시나리오

### 1. Redis 없이(로컬 전송) 서로 다른 서버에 붙으면 메시지가 안 간다

```bash
CHAT_RELAY=local chat-compose up -d
chat-compose logs -f app1 app2 | grep "Started TotheworkApplication"   # 둘 다 뜰 때까지
deploy/chat-test/seed.sh                                              # storeId와 두 계정이 출력된다
```

탭 두 개로 `chat-test.html`을 열고 각각 사장·알바로 로그인해 같은 storeId로 입장한다. 상태 표시가 `app1`과 `app2`로
갈리게 한다.

- 기대 결과: 보낸 사람 화면에는 메시지가 보이고, **다른 서버에 붙은 쪽에는 오지 않는다.** 그 탭을 새로고침하면(이전
  메시지 조회) 보인다. 메시지는 DB에 저장됐지만 실시간 전달이 그 서버 안에서 끝났기 때문이다.
- 확인한 결과: `owner -> app1, staff -> app2` / 보낸 쪽(app1) 1건 수신, 다른 쪽(app2) 0건.

### 2. Redis를 켜면 주고받는다

```bash
chat-compose up -d app1 app2          # CHAT_RELAY 기본값 redis로 앱만 다시 만든다. DB는 그대로다
```

두 탭에서 다시 입장한다. 재접속 시 구독은 자동으로 다시 걸린다.

- 기대 결과: 어느 쪽에서 보내도 양쪽에 **한 번씩** 보인다.
- 확인한 결과: `owner -> app2, staff -> app1` / 보낸 쪽 1건, 다른 쪽 1건.

### 3. redis-cli로 흐름 보기

```bash
chat-compose exec redis redis-cli PUBSUB NUMPAT
chat-compose exec redis redis-cli CLIENT LIST | grep 'psub=1'
chat-compose exec redis redis-cli MONITOR      # 다른 창에서 메시지를 보내 본다. Ctrl+C로 끝낸다
```

- `PUBSUB NUMPAT`은 **서로 다른 패턴의 개수**다. 두 서버가 같은 패턴(`chat:stores:*`)을 구독하므로 2가 아니라 `1`이
  나온다. 서버별 구독은 `CLIENT LIST`에서 `psub=1`인 연결로 본다. 서버당 하나씩 2개가 보여야 한다.
- `MONITOR`에는 메시지 하나당 `"PUBLISH" "chat:stores:{storeId}" "{\"type\":\"CREATED\",...}"`이 **한 번** 찍힌다.
  발행한 서버도 자기 구독자에게 직접 보내지 않고 Redis를 거쳐 받는다. 그래서 두 번 찍히거나 두 번 받지 않는다.

### 4. 서버 하나가 내려갔다 올라올 때

```bash
chat-compose stop app2
```

- app2에 붙어 있던 탭은 연결이 끊기고(상태 `재접속 중...`), 3초마다 재접속을 시도해 app1에 붙는다. 다시 붙으면 구독을
  다시 걸고 최신 메시지를 조회하므로, 끊긴 동안 다른 사람이 보낸 메시지도 채워진다.
- 이 상태에서 보낸 메시지는 정상 저장·방송된다.

```bash
chat-compose start app2
```

- 다시 올라온 app2도 시작할 때 패턴을 구독한다(`CLIENT LIST`의 `psub=1`이 다시 2개). 새로 여는 탭이 app2에 붙어도
  주고받을 수 있다.
- 확인한 결과: app2가 내려간 동안 새 연결 4개가 모두 app1에 붙었고, 그때 보낸 메시지가 app2 재시작 후 이전 메시지
  조회 맨 위에 있었다. 재시작한 app2와 app1 사이의 전달도 정상이었다.

> 내려간 컨테이너의 주소로 가는 연결은 거절되지 않고 매달린다. lb의 기본 연결 타임아웃(60초) 동안 그 요청이
> 기다리므로, `proxy_connect_timeout 2s`로 바로 다른 앱으로 넘긴다. 운영의 ALB는 헬스체크(`/health`)로 내려간
> 서버를 빼므로 같은 문제가 없다.

## 정리

```bash
chat-compose down        # 컨테이너와 데이터(MySQL은 볼륨 없이 띄운다)를 모두 지운다
```
