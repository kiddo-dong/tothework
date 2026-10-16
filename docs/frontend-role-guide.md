# 알밤(Al-Bam) 프론트엔드 화면 가이드 — 역할별 페이지 명세

이 문서는 백엔드 API를 기반으로 **어떤 화면이 있어야 하고, 각 화면을 누가(어떤 역할이) 볼 수 있는지**를 정리한 것입니다. 프론트엔드 개발자에게 그대로 전달하면, "권한별로 알아서 짜주세요" 대신 구체적인 체크리스트로 작업할 수 있습니다.

---

## 0. 먼저 알아야 할 것 — 역할과 인증 모델

### 역할(Role)은 "매장마다" 다르다
한 사용자가 A매장에서는 사장님(OWNER)이고 B매장에서는 알바생(STAFF)일 수 있습니다. 역할은 **유저에 고정된 값이 아니라, 매장(Store)마다 따로 갖는 값**입니다.

| 역할 | 의미 |
|---|---|
| `STAFF` | 알바생 — 본인 것 조회 위주 |
| `MANAGER` | 매니저 — 운영·인사·급여·발주 관리 가능 |
| `OWNER` | 사장님 — 매장 자체의 생사(수정·삭제·양도)까지 가능 |

**권한은 상위 포함 관계입니다.** `OWNER ⊇ MANAGER ⊇ STAFF` — OWNER가 할 수 있는 건 MANAGER도 대부분 할 수 있고, MANAGER가 할 수 있는 건 STAFF는 못 합니다.

### 로그인 흐름
1. `POST /api/v1/auth/login` (또는 `/auth/oauth/{google|kakao|naver}`) → 본문으로 `accessToken`, `profileCompleted` 받음. **리프레시 토큰은 본문에 없고 HttpOnly 쿠키(`refreshToken`)로 자동 저장됨** — JS로 읽을 수 없고 저장할 필요도 없음
2. `profileCompleted`가 `false`면 → **추가정보 입력 화면**으로 보내기 (전화번호/생년월일/약관동의 누락 상태, 소셜 가입 직후 흔함)
3. 이후 모든 API 요청에 `Authorization: Bearer {accessToken}` 헤더 필수
4. 액세스 토큰 만료(401) 시 → `POST /api/v1/auth/refresh`를 **본문 없이** 호출 (쿠키가 자동 첨부됨) → 새 `accessToken` 수령. 이때 쿠키도 새 리프레시 토큰으로 자동 교체됨
5. 로그아웃은 `POST /api/v1/auth/logout` 호출 (서버가 쿠키 만료) + 클라이언트가 들고 있던 `accessToken` 폐기

> ⚠️ **auth 요청에는 반드시 credentials 포함**: fetch는 `credentials: 'include'`, axios는 `withCredentials: true`를 설정해야 쿠키가 오갑니다. 백엔드 CORS는 `http://localhost:5173` 오리진을 허용하도록 설정되어 있습니다 (배포 시 도메인 추가 필요).

### "지금 이 매장에서 내 역할이 뭔지" 알아내는 법
- `GET /api/v1/stores` (내가 속한 매장 목록) 응답의 각 항목에 `myRole` 필드가 있습니다. 매장을 선택해 들어갈 때 이 값으로 화면을 분기하세요.
- 매장에 들어간 뒤 `GET /api/v1/stores/{storeId}/home` 응답에도 `myRole`이 포함되어 있어 재확인 가능합니다.

### 화면 분기의 기본 원칙
**프론트는 `myRole` 값으로 메뉴/버튼을 숨기거나 비활성화하면 되고, 실제 차단은 백엔드가 합니다.** STAFF가 관리자 전용 API를 직접 호출해도 서버가 403을 돌려주니, 프론트는 보안이 아니라 사용성을 위해 화면을 나눈다고 생각하면 됩니다.

### 응답 형식 (공통)
```json
{ "success": true, "data": { ... }, "message": null }
{ "success": false, "data": null, "message": "에러 설명" }
```
실패 시 HTTP 상태코드(400/401/403/404/409/413/500)와 `message`를 그대로 사용자에게 보여줘도 됩니다. `401`=로그인 필요, `403`=권한 부족.

---

## 1. 화면 인벤토리 (역할별)

### 🟢 모든 역할 공통

| 화면 | 설명 | 주요 API |
|---|---|---|
| 로그인 / 회원가입 | 로컬+소셜 로그인, 이메일 인증 안내 | `/auth/*` |
| 추가정보 입력 | `profileCompleted=false`일 때만 진입 | `POST /users/me/complete-profile` |
| 매장 선택 | 내가 속한 매장 목록, 역할 뱃지 표시 | `GET /stores` |
| **홈 (첫 화면)** | 오늘 내 스케줄, 출근 상태, 예상 월급, 안읽은 공지, 체크리스트 진행률, 최근 인수인계 | `GET /stores/{id}/home` |
| 내 프로필 | 조회/수정, 프로필 사진 | `GET/PATCH /users/me`, `/users/me/profile-image` |
| 내 스케줄 | 내 근무 일정 조회 | `GET /shifts?storeMemberId=나` |
| 출퇴근 | 출근/퇴근 버튼 | `POST /attendance/clock-in`, `/clock-out` |
| 내 급여 | 이번 달 예상 월급, 급여명세서 | `GET /payroll/me/estimate`, `/payroll/payslip?memberId=나` |
| 내 연차 | 발생/사용/잔여 조회 | `GET /members/{나}/leaves` |
| 근무가능요일 설정 | 본인이 직접 수정 | `PATCH /members/me/available-days` |
| 매뉴얼 | 카테고리별 열람 (레시피, 기기사용법 등) | `GET /manuals` |
| 공지사항 | 목록 + 확인 버튼 | `GET /notices`, `POST /notices/{id}/read` |
| 오픈/마감 체크리스트 | 오늘 항목 체크 | `GET /checklist`, `POST /checklist-items/{id}/check` |
| 인수인계 노트 | 작성 + 열람 | `POST/GET /handover-notes` |
| 매장 나가기 | 본인 탈퇴 | `DELETE /members/me` |
| 가입 신청 (신규 매장) | 초대코드로 신청, 내 신청 조회/취소 | `POST /join-requests`, `GET/DELETE /join-requests/me` |
| 근로기준법/세무 Q&A 챗봇 | 매장과 무관한 전역 기능, RAG 기반 답변 + 출처 표시. **대화형(멀티턴) 지원** — 세션을 만들면 "그럼 5인 미만은요?" 같은 후속 질문이 맥락 유지됨. 단발 질문은 `/ask` | 세션: `POST/GET /labor-qa/sessions`, `GET/DELETE /labor-qa/sessions/{id}`, 질문: `POST /labor-qa/sessions/{id}/ask` (단발: `POST /labor-qa/ask`) |

> 💡 매뉴얼·공지·체크리스트·인수인계는 "보기"는 전 역할 공통이고, "작성/관리"만 관리자(OWNER/MANAGER) 전용입니다. 같은 화면에서 관리자에게만 편집 버튼을 노출하는 방식을 추천합니다.
>
> ⚠️ **거래처/발주 정보는 공통이 아닙니다** — 아래 MANAGER 이상 표를 보세요.

---

### 🟡 MANAGER 이상 (OWNER + MANAGER)

STAFF 화면에 **추가로** 필요한 것들입니다.

| 화면 | 설명 | 주요 API |
|---|---|---|
| 홈 - 관리자 섹션 | 오늘 근무자 실시간 현황(지각/결근), 오늘의 발주 목록, 이번달 인건비, 가입신청 대기 수 | `GET /stores/{id}/home` 의 `managerSection` |
| 멤버 관리 | 전체 멤버 목록(시급·역할·상태 포함), 역할/시급/공제방식 수정, 퇴사 처리 | `GET/PATCH/DELETE /members` |
| 가입 신청 승인 | 대기 목록 확인, 역할 지정 승인/거절 | `GET /join-requests`, `POST /.../approve`, `/reject` |
| 스케줄 관리 | 스케줄 생성/수정/삭제, 반복 생성, 시프트 템플릿 관리 | `/shifts`, `/shifts/recurring`, `/shift-templates` |
| AI 스케줄 초안 생성 | 기간+자유 텍스트 요구사항 입력 → AI가 초안 제안, **저장 전 반드시 사용자 확인 필요**(accepted/rejected 분리 응답) | `POST /shifts/ai-draft` (미리보기, 비저장) → `POST /shifts/ai-draft/confirm` (사용자가 확인/수정한 목록을 실제 저장) |
| 근태 관리 | 매장 전체 근태 조회, 수동 등록/보정, 근태 리포트(지각·결근 자동판정) | `/attendance`, `/attendance/report` |
| 연차 관리 | 멤버 연차 사용 등록/취소 | `POST/DELETE /members/{id}/leaves` |
| 급여 대시보드 | 월간 인건비 합계, 멤버별 비용 | `GET /dashboard?year=&month=` |
| 일일/주간 현황 | 그날 근무·근태 요약, 주 52시간 초과 사전 경고 | `GET /dashboard/daily`, `/dashboard/weekly` |
| **거래처/발주 관리** | 발주처 등록·조회, 품목별 요일 발주량 관리 — **STAFF 접근 불가** | `GET/POST/PATCH/DELETE /suppliers`, `/suppliers/{id}/items` |
| 매뉴얼/공지/체크리스트/인수인계 **관리** | 작성·수정·삭제 (STAFF는 조회만) | 각 도메인 POST/PATCH/DELETE |
| 메뉴 원가 계산기 | 재료 단가 등록, 메뉴별 레시피·원가율·이익 계산 | `/menu-ingredients`, `/menus` |

---

### 🔴 OWNER 전용

MANAGER도 못 하는, 매장 자체에 대한 결정 권한입니다.

| 화면 | 설명 | 주요 API |
|---|---|---|
| 매장 정보 수정 | 이름/주소/업종/영업시간/휴게정책/5인미만 설정 | `PATCH /stores/{id}` |
| 초대코드 관리 | 코드 조회, 재발급 | `GET /stores/{id}/invite-code`, `POST /.../regenerate` |
| 매장 삭제 | **매장 이름을 정확히 입력해야 실행** (실수 방지 확인창 필수) | `DELETE /stores/{id}?confirmName=` |
| 소유권 이전 | 재직 중인 다른 멤버에게 OWNER 넘기기, **매장 이름 확인 필수** (이전 후 본인은 MANAGER로 강등되며, 이후 원하면 매장 나가기 가능) | `POST /stores/{id}/transfer-ownership` |

> ⚠️ **매장 삭제·소유권 이전 화면은 반드시 "매장 이름을 입력하세요" 형태의 확인 모달을 넣어야 합니다.** 서버가 이름 불일치 시 400 에러로 막지만, UX상으로도 실수 방지 문구가 필요합니다 (GitHub의 저장소 삭제 확인창과 같은 패턴).

---

## 2. 화면 구현 시 체크리스트

프론트 개발자에게 화면 하나를 요청할 때 이 4가지를 같이 알려주면 충분합니다:

1. **누가 보는 화면인가** (STAFF만 / MANAGER 이상 / OWNER 전용 / 전체 공통이지만 관리자만 편집)
2. **어떤 API를 호출하는가** (위 표의 엔드포인트, 자세한 요청/응답 스펙은 `/swagger-ui.html`)
3. **실패 시 어떻게 보여줄 것인가** (400/403/404/409 각각 메시지 그대로 노출 가능)
4. **파괴적 동작인지** (삭제·퇴사 등은 확인 모달 필수)

### 예시 — 실제로 이렇게 요청하면 됩니다
> "거래처/발주 관리 화면을 만들어주세요.
> - **MANAGER 이상만 접근 가능** (STAFF는 메뉴 자체를 숨겨주세요, 직접 호출해도 403 뜹니다)
> - `GET /api/v1/stores/{storeId}/suppliers` 로 거래처+품목 목록 받아서 카테고리별로
> - 품목마다 요일별 발주량(`weeklyQuantities`)이 있으니 오늘 요일에 해당하는 것만 강조 표시하면 좋습니다
> - 등록/수정/삭제는 `POST/PATCH/DELETE /suppliers`, 품목은 `/suppliers/{id}/items`
> - 스펙 자세한 건 Swagger `/swagger-ui.html`에서 `SupplierController` 보시면 됩니다"

---

## 3. Swagger 활용법

`http://{서버주소}:8080/swagger-ui.html` 에서:
- 전체 엔드포인트의 정확한 요청/응답 필드, 타입, 필수 여부 확인 가능
- "Authorize" 버튼에 로그인으로 받은 `accessToken`을 넣으면 브라우저에서 바로 API 테스트 가능

이 문서는 **"어떤 화면이 필요한지"**를 정리한 것이고, **"각 API의 정확한 필드"**는 Swagger를 기준으로 삼아주세요 (코드가 계속 바뀌므로 이 문서보다 Swagger가 항상 최신입니다).
