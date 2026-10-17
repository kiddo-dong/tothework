# 알밤(Al-Bam) 백엔드 PRD (Product Requirements Document)

> 버전: v1 (2026-07 기준 구현 상태 스냅샷, 2026-10 모듈러 모놀리스 재구축 반영)
> 대상 독자: 백엔드/프론트엔드 개발자, 기획
> 원칙: 이 문서는 **현재 구현된 코드**를 기준으로 작성되었습니다. 코드가 바뀌면 이 문서보다 코드/Swagger가 우선합니다.
>
> 2026-10 백엔드를 모듈러 모놀리스로 다시 지었다. API 경로·JSON·비즈니스 규칙은 그대로이고, 응답이 달라진 곳은
> 다섯 군데뿐이다.
>
> | 상황 | 이전 | 지금 |
> |---|---|---|
> | 다른 매장의 멤버에게 스케줄 배정 | 400 "해당 매장의 멤버가 아닙니다" | 404 "멤버를 찾을 수 없습니다" |
> | 직함(title) 20자 초과 | 검증이 없어 DB 오류가 409로 나갔다 | 400 |
> | 필수 헤더 누락 | 500 | 400 |
> | 로그인 없이 열린 경로 아래의 없는 주소 | 500 | 404 "요청한 경로를 찾을 수 없습니다." |
> | 탈퇴 차단 | 삭제된 매장의 소속도 막았다(삭제된 매장은 나갈 수 없어 탈퇴가 영영 막혔다) | 운영 중인 매장의 소속만 막는다 |

---

## 1. 제품 개요

### 1.1 한 줄 정의
자영업자(사장님)가 알바생·매장 운영 전반(근태·스케줄·급여·발주·매뉴얼·공지 등)을 **근로기준법을 준수하면서** 관리할 수 있게 해주는 웹/앱 서비스의 백엔드 API.

### 1.2 문제 정의
- 초소형 매장(5인 미만) 사장님은 근로기준법 계산(연장·야간·주휴수당, 최저임금, 연소근로자 보호)을 정확히 알지 못한 채 급여를 지급하는 경우가 많다.
- 알바생은 본인이 얼마를 받을지, 왜 그 금액인지 알기 어렵다.
- 매장마다 발주처·매뉴얼·체크리스트가 제각각이라 신입 알바생 온보딩 비용이 크다.

### 1.3 포지셔닝 (경쟁사 대비)
경쟁 서비스(예: 아울러)가 중대형 매장의 스케줄/근태 관리에 집중하는 반면, 알밤은:
- **초소형 매장 특화**: 5인 미만 사업장 예외 규정을 1급 시민으로 지원 (스케줄 상한, 급여 가산 계산 모두 분기 처리).
- **준법 자동화**: 사장님이 근로기준법을 몰라도 시스템이 최저임금 하한, 연장/야간/휴일/주휴 수당, 연소근로자 보호를 자동 계산·차단.
- **알바생 경험**: 알바생도 본인의 예상 월급, 급여명세서, 연차 현황을 직접 조회 가능 (사장님만 보는 블랙박스가 아님).

### 1.4 스코프 밖 (Non-goals, 명시적 제외)
- 실제 발주서 작성/전송, 재고 추적 (거래처 도메인은 "참조용 디렉토리"일 뿐).
- 전자근로계약서, 급여명세서 PDF 생성 (향후 후보).
- 연차의 80% 출근율 요건, 연차 소멸/사용촉진, 3년차 이후 가산일수 — `LeaveService`에 Javadoc으로 명시된 향후 과제.
- Spring Security 역할 기반 `@PreAuthorize` — 인가는 전부 서비스 계층의 `StoreAuthorizationService` 명시적 호출로 처리 (의도적 설계).

---

## 2. 사용자 및 권한 모델

### 2.1 역할은 "유저"가 아니라 "매장×유저" 단위
한 사용자가 A매장에서 OWNER, B매장에서 STAFF일 수 있다. 역할은 `StoreMember.role`에 저장되며, Spring Security는 인증 여부(`ROLE_USER`)만 판단하고 실제 권한 분기는 서비스 계층에서 수행한다.

| 역할 | 정의 | 권한 범위 |
|---|---|---|
| STAFF | 알바생 | 본인 데이터 조회/입력 위주 (출퇴근, 본인 스케줄, 본인 급여 예상) |
| MANAGER | 매니저 | STAFF 권한 + 운영 관리(스케줄/근태/급여 대시보드/발주/멤버 관리/가입승인) |
| OWNER | 사장님 | MANAGER 권한 + 매장 자체의 생사여탈(정보수정/삭제/소유권 이전/초대코드 재발급) |

권한은 상위 포함 관계(`OWNER ⊇ MANAGER ⊇ STAFF`)이며, `StoreAuthorizationService`가 3단계 게이트를 제공한다: `requireMember`(ACTIVE 멤버), `requireOwnerOrManager`, `requireOwner`.

### 2.2 인증
- JWT Access/Refresh 토큰 (HMAC 서명, `type` 클레임으로 구분).
- 로컬 회원가입/로그인 + Google/Kakao/Naver 소셜 로그인(클라이언트가 provider 토큰 획득 → 백엔드가 provider의 userinfo 엔드포인트로 검증).
- 이메일 인증 필수(로컬 가입), 소셜 가입은 `emailVerified=true`로 시작.
- 소셜 가입 직후 `profileCompleted=false`일 수 있음 (전화번호/생년월일/약관동의 미입력) → 프론트는 이 값으로 추가정보 입력 화면 분기.

---

## 3. 도메인별 요구사항 명세

각 도메인은 "목적 → 데이터 모델 → 엔드포인트 → 핵심 비즈니스 규칙" 순으로 기술합니다. 전체 API는 `/api/v1` 하위, 응답은 `ApiResponse<T> = {success, data, message}` 포맷.

### 3.1 user — 인증/프로필

**목적**: 계정 생성, 로그인, 프로필 관리, 안전한 탈퇴.

**핵심 필드**: email(unique), password(BCrypt, 소셜은 null), name, phone(unique), birthDate, termsAgreedAt, profileImageUrl, provider(LOCAL/GOOGLE/KAKAO/NAVER), emailVerified, deletedAt.

**엔드포인트**
| Method | Path | 인증 | 설명 |
|---|---|---|---|
| POST | /auth/signup | 공개 | 회원가입, 인증 메일 발송(24h 유효) |
| POST | /auth/login | 공개 | 이메일 미인증 시 로그인 차단 |
| POST | /auth/refresh | 공개 | 리프레시 토큰으로 재발급 |
| POST | /auth/oauth/{provider} | 공개 | google/kakao/naver |
| GET | /auth/verify-email?token= | 공개 | |
| POST | /auth/resend-verification | 공개 | |
| POST | /auth/password-reset/request | 공개 | 존재하지 않는 이메일이어도 응답 동일(정보 노출 방지) |
| POST | /auth/password-reset/confirm | 공개 | 토큰 30분 유효 |
| GET/PATCH | /users/me | 인증 | |
| POST | /users/me/complete-profile | 인증 | 이미 완료 시 409 |
| DELETE | /users/me | 인증 | 탈퇴(아래 규칙) |
| POST/DELETE | /users/me/profile-image | 인증 | S3 업로드/삭제 |

**비즈니스 규칙**
- 비밀번호: 8자 이상 + {영문/숫자/특수문자} 중 2종류 이상.
- 전화번호 형식: `01[0-9]-\d{3,4}-\d{4}`.
- 로그인 실패 메시지는 "이메일 없음"과 "비밀번호 틀림"을 구분하지 않음 (계정 존재 여부 비노출).
- **탈퇴 차단 조건**: 운영 중인(삭제되지 않은) 매장에 ACTIVE 소속이 하나라도 있으면 탈퇴 불가(먼저 매장을 나가야 함).
- 탈퇴 시 완전 삭제가 아니라 **익명화**: email → `deleted-{id}@withdrawn.albam`, name → "탈퇴회원", phone/생년월일/프로필사진/providerId null 처리, `deletedAt` 세팅. 프로필 이미지는 S3에서 실제 삭제.

### 3.2 store — 매장

**핵심 필드**: name, address, businessRegistrationNumber, category(FOOD/CAFE/CONVENIENCE_STORE/RETAIL/BEAUTY/EDUCATION/FITNESS/ETC), businessHours(요일별 open/close/휴무), inviteCode(6자리), breakPolicy(STATUTORY/FLEXIBLE), **smallBusiness**(5인 미만 여부 — 급여/스케줄 계산 전반에 영향을 주는 핵심 플래그).

**엔드포인트**
| Method | Path | 권한 |
|---|---|---|
| POST | /stores | 인증(생성자=OWNER 자동 등록) |
| GET | /stores | 본인 소속 매장 목록 + myRole |
| GET/PATCH | /stores/{id} | 멤버 / OWNER |
| DELETE | /stores/{id}?confirmName= | OWNER, 매장명 정확 입력 필수 |
| GET | /stores/{id}/invite-code | OWNER/MANAGER |
| POST | /stores/{id}/invite-code/regenerate | OWNER |
| POST | /stores/{id}/transfer-ownership | OWNER, 매장명 확인 필수 |

**비즈니스 규칙**
- 초대코드는 혼동 문자(I/O/0/1) 제외 문자셋에서 생성, 중복 시 재생성.
- 소유권 이전: 대상은 이전을 받으면 OWNER, 기존 오너는 MANAGER로 강등(매장당 OWNER 정확히 1명 유지). 자기 자신 지정 불가, 퇴사자 지정 불가, 매장명 오타 시 400.
- 매장 삭제/소유권 이전 둘 다 "매장 이름을 정확히 입력"하는 확인 절차 필수 (되돌릴 수 없는 작업이므로).

### 3.3 storemember — 매장 멤버

**핵심 필드**: role, hourlyWage, status(ACTIVE/INACTIVE), joinedAt/resignedAt, availableDays(근무가능요일), weeklyHolidayDay(주휴수당 대상 요일), taxMode(NONE/WITHHOLDING_3_3/FOUR_INSURANCES).

**엔드포인트**
| Method | Path | 권한 |
|---|---|---|
| GET | /members | OWNER/MANAGER (시급 등 상세 정보 포함) |
| GET | /members/summary | 전체 멤버 (이름+역할만, 개인정보 최소화) |
| PATCH | /members/{id} | OWNER/MANAGER (OWNER 행은 수정 불가) |
| PATCH | /members/me/available-days | 본인 |
| DELETE | /members/me | 본인 퇴사 (OWNER는 불가 — 먼저 이전/삭제해야 함) |
| DELETE | /members/{id} | OWNER/MANAGER (OWNER는 대상에서 제외) |

**비즈니스 규칙**
- 시급은 항상 최저임금(10,320원) 이상이어야 함.
- **퇴사는 소프트 삭제** (status→INACTIVE, resignedAt 기록) — 근태/급여 이력 보존을 위해 행을 지우지 않음. 재가입 시 새 행이 아니라 기존 행을 `rejoin()`으로 재활성화.

### 3.4 invite — 가입 신청/승인

**핵심 필드**: JoinRequest(store, user, status(PENDING/APPROVED/REJECTED), decidedRole).

**엔드포인트**
| Method | Path | 권한 |
|---|---|---|
| POST | /join-requests | 인증(초대코드로 신청) |
| GET/DELETE | /join-requests/me | 본인 |
| GET | /stores/{id}/join-requests | OWNER/MANAGER (PENDING만) |
| POST | /stores/{id}/join-requests/{id}/approve | OWNER/MANAGER, body에 role 지정 |
| POST | /stores/{id}/join-requests/{id}/reject | OWNER/MANAGER |

**비즈니스 규칙**
- 이미 ACTIVE 멤버이거나 이미 PENDING 신청이 있으면 409.
- **OWNER 역할로 승인 불가** (소유권은 이전 기능으로만 넘어감).
- 과거 퇴사 이력이 있는 유저가 재승인되면 새 행이 아니라 기존 INACTIVE 행을 재활성화(이력 보존).

### 3.5 attendance — 근태

**핵심 필드**: Attendance(storeMember, workDate, clockInAt, clockOutAt, status(WORKING/DONE), breakMinutes).

**엔드포인트**
| Method | Path | 권한 |
|---|---|---|
| POST | /attendance | OWNER/MANAGER (누락된 출근 수기 등록) |
| POST | /attendance/clock-in, /clock-out | 본인 |
| GET | /attendance/me?from&to | 본인 |
| GET | /attendance?from&to | OWNER/MANAGER (매장 전체) |
| GET | /attendance/report?storeMemberId&from&to | 본인 또는 OWNER/MANAGER |
| PATCH/DELETE | /attendance/{id} | OWNER/MANAGER (하드 삭제 — 이 도메인은 예외) |

**비즈니스 규칙 — 준법 리포트 (`AttendanceReportService`)**
스케줄(Shift)과 실근태(Attendance)를 요일/멤버별로 대조하여 판정: `NORMAL/LATE/EARLY_LEAVE/LATE_AND_EARLY_LEAVE/ABSENT/ON_LEAVE/WORKING/EXTRA`.
- 아직 시작 전이거나 진행 중(종료시간 미도래)인 스케줄은 미매칭이어도 ABSENT로 단정하지 않음(판단 유보).
- 해당 날짜에 연차(LeaveUsage)가 있으면 미매칭 스케줄은 ABSENT 대신 ON_LEAVE.
- 스케줄 없이 발생한 근태는 EXTRA.
- 취소된(CANCELED)/미래 스케줄은 판정에서 제외.

### 3.6 shift — 스케줄

**핵심 필드**: Shift(storeMember, workDate, startTime, endTime, status(SCHEDULED/CONFIRMED/CANCELED), breakMinutes, 자정 초과 근무 지원). ShiftTemplate(매장별 이름 있는 시간 프리셋 — 생성 시 값만 복사, 사후 수정과 무관).

**엔드포인트**
| Method | Path | 권한 |
|---|---|---|
| POST | /shifts | OWNER/MANAGER |
| POST | /shifts/recurring | OWNER/MANAGER (최대 92일, 요일 반복 생성, 실패일자는 skip 목록으로 리턴하며 전체 실패시키지 않음) |
| GET | /shifts?storeMemberId&from&to | 전체 멤버 |
| PATCH/DELETE | /shifts/{id} | OWNER/MANAGER (하드 삭제) |
| CRUD | /shift-templates | 조회는 전체, 생성/수정/삭제는 OWNER/MANAGER |

**비즈니스 규칙 — 생성/수정 시 5단계 검증**
1. 근무가능요일(availableDays) 및 매장 영업시간(휴무일/시간외) 확인.
2. **연소근로자(18세 미만) 보호**: 일 7시간 초과 금지, 22:00~06:00 야간시간 배정 금지, 본인의 주휴요일 배정 금지.
3. 동일 멤버 근무 겹침 방지(자정 초과 포함, day-1~day+1 후보 윈도우로 검사).
4. **주간 상한**: 성인 52시간(`MAX_WEEKLY_WORK_MINUTES`)/연소자 35시간, 단 **5인 미만 사업장(smallBusiness)은 성인 상한 검사 자체를 생략**.
5. 휴게시간은 매장의 `BreakPolicy`(STATUTORY/FLEXIBLE)에 따라 자동/수동 산정.
- 상태를 CANCELED로 바꾸는 수정은 위 검증을 모두 건너뜀.

### 3.7 leave — 연차

**핵심 필드**: LeaveUsage(storeMember, leaveDate — 멤버당 날짜 unique).

**엔드포인트**
| Method | Path | 권한 |
|---|---|---|
| GET | /members/{id}/leaves | 본인 또는 OWNER/MANAGER |
| POST | /members/{id}/leaves | OWNER/MANAGER |
| DELETE | /leaves/{id} | OWNER/MANAGER (하드 삭제) |

**비즈니스 규칙 (근로기준법 제60조 간이 구현)**
- 근속 12개월 미만: `min(근속월수, 11)`일. 12개월 이상: `11 + 15 × 근속연수`.
- **의도적으로 미구현**(코드 주석에 명시): 80% 출근율 요건, 연차 소멸/사용촉진, 3년차 이후 2년마다 +1일 가산 — 향후 과제.
- 잔여일수 초과 사용, 동일 날짜 중복, 퇴사자에 대한 연차 등록은 차단.

### 3.8 payroll — 급여/대시보드 (핵심 준법 엔진)

**핵심 필드**: Payroll(멤버×연월 unique upsert 캐시 — regularPay, overtimePay, nightPay, holidayWorkPay, weeklyHolidayPay, leavePay, totalPay, deduction, netPay).

**엔드포인트**
| Method | Path | 권한 | 비고 |
|---|---|---|---|
| GET | /payroll?memberId&year&month | 본인/OWNER·MANAGER | **조회가 곧 생성/갱신**(부수효과로 Payroll row upsert) |
| GET | /payroll/payslip?memberId&year&month | 본인/OWNER·MANAGER | 일자별 내역 포함, 비영속(순수 계산) |
| GET | /payroll/me/estimate?year&month | 본인 | 미래 스케줄 기준 실시간 예상치, 비영속 |
| GET | /dashboard?year&month | OWNER/MANAGER | 월간 인건비 총액 + 멤버별 |
| GET | /dashboard/daily?date | OWNER/MANAGER | 금액 없음, 시간/준법만 |
| GET | /dashboard/weekly?date | OWNER/MANAGER | 52h/35h 초과 경고, 주휴 자격 여부 — 금액 없음 |

**계산 로직 (`PayrollCalculator`) — 근로기준법 반영 상세**
- 기본급 = (실근무시간 − 휴게) × 시급.
- **연장근로(1.5배)**: 일 8시간 초과분 + 주 40시간 초과 누적분을 발생일에 귀속. 휴일근로 시간과는 절대 중복 계산하지 않음(휴일근로는 연장근로 계산에서 제외).
- **야간근로(+0.5배)**: 22:00~06:00 겹치는 시간 전체(휴게시간이 야간대에 걸쳐도 현재는 차감하지 않음 — 코드에 향후 개선사항으로 명시된 알려진 한계).
- **휴일근로**: 멤버의 `weeklyHolidayDay`에 근무 시 기본 1배 + 8시간까지 0.5배 가산, 8시간 초과분은 1.0배(더블) 가산. 연장/주간상한 계산과 별개.
- **주휴수당**: 주 15시간 이상 **완전 개근** 시 지급. `min(주근무시간,40)/40 × 8 × 시급`, 해당 주의 **일요일이 속한 달**에 귀속(월 경계 이슈 방지). 스케줄이 아예 없는 주는 개근으로 간주.
- **5인 미만 사업장 예외**: 연장 1.0배(가산 없음), 야간수당 0, 휴일가산 0 — **단 주휴수당은 그대로 적용**(면제 대상 아님).
- **연차수당**: 해당월 연차일수 × 평균 소정근로시간 × 시급 (스케줄 없으면 8시간 기본값).
- **공제**: taxMode별 — 원천징수 3.3%(`WITHHOLDING_TAX_RATE`) 또는 4대보험 근로자부담 근사 9.404%(`FOUR_INSURANCES_EMPLOYEE_RATE`).
- **estimateMyPayroll**: 이번 달 남은 미래 스케줄을 "그대로 근무할 것"으로 가정한 가상 Attendance를 만들어 실제 완료 근태와 합쳐 계산 — 미래 주휴수당 자격도 낙관적으로 가정(코드에 한계로 명시).

### 3.9 menu — 메뉴 원가 계산기

**핵심 필드**: MenuIngredient(price, packageQty, unit(G/ML/EA), lossRate(로스율) → `unitCost = price/(packageQty×(1−lossRate/100))`). StoreMenu + MenuRecipeItem(재료 참조+사용량만 저장, **원가는 스냅샷하지 않고 조회 시점에 실시간 계산** — Payroll과 대비되는 설계).

**엔드포인트**: `/menu-ingredients`, `/menus` 전부 CRUD, **전부 OWNER/MANAGER 전용** (원가·이익은 민감정보로 STAFF 접근 차단).

**비즈니스 규칙**
- 재료 삭제 시 이를 참조하는 레시피 항목도 함께 삭제(하드 삭제).
- 메뉴 수정 시 레시피 리스트는 부분 수정이 아니라 전체 교체.
- `이익 = 판매가 − 원가`, `원가율/이익률 = ×100/판매가` (판매가 0이면 0으로 처리, 0나눗셈 방지).

### 3.10 supplier — 거래처/발주 참조

**핵심 필드**: Supplier(name, category(자유 텍스트), siteUrl, phone). SupplierItem(요일별 발주수량을 자유 텍스트 맵으로 저장, 예: "2박스").

**엔드포인트**: `/suppliers`, `/suppliers/{id}/items` CRUD — **전부 OWNER/MANAGER 전용** (2026-07 변경: 기존엔 전체 멤버 조회 가능이었으나 발주 정보 민감성 때문에 제한).

**비즈니스 규칙**: 실제 발주서 작성·재고 추적은 스코프 밖 — "오늘 어디서 뭘 얼마나 시켜야 하는지 보여주는 참조 디렉토리"가 목적. 로그인 계정 정보는 저장하지 않음.

### 3.11 checklist — 오픈/마감 체크리스트

**핵심 필드**: ChecklistItem(OPEN/CLOSE, 매장 마스터 목록). ChecklistCompletion(항목×날짜 unique, 체크한 사람/시각 기록).

**엔드포인트**: 항목 CRUD(OWNER/MANAGER 생성/수정/삭제, 조회는 전체) + `GET /checklist?date=`(일자별 현황) + `POST/DELETE .../check?date=`(체크/해제, **멱등**).

### 3.12 notice — 공지사항

**핵심 필드**: Notice + NoticeRead(공지×멤버 unique, 읽음 확인).

**엔드포인트**: 작성/삭제 OWNER/MANAGER, 조회는 전체(각 공지에 `readCount`/`readByMe` 포함), `POST /{id}/read` 멱등 읽음처리, `GET /{id}/reads`(OWNER/MANAGER, 멤버별 열람 현황).

### 3.13 handover — 인수인계 노트

**핵심 필드**: HandoverNote(author, content, workDate).

**엔드포인트**: 작성/조회는 전체 멤버, **삭제는 작성자 본인 또는 OWNER/MANAGER만** 가능. 홈 화면에는 최근 5건(어제+오늘)만 노출.

### 3.14 manual — 매뉴얼(지식 베이스)

**핵심 필드**: Manual(category 자유텍스트, title, content, imageUrls(순서 있는 리스트)).

**엔드포인트**: 작성/수정/삭제 OWNER/MANAGER, 조회(목록 요약/상세)는 전체, `POST /manuals/images`(S3 업로드, 이미지 URL을 본문에 삽입 후 저장).

**비즈니스 규칙**: 수정 시 이전 이미지 목록과 비교해 제거된 이미지는 S3에서도 삭제. 매뉴얼 삭제 시 DB 삭제 후 연관 이미지 전체 S3 삭제.

### 3.15 home — 역할 인지형 홈 화면

**엔드포인트**: `GET /stores/{id}/home` 단일 엔드포인트.

**구조**
- `myDay`(전체 공통): 오늘 스케줄, 근무중 여부/출근시각, 이번달 예상 순수령액, 안읽은 공지 수, 오픈/마감 체크리스트 진행률, 최근 인수인계 5건.
- `managerSection`(OWNER/MANAGER만, null 아니면 노출): 오늘 근무자 현황(준법 판정 포함), 오늘의 발주 품목(요일 필터링), 이번달 인건비/순지급액 총계, 대기 중인 가입신청 수.

이 도메인은 독립 로직 없이 다른 도메인 서비스를 조합만 하는 순수 애그리게이터.

---

## 4. 크로스커팅 설계 원칙

1. **소프트 삭제 vs 하드 삭제**: 근태/급여 이력에 연결된 것(User 탈퇴, StoreMember 퇴사)은 소프트 삭제. 그 외(Attendance 관리자 삭제, Shift, LeaveUsage, ShiftTemplate, Checklist, Notice, Manual, Supplier, MenuIngredient/StoreMenu, JoinRequest 취소)는 하드 삭제.
2. **인가는 서비스 계층 책임**: Spring Security는 인증만 담당(`ROLE_USER` 고정), 모든 도메인 서비스 메서드 첫 줄이 `storeAuthorizationService.require*` 호출.
3. **멱등 토글**: 체크리스트 체크, 공지 읽음 처리는 이미 완료 상태에서 재호출해도 에러 없이 동일 결과.
4. **파괴적 작업의 이름 확인 절차**: 매장 삭제·소유권 이전은 매장명을 정확히 입력해야 실행 (GitHub 저장소 삭제와 동일한 UX 패턴).
5. **5인 미만 사업장 플래그의 전역 영향**: 스케줄 주간 상한(성인만 면제), 급여의 연장/야간/휴일가산(면제) — 단 **주휴수당과 연소근로자 보호는 사업장 규모와 무관하게 항상 적용**.
6. **연소근로자(18세 미만) 보호**는 스케줄 생성 시점(차단)과 대시보드(경고) 이중으로 체크되나, `birthDate`가 null(OAuth 프로필 미완성)이면 성인으로 간주됨 — **알려진 갭**, 프로필 완성 강제 유도 필요.
7. **`GET /payroll`은 순수 조회가 아니라 upsert 부수효과**를 가짐 — `/payslip`, `/me/estimate`는 비영속 순수 계산. 프론트는 이 차이를 인지하고 캐싱 전략을 짜야 함.

---

## 5. 근로기준법 상수 (Appendix)

`global/labor/LaborStandards.java` 기준 (변경 시 이 문서도 갱신 필요):

| 상수 | 값 | 의미 |
|---|---|---|
| MINIMUM_HOURLY_WAGE | 10,320원 | 2026년 최저시급 |
| MAX_WEEKLY_WORK_MINUTES | 52h | 성인 주 상한(소정40+연장12) |
| MINOR_MAX_DAILY_WORK_MINUTES | 7h | 연소자 일 상한 |
| MINOR_MAX_WEEKLY_WORK_MINUTES | 35h | 연소자 주 상한 |
| WEEKLY_HOLIDAY_ELIGIBLE_MINUTES | 15h | 주휴수당 발생 기준 |
| OVERTIME_MULTIPLIER | 1.5× | 연장근로 가산 |
| NIGHT_PREMIUM_MULTIPLIER | +0.5× | 야간근로 가산 |
| HOLIDAY_PREMIUM_MULTIPLIER | +0.5× | 휴일근로 8h 이내 가산 |
| HOLIDAY_OVERTIME_PREMIUM_MULTIPLIER | +1.0× | 휴일근로 8h 초과 가산 |
| WITHHOLDING_TAX_RATE | 3.3% | 사업소득 원천징수(소득세3%+지방소득세0.3%) |
| FOUR_INSURANCES_EMPLOYEE_RATE | 9.404% | 4대보험 근로자부담 근사 합계 |

---

## 6. 비기능 요구사항 (현재 상태 및 과제)

- **API 버전**: `/api/v1` 고정, Swagger UI(`springdoc`)로 실시간 스펙 제공, JWT Bearer 인증 지원.
- **에러 응답**: 모든 예외가 `ApiResponse{success:false, message}`로 통일, HTTP 상태코드 400/401/403/404/409/413/500 매핑.
- **파일 업로드**: 프로필/매뉴얼 이미지는 S3, 10MB 제한.
- **메일**: Gmail SMTP(App Password 필요), 인증 메일 24h, 비밀번호 재설정 30분 유효.
- **스키마**: 서비스마다 MySQL 스키마(`tw_identity`, `tw_store`, `tw_workforce`, `tw_operations`, `tw_laborqa`)와
  Flyway 마이그레이션.
- **검증**: API 통합 테스트(실제 MySQL), 급여 계산기 단위 테스트, 동시성·쿼리 수 테스트가 있다.
- **향후 과제**: 전자근로계약서, 급여명세서 PDF — 준법 라인업 후보 기능.

---

## 7. 용어집

| 용어 | 설명 |
|---|---|
| 소정근로시간 | 근로계약서상 정해진 근무시간 |
| 연장근로 | 소정근로시간을 초과한 근무 (일 8h/주 40h 기준) |
| 야간근로 | 22:00~06:00 사이의 근무 |
| 휴일근로 | 주휴일(무급/유급 휴일)에 이루어진 근무 |
| 주휴수당 | 주 15시간 이상 개근 시 지급되는 유급휴일수당 |
| 5인 미만 사업장 | 상시 근로자 5인 미만 — 근로기준법 일부 조항(연장/야간/휴일 가산, 주 52h 상한) 적용 제외 |
| 연소근로자 | 18세 미만 근로자 — 근로시간·야간근무·휴일근무 특별 보호 대상 |
