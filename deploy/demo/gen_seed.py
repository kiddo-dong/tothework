#!/usr/bin/env python3
"""포트폴리오 캡처용 데모 매장(온기커피 성수점) 시드 SQL 생성기.

python3 gen_seed.py > seed_demo.sql
모든 데모 계정 이메일은 @demo.tothework.com, 비밀번호는 Demo1234!
"""
import random
from datetime import date, datetime, time, timedelta

random.seed(20261015)

TODAY = date(2026, 10, 15)
NOW = datetime(2026, 10, 15, 14, 20)
START = date(2026, 8, 1)          # 스케줄·근태 시작일
SHIFT_END = date(2026, 10, 31)    # 스케줄은 이달 말까지
PW = "$2a$10$d00fhxAaI305ljpKOHvr0uEv1iBrR7sLvDv7yHB1zqUeqGBGNj4JG"  # Demo1234!
DAYS = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]

out = []
w = out.append


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "b'1'" if v else "b'0'"
    if isinstance(v, (int, float)):
        return str(v)
    if isinstance(v, datetime):
        return f"'{v:%Y-%m-%d %H:%M:%S}'"
    if isinstance(v, date):
        return f"'{v:%Y-%m-%d}'"
    if isinstance(v, time):
        return f"'{v:%H:%M:%S}'"
    if isinstance(v, str) and v.startswith("@"):
        return v
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def insert(table, cols, rows, chunk=200):
    for i in range(0, len(rows), chunk):
        vals = ",\n  ".join("(" + ", ".join(q(v) for v in r) + ")" for r in rows[i:i + chunk])
        w(f"INSERT INTO {table} ({', '.join(cols)}) VALUES\n  {vals};")


def dt(d, t):
    return datetime.combine(d, t)


w("-- 데모 매장 시드: 온기커피 성수점 (gen_seed.py로 생성)")
w("SET NAMES utf8mb4;")
w("START TRANSACTION;")

# ---------------------------------------------------------------- 사용자
# key, 이름, 전화, 생년월일, 가입일
users = [
    ("owner",  "김도현", "010-7341-2081", date(1988, 3, 14), date(2025, 11, 3)),
    ("mgr",    "이서연", "010-7341-2082", date(1995, 7, 22), date(2025, 11, 10)),
    ("s1",     "박준호", "010-7341-2083", date(1999, 1, 9),  date(2026, 1, 5)),
    ("s2",     "최유진", "010-7341-2084", date(2002, 5, 30), date(2026, 2, 16)),
    ("s3",     "정민재", "010-7341-2085", date(2001, 11, 2), date(2026, 3, 2)),
    ("s4",     "한지우", "010-7341-2086", date(2004, 8, 17), date(2026, 4, 20)),
    ("s5",     "오세린", "010-7341-2087", date(2003, 2, 25), date(2026, 6, 1)),
    ("minor",  "강하늘", "010-7341-2088", date(2009, 6, 11), date(2026, 7, 13)),
    ("left",   "윤태오", "010-7341-2089", date(2000, 9, 3),  date(2026, 1, 12)),
    ("apply",  "신지호", "010-7341-2090", date(2003, 12, 7), date(2026, 10, 13)),
]
email = {
    "owner": "owner", "mgr": "manager", "s1": "junho", "s2": "yujin", "s3": "minjae",
    "s4": "jiwoo", "s5": "serin", "minor": "haneul", "left": "taeo", "apply": "jiho",
}
for k, name, phone, birth, joined in users:
    ts = dt(joined, time(10, 12))
    insert("users", ["email", "password", "name", "phone", "birth_date", "terms_agreed_at", "provider",
                     "email_verified", "created_at", "updated_at"],
           [(f"{email[k]}@demo.tothework.com", PW, name, phone, birth, ts, "LOCAL", True, ts, ts)])
    w(f"SET @u_{k} = LAST_INSERT_ID();")

# ---------------------------------------------------------------- 매장
store_created = datetime(2025, 11, 3, 10, 30)
insert("stores", ["name", "address", "business_registration_number", "category", "invite_code", "break_policy",
                  "small_business", "payday", "plan", "onboarding_completed_at", "created_at", "updated_at"],
       [("온기커피 성수점", "서울 성동구 성수이로 88 1층", "214-87-50392", "CAFE", "ONGI88", "STATUTORY",
         False, 10, "BASIC", store_created + timedelta(minutes=25), store_created, store_created)])
w("SET @store = LAST_INSERT_ID();")

hours = []
for d in DAYS:
    if d in ("SATURDAY", "SUNDAY"):
        hours.append(("@store", d, time(9, 0), time(22, 0), False))
    else:
        hours.append(("@store", d, time(8, 0), time(22, 0), False))
insert("store_business_hours", ["store_id", "day_of_week", "open_time", "close_time", "closed"], hours)

# ---------------------------------------------------------------- 멤버
# key, 역할, 직함, 시급, 세금, 주휴요일, 근무가능요일, 상태
members = [
    ("owner", "OWNER",   "대표",     0,     "NONE",            None,     DAYS, "ACTIVE"),
    ("mgr",   "MANAGER", "점장",     12500, "FOUR_INSURANCES", "SUNDAY", DAYS[:6], "ACTIVE"),
    ("s1",    "STAFF",   "수석 바리스타", 11200, "WITHHOLDING_3_3", "SUNDAY", DAYS[:5], "ACTIVE"),
    ("s2",    "STAFF",   "바리스타",  10700, "WITHHOLDING_3_3", "SUNDAY", DAYS[:5], "ACTIVE"),
    ("s3",    "STAFF",   "바리스타",  10500, "WITHHOLDING_3_3", "TUESDAY", ["MONDAY", "WEDNESDAY", "FRIDAY", "SATURDAY", "SUNDAY"], "ACTIVE"),
    ("s4",    "STAFF",   "홀",       10320, "WITHHOLDING_3_3", "MONDAY", ["TUESDAY", "THURSDAY", "SATURDAY", "SUNDAY"], "ACTIVE"),
    ("s5",    "STAFF",   "홀",       10320, "NONE",            "MONDAY", ["FRIDAY", "SATURDAY", "SUNDAY"], "ACTIVE"),
    ("minor", "STAFF",   "주말 홀",   10320, "NONE",            "MONDAY", ["SATURDAY", "SUNDAY"], "ACTIVE"),
    ("left",  "STAFF",   "바리스타",  10500, "WITHHOLDING_3_3", "SUNDAY", ["TUESDAY", "THURSDAY"], "INACTIVE"),
]
joined_map = {k: j for k, _, _, _, j in users}
for k, role, title, wage, tax, hol, avail, status in members:
    j = dt(joined_map[k], time(11, 0))
    resigned = datetime(2026, 9, 12, 18, 0) if status == "INACTIVE" else None
    insert("store_members", ["store_id", "user_id", "role", "title", "hourly_wage", "status", "joined_at",
                             "resigned_at", "weekly_holiday_day", "tax_mode", "created_at", "updated_at"],
           [("@store", f"@u_{k}", role, title, wage, status, j, resigned, hol, tax, j, j)])
    w(f"SET @m_{k} = LAST_INSERT_ID();")
    insert("store_member_available_days", ["store_member_id", "day_of_week"], [(f"@m_{k}", d) for d in avail])

# 승인 완료된 가입 신청 이력 + 대기 중 1건
jr = []
for k, *_ in members[1:]:
    req = dt(joined_map[k], time(10, 40))
    jr.append(("@store", f"@u_{k}", "APPROVED", "MANAGER" if k == "mgr" else "STAFF", req, req + timedelta(minutes=20), req, req))
jr.append(("@store", "@u_apply", "PENDING", None, datetime(2026, 10, 13, 21, 5), None,
           datetime(2026, 10, 13, 21, 5), datetime(2026, 10, 13, 21, 5)))
insert("join_requests", ["store_id", "user_id", "status", "decided_role", "requested_at", "decided_at",
                         "created_at", "updated_at"], jr)

# ---------------------------------------------------------------- 근무유형
templates = [
    ("오픈", time(8, 0), time(13, 0), 30),
    ("미들", time(12, 0), time(18, 0), 30),
    ("마감", time(17, 0), time(22, 0), 30),
    ("주말 오픈", time(9, 0), time(15, 30), 30),
    ("주말 마감", time(15, 0), time(22, 0), 30),
]
insert("shift_templates", ["store_id", "name", "start_time", "end_time", "break_minutes", "display_order",
                           "created_at", "updated_at"],
       [("@store", n, s, e, b, i, store_created, store_created) for i, (n, s, e, b) in enumerate(templates)])
T = {n: (s, e, b) for n, s, e, b in templates}

# ---------------------------------------------------------------- 주간 고정 스케줄
# 요일(0=월) -> [(멤버, 근무유형)]
weekly = {
    0: [("s1", "오픈"), ("mgr", "미들"), ("s3", "마감")],
    1: [("s2", "오픈"), ("mgr", "미들"), ("s4", "마감"), ("left", "마감")],
    2: [("s1", "오픈"), ("mgr", "미들"), ("s3", "마감")],
    3: [("s2", "오픈"), ("mgr", "미들"), ("s4", "마감"), ("left", "마감")],
    4: [("s1", "오픈"), ("s2", "미들"), ("s3", "마감"), ("s5", "마감")],
    5: [("mgr", "주말 오픈"), ("minor", "주말 오픈"), ("s3", "주말 마감"), ("s4", "주말 마감"), ("s5", "주말 마감")],
    6: [("s4", "주말 오픈"), ("minor", "주말 오픈"), ("s5", "주말 마감"), ("s3", "주말 마감")],
}
member_start = {k: joined_map[k] for k in joined_map}
left_until = date(2026, 9, 12)
s1_replaces_left = date(2026, 9, 13)  # 퇴사자 자리는 박준호가 일부 메움

shifts = []      # (key, date, start, end, break, status)
for i in range((SHIFT_END - START).days + 1):
    d = START + timedelta(days=i)
    for k, tpl in weekly[d.weekday()]:
        if k == "left" and d > left_until:
            if d >= s1_replaces_left and d.weekday() == 1:
                k = "s1"
            else:
                continue
        if d < member_start[k]:
            continue
        s, e, b = T[tpl]
        if d < TODAY - timedelta(days=1):
            status = "CONFIRMED"
        elif d <= TODAY + timedelta(days=4):
            status = "CONFIRMED"
        else:
            status = "SCHEDULED"
        # 과거 일부 근무는 취소
        if d < TODAY and random.random() < 0.025 and k not in ("owner",):
            status = "CANCELED"
        shifts.append((k, d, s, e, b, status))
# 추석 연휴(10/3~10/5) 단축 영업 대신 이벤트 지원 근무 하나
shifts.append(("s1", date(2026, 10, 4), time(12, 0), time(18, 0), 30, "CONFIRMED"))

insert("shifts", ["store_member_id", "work_date", "start_time", "end_time", "break_minutes", "status",
                  "created_at", "updated_at"],
       [(f"@m_{k}", d, s, e, b, st, dt(d - timedelta(days=7), time(21, 30)), dt(d - timedelta(days=7), time(21, 30)))
        for k, d, s, e, b, st in shifts])

# ---------------------------------------------------------------- 출퇴근
att = []
working_today = []
for k, d, s, e, b, st in shifts:
    if st == "CANCELED" or d > TODAY:
        continue
    start_dt, end_dt = dt(d, s), dt(d, e)
    r = random.random()
    if r < 0.06:
        cin = start_dt + timedelta(minutes=random.randint(6, 18))   # 지각
    else:
        cin = start_dt - timedelta(minutes=random.randint(0, 12))
    cin = cin.replace(second=random.randint(0, 59))
    if d == TODAY:
        if start_dt <= NOW < end_dt:
            att.append((k, d, cin, None, 0, "WORKING"))
            working_today.append(k)
        elif end_dt <= NOW:
            cout = end_dt + timedelta(minutes=random.randint(0, 9))
            att.append((k, d, cin, cout, b, "DONE"))
        continue
    if random.random() < 0.08:
        cout = end_dt + timedelta(minutes=random.randint(15, 45))   # 연장
    else:
        cout = end_dt + timedelta(minutes=random.randint(0, 10))
    cout = cout.replace(second=random.randint(0, 59))
    att.append((k, d, cin, cout, b, "DONE"))
insert("attendances", ["store_member_id", "work_date", "clock_in_at", "clock_out_at", "break_minutes", "status",
                       "created_at", "updated_at"],
       [(f"@m_{k}", d, ci, co, b, st, ci, co or ci) for k, d, ci, co, b, st in att])

# ---------------------------------------------------------------- 연차
insert("leave_usages", ["store_member_id", "leave_date", "created_at", "updated_at"], [
    ("@m_mgr", date(2026, 8, 14), datetime(2026, 8, 3, 15, 0), datetime(2026, 8, 3, 15, 0)),
    ("@m_mgr", date(2026, 9, 25), datetime(2026, 9, 10, 15, 0), datetime(2026, 9, 10, 15, 0)),
    ("@m_s1", date(2026, 9, 8), datetime(2026, 8, 28, 11, 0), datetime(2026, 8, 28, 11, 0)),
    ("@m_s2", date(2026, 10, 22), datetime(2026, 10, 12, 19, 0), datetime(2026, 10, 12, 19, 0)),
])

# ---------------------------------------------------------------- 체크리스트
open_items = ["에스프레소 머신 예열 및 첫 샷 추출 테스트", "그라인더 분쇄도 확인 (추출 25~30초)",
              "쇼케이스 디저트 진열 및 유통기한 확인", "우유·시럽 재고 확인", "매장 테이블·의자 정리",
              "POS 시재 확인 (기본 10만원)"]
close_items = ["머신 백플러시 및 스팀봉 세척", "그라인더 호퍼 원두 정리", "남은 디저트 폐기 기록",
               "냉장고 온도 기록 (0~5℃)", "바닥 청소 및 쓰레기 분리배출", "POS 마감 정산 및 시재 확인",
               "전기·가스·간판 소등 확인"]
items = [("OPEN", c, i) for i, c in enumerate(open_items)] + [("CLOSE", c, i) for i, c in enumerate(close_items)]
for idx, (t, c, order) in enumerate(items):
    insert("checklist_items", ["store_id", "type", "content", "display_order", "created_at", "updated_at"],
           [("@store", t, c, order, store_created, store_created)])
    w(f"SET @ci_{idx} = LAST_INSERT_ID();")

# 각 날짜의 오픈 담당/마감 담당이 체크
by_date = {}
for k, d, s, e, b, st in shifts:
    if st != "CANCELED":
        by_date.setdefault(d, []).append((k, s, e))
comp = []
for i in range(30, -1, -1):
    d = TODAY - timedelta(days=i)
    people = by_date.get(d)
    if not people:
        continue
    opener = min(people, key=lambda p: p[1])
    closer = max(people, key=lambda p: p[2])
    for idx, (t, c, order) in enumerate(items):
        who, base = (opener, opener[1]) if t == "OPEN" else (closer, closer[2])
        if d == TODAY:
            if t == "CLOSE" or order >= 4:   # 오늘은 오픈 일부만 완료
                continue
        elif random.random() < 0.04:
            continue
        if t == "OPEN":
            at = dt(d, base) + timedelta(minutes=5 + order * 4 + random.randint(0, 3))
        else:
            at = dt(d, base) - timedelta(minutes=40 - order * 5 - random.randint(0, 3))
        comp.append((f"@ci_{idx}", d, f"@m_{who[0]}", at))
insert("checklist_completions", ["item_id", "work_date", "checked_by", "checked_at"], comp)

# ---------------------------------------------------------------- 인수인계
notes = [
    (date(2026, 10, 15), "s1", "오늘 오전에 우유 납품 2박스 들어왔어요. 냉장고 하단에 넣어뒀고 기존 재고부터 쓰시면 됩니다."),
    (date(2026, 10, 14), "s4", "2번 그라인더 분쇄도가 살짝 굵어서 2칸 조정했어요. 아침에 추출 시간 한 번 확인 부탁드려요!"),
    (date(2026, 10, 14), "mgr", "토요일 오후 단체 예약(12명) 있습니다. 2시 반부터 창가 쪽 테이블 붙여서 세팅 부탁해요."),
    (date(2026, 10, 13), "s3", "바닐라 시럽 1병 남았습니다. 발주 목록에 추가해두었어요."),
    (date(2026, 10, 12), "s5", "포스기 영수증 용지 교체했어요. 여분은 카운터 아래 서랍 두 번째 칸에 있습니다."),
    (date(2026, 10, 11), "minor", "오늘 손님이 텀블러 두고 가셨어요. 분실물 바구니에 넣어두었습니다 (검정색 스탠리)."),
    (date(2026, 10, 10), "s2", "오후에 제빙기 물 새는 소리가 나서 사장님께 말씀드렸어요. 월요일 AS 기사님 오신대요."),
    (date(2026, 10, 9), "s1", "신메뉴 '흑임자 라떼' 레시피 매뉴얼에 올라갔습니다. 시럽 20g 꼭 계량해주세요."),
    (date(2026, 10, 8), "s3", "마감 때 냉장고 온도 6도로 나왔어요. 다이얼 한 칸 내려두었습니다."),
    (date(2026, 10, 7), "mgr", "이번 주부터 마감조는 21:40에 라스트오더 안내 부탁드려요."),
]
insert("handover_notes", ["store_id", "author_id", "work_date", "content", "created_at", "updated_at"],
       [("@store", f"@m_{k}", d, c, dt(d, time(17, 50)), dt(d, time(17, 50))) for d, k, c in notes])

# ---------------------------------------------------------------- 공지
notices = [
    ("owner", datetime(2026, 10, 13, 9, 30), "10월 급여 지급 안내",
     "9월 근무분 급여는 10월 10일에 정상 지급되었습니다.\n앱의 급여명세서에서 상세 내역 확인해 주세요. 문의는 점장님께 부탁드립니다."),
    ("mgr", datetime(2026, 10, 9, 20, 10), "신메뉴 흑임자 라떼 출시 (10/10~)",
     "10월 10일부터 흑임자 라떼가 판매됩니다.\n레시피는 매뉴얼 > 음료 카테고리에 있습니다. 출근 전에 꼭 한 번 읽어주세요!"),
    ("owner", datetime(2026, 9, 28, 18, 0), "추석 연휴 영업시간 변경",
     "10/3(토)~10/5(월)은 10:00~20:00 단축 영업합니다.\n해당 일자 스케줄은 이미 조정해 두었으니 앱에서 확인 바랍니다."),
    ("mgr", datetime(2026, 9, 15, 14, 0), "위생 점검 대비 체크리스트 강화",
     "다음 달 구청 위생 점검이 예정되어 있어 마감 체크리스트에 냉장고 온도 기록 항목이 추가되었습니다."),
    ("owner", datetime(2026, 8, 20, 10, 0), "근무 교대 요청은 앱으로 남겨주세요",
     "카톡으로 교대 요청하시면 누락이 생겨서, 앞으로는 인수인계 메모나 점장님께 직접 말씀 부탁드립니다."),
]
active = [k for k, *_, st in members if st == "ACTIVE"]
for idx, (k, at, title, content) in enumerate(notices):
    insert("notices", ["store_id", "author_id", "title", "content", "created_at", "updated_at"],
           [("@store", f"@m_{k}", title, content, at, at)])
    w(f"SET @n_{idx} = LAST_INSERT_ID();")
    readers = [m for m in active if m != k and (idx > 0 or random.random() < 0.6)]
    insert("notice_reads", ["notice_id", "store_member_id", "read_at"],
           [(f"@n_{idx}", f"@m_{m}", at + timedelta(hours=random.randint(1, 30), minutes=random.randint(0, 59)))
            for m in readers])

# ---------------------------------------------------------------- 매뉴얼
manuals = [
    ("음료", "아메리카노 / 라떼 기본 레시피",
     "[아메리카노 HOT]\n- 원두 18g, 투샷 추출(36ml, 25~30초)\n- 뜨거운 물 260ml\n\n[카페라떼 HOT]\n- 투샷 + 스팀 우유 220ml\n- 우유 온도 60~65℃, 거품 1cm"),
    ("음료", "흑임자 라떼 (신메뉴)",
     "- 흑임자 페이스트 20g + 투샷을 먼저 섞기\n- 스팀 우유 200ml\n- 토핑: 흑임자 파우더 0.5g\n- 아이스: 우유 180ml + 얼음 1컵"),
    ("오픈/마감", "에스프레소 머신 백플러시 방법",
     "1. 블라인드 필터 장착 후 세정제 1스푼\n2. 10초 추출 / 10초 정지 5회 반복\n3. 세정제 없이 같은 방법으로 5회 헹굼\n4. 그룹 헤드 개스킷 솔로 닦기"),
    ("고객응대", "컴플레인 응대 가이드",
     "1. 먼저 사과하고 불편 사항 끝까지 듣기\n2. 음료 문제는 즉시 재제조\n3. 해결이 어려우면 점장 연락 (010-7341-2082)\n4. 처리 내용은 인수인계 메모에 기록"),
    ("안전", "화재·응급 상황 대응",
     "- 소화기 위치: 카운터 옆, 주방 출입구\n- 화재 시 119 신고 → 고객 대피 유도 → 사장님 연락\n- 화상: 흐르는 찬물 15분 이상, 구급상자는 창고 선반"),
]
insert("manuals", ["store_id", "author_id", "category", "title", "content", "display_order", "created_at", "updated_at"],
       [("@store", "@m_mgr" if i % 2 else "@m_owner", c, t, body, i, datetime(2026, 8, 1) + timedelta(days=i * 9),
         datetime(2026, 8, 1) + timedelta(days=i * 9)) for i, (c, t, body) in enumerate(manuals)])

# ---------------------------------------------------------------- 메뉴 원가
# 이름, 분류, 단위, 포장량, 가격, 로스율, 제품정보
ingredients = [
    ("원두 (하우스 블렌드)", "원두", "G", 1000, 28000, 3, "브라질·콜롬비아 블렌드 1kg"),
    ("디카페인 원두", "원두", "G", 500, 19000, 3, "콜롬비아 디카페인 500g"),
    ("우유", "유제품", "ML", 1000, 2600, 2, "서울우유 1L"),
    ("오트밀크", "유제품", "ML", 1000, 4500, 2, "오틀리 바리스타 1L"),
    ("바닐라 시럽", "시럽", "ML", 1000, 13000, 0, "모닌 바닐라 1L"),
    ("카라멜 소스", "시럽", "G", 1890, 18000, 0, "1.89kg"),
    ("흑임자 페이스트", "베이스", "G", 1000, 24000, 0, "국산 흑임자 1kg"),
    ("말차 파우더", "베이스", "G", 500, 22000, 0, "제주 말차 500g"),
    ("얼음", "기타", "G", 3000, 3500, 5, "3kg"),
    ("일회용 컵 (16oz)", "포장", "EA", 1000, 52000, 0, "PET 16oz"),
]
for idx, (n, cat, unit, pq, price, loss, info) in enumerate(ingredients):
    insert("menu_ingredients", ["store_id", "name", "category", "unit", "package_qty", "price", "loss_rate",
                                "product_info", "created_at", "updated_at"],
           [("@store", n, cat, unit, pq, price, loss, info, store_created, store_created)])
    w(f"SET @ing_{idx} = LAST_INSERT_ID();")
menus = [
    ("아메리카노", "커피", 4500, [(0, 18), (8, 200), (9, 1)]),
    ("카페라떼", "커피", 5000, [(0, 18), (2, 220), (9, 1)]),
    ("오트 라떼", "커피", 5500, [(0, 18), (3, 220), (9, 1)]),
    ("바닐라 라떼", "커피", 5500, [(0, 18), (2, 200), (4, 20), (9, 1)]),
    ("카라멜 마키아토", "커피", 5800, [(0, 18), (2, 200), (5, 25), (9, 1)]),
    ("디카페인 아메리카노", "커피", 5000, [(1, 18), (8, 200), (9, 1)]),
    ("흑임자 라떼", "시그니처", 6300, [(0, 18), (6, 20), (2, 200), (9, 1)]),
    ("말차 라떼", "논커피", 5800, [(7, 6), (2, 220), (9, 1)]),
]
for idx, (n, cat, price, recipe) in enumerate(menus):
    insert("store_menus", ["store_id", "name", "category", "selling_price", "created_at", "updated_at"],
           [("@store", n, cat, price, store_created, store_created)])
    w(f"SET @menu_{idx} = LAST_INSERT_ID();")
    insert("menu_recipe_items", ["menu_id", "ingredient_id", "amount"],
           [(f"@menu_{idx}", f"@ing_{i}", a) for i, a in recipe])

# ---------------------------------------------------------------- 거래처
suppliers = [
    ("빈브라더스 로스터리", "원두", "02-3141-7700", "https://example.com/beans",
     "매주 월·목 오전 배송. 전날 18시까지 주문", [
         ("하우스 블렌드", "1kg", None, {"MONDAY": "4", "THURSDAY": "3"}),
         ("디카페인", "500g", "재고 2봉 이하일 때만", {"MONDAY": "1"}),
     ]),
    ("성수유업 대리점", "유제품", "010-5512-3390", None, "매일 오전 7시 30분 배송, 일요일 휴무", [
        ("우유 1L", "1L x 12", None, {"MONDAY": "2", "TUESDAY": "2", "WEDNESDAY": "2", "THURSDAY": "2",
                                      "FRIDAY": "3", "SATURDAY": "3"}),
        ("오트밀크", "1L x 12", "주 1회", {"WEDNESDAY": "1"}),
    ]),
    ("카페24마트", "부자재", "1588-0000", "https://example.com/mart", "온라인 주문, 2일 소요", [
        ("16oz PET 컵", "1000개", None, {"MONDAY": "1"}),
        ("빨대 (종이)", "500개", None, {"MONDAY": "1"}),
        ("바닐라 시럽", "1L", "모닌", {"THURSDAY": "2"}),
    ]),
]
for si, (n, cat, phone, url, memo, its) in enumerate(suppliers):
    insert("suppliers", ["store_id", "name", "category", "phone", "site_url", "memo", "display_order",
                         "created_at", "updated_at"],
           [("@store", n, cat, phone, url, memo, si, store_created, store_created)])
    w(f"SET @sup_{si} = LAST_INSERT_ID();")
    for ii, (iname, spec, imemo, qty) in enumerate(its):
        insert("supplier_items", ["supplier_id", "name", "spec", "memo", "display_order", "created_at", "updated_at"],
               [(f"@sup_{si}", iname, spec, imemo, ii, store_created, store_created)])
        w("SET @si = LAST_INSERT_ID();")
        insert("supplier_item_quantities", ["item_id", "day_of_week", "quantity"],
               [("@si", d, v) for d, v in qty.items()])

w("COMMIT;")
w("SELECT @store AS demo_store_id;")
print("\n".join(out))
