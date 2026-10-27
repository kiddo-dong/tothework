"""컨트롤러 소스를 파싱해 Postman 컬렉션(v2.1)을 만든다. 사용: python3 docs/postman/generate_collection.py . docs/postman/tothework.postman_collection.json"""
import json
import re
import subprocess
import sys
from pathlib import Path

repo = Path(sys.argv[1])
out = Path(sys.argv[2])

files = [repo / f for f in subprocess.run(["git", "ls-files", "*.java"], cwd=repo, capture_output=True,
                                          text=True, check=True).stdout.split() if "/src/main/" in f]
sources = {f: f.read_text(encoding="utf-8") for f in files}

# ---------- 레코드(요청 본문)와 enum 수집 ----------
records = {}   # 이름 -> [(타입, 필드명)]
enums = {}     # 이름 -> [상수]


def split_top(s, sep=","):
    parts, depth, cur = [], 0, ""
    for ch in s:
        if ch in "<([{":
            depth += 1
        elif ch in ">)]}":
            depth -= 1
        if ch == sep and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        parts.append(cur)
    return parts


def strip_comments(s):
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
    return re.sub(r"//[^\n]*", "", s)


for f, src in sources.items():
    clean = strip_comments(src)
    for m in re.finditer(r"\brecord\s+(\w+)\s*\(", clean):
        name, i, depth = m.group(1), m.end(), 1
        j = i
        while depth:
            depth += {"(": 1, ")": -1}.get(clean[j], 0)
            j += 1
        header = clean[i:j - 1]
        fields = []
        for part in split_top(header):
            part = re.sub(r"@\w+(\.\w+)*(\((?:[^()]|\([^()]*\))*\))?", "", part).strip()
            if not part:
                continue
            tokens = part.rsplit(None, 1)
            if len(tokens) == 2:
                fields.append((tokens[0].strip(), tokens[1].strip()))
        records.setdefault(name, fields)
    for m in re.finditer(r"\benum\s+(\w+)\s*\{([^;}]*)", clean):
        consts = [c.strip().split("(")[0] for c in m.group(2).split(",") if c.strip()]
        enums.setdefault(m.group(1), [c for c in consts if re.match(r"^[A-Z_0-9]+$", c)])
enums["DayOfWeek"] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]

# 필드 이름별로 현실적인 예시 값. 없으면 타입으로 만든다.
FIELD_SAMPLES = {
    "email": "owner@example.com", "password": "Test1234!", "passwordConfirm": "Test1234!",
    "currentPassword": "Test1234!", "newPassword": "NewPass1234!", "newPasswordConfirm": "NewPass1234!",
    "name": "홍길동", "phone": "010-1234-5678", "birthDate": "1995-03-01", "agreedToTerms": True,
    "accessToken": "<소셜 제공자가 준 access token>", "token": "<메일로 받은 토큰>",
    "code": "<초대코드 6자리>", "confirmName": "알밤카페 마포점", "memberName": "홍길동",
    "address": "서울시 마포구 월드컵로 1", "businessRegistrationNumber": "123-45-67890",
    "payday": 10, "smallBusiness": True, "title": "홀팀장", "hourlyWage": 10320,
    "content": "내용을 입력하세요", "question": "주휴수당은 언제 받을 수 있나요?",
    "requirement": "주말에는 2명 이상 배정해 주세요", "category": "기본", "spec": "1L x 12개입",
    "memo": "메모", "siteUrl": "https://example.com", "productInfo": "서울우유 1L",
    "price": 2800, "packageQty": 1000, "lossRate": 5, "sellingPrice": 4500, "amount": 200,
    "displayOrder": 0, "breakMinutes": 30,
    "workDate": "{{date}}", "leaveDate": "{{date}}", "periodStart": "{{from}}", "periodEnd": "{{to}}",
    "startTime": "09:00", "endTime": "15:00", "openTime": "09:00", "closeTime": "22:00",
    "clockInAt": "{{date}}T09:00:00", "clockOutAt": "{{date}}T18:00:00",
    "storeMemberId": "{{memberId}}", "targetMemberId": "{{memberId}}", "ingredientId": "{{ingredientId}}",
    "imageKeys": [], "closed": False,
}
NAME_OVERRIDES = {  # 레코드별로 이름이 겹치는 필드
    ("CreateStoreRequest", "name"): "알밤카페 마포점", ("UpdateStoreRequest", "name"): "알밤카페 마포점",
    ("ShiftTemplateRequest", "name"): "오픈", ("ChecklistItemRequest", "content"): "커피머신 예열",
    ("CreateNoticeRequest", "title"): "이번 주 공지", ("ManualRequest", "title"): "에스프레소 추출법",
    ("SupplierRequest", "name"): "서울우유 대리점", ("SupplierItemRequest", "name"): "우유",
    ("MenuIngredientRequest", "name"): "우유", ("MenuRequest", "name"): "카페라떼",
    ("CreateHandoverNoteRequest", "content"): "우유 2팩 남음, 6시 예약 있음",
    ("UpdateStoreMemberRequest", "role"): "MANAGER",
}


ENUM_PREFERENCE = {"StoreCategory": "CAFE", "MemberRole": "STAFF", "IngredientUnit": "ML", "TaxMode": "NONE",
                   "ShiftStatus": "SCHEDULED", "ChecklistType": "OPEN", "BreakPolicy": "STATUTORY",
                   "MemberStatus": "ACTIVE"}


def sample(type_, field="", owner=""):
    type_ = type_.strip()
    if (owner, field) in NAME_OVERRIDES:
        return NAME_OVERRIDES[(owner, field)]
    simple_name = type_.split(".")[-1]
    if simple_name in enums:
        return ENUM_PREFERENCE.get(simple_name, enums[simple_name][0])
    if field in FIELD_SAMPLES and not type_.startswith(("List", "Set", "Map")) or field == "imageKeys":
        return FIELD_SAMPLES[field]
    gen = re.match(r"(\w+(?:\.\w+)*)\s*<(.*)>$", type_)
    if gen:
        base, args = gen.group(1).split(".")[-1], split_top(gen.group(2))
        if base in ("List", "Set"):
            if args[0].strip() == "DayOfWeek":
                return ["MONDAY", "WEDNESDAY", "FRIDAY"]
            return [sample(args[0], field, owner)]
        if base == "Map":
            key, value = args[0].strip(), args[1].strip()
            if key == "DayOfWeek":
                if value == "String":
                    return {"MONDAY": "10개", "THURSDAY": "2박스"}
                days = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
                return {d: sample(value, "", owner) for d in days}
            return {}
    simple = type_.split(".")[-1]
    if simple in ("String",):
        return FIELD_SAMPLES.get(field, "문자열")
    if simple in ("int", "Integer", "long", "Long", "double", "Double"):
        return FIELD_SAMPLES.get(field, 1)
    if simple in ("boolean", "Boolean"):
        return FIELD_SAMPLES.get(field, True)
    if simple == "LocalDate":
        return "{{date}}"
    if simple == "LocalTime":
        return "09:00"
    if simple == "LocalDateTime":
        return "{{date}}T09:00:00"
    if simple in enums:
        return enums[simple][0]
    if simple in records:
        return {fname: sample(ftype, fname, simple) for ftype, fname in records[simple]}
    return None


# ---------- 컨트롤러 파싱 ----------
FOLDERS = [  # 컨트롤러 → (폴더, 순서)
    ("AuthController", "01 인증"), ("UserController", "02 내 정보"), ("StoreController", "03 매장"),
    ("StoreOnboardingController", "04 온보딩"), ("JoinRequestController", "05 참여 신청"),
    ("StoreMemberController", "06 구성원 (store)"), ("MemberController", "06 구성원 (bff)"),
    ("ShiftController", "07 스케줄"), ("ScheduleAiController", "08 AI 스케줄 초안"),
    ("ShiftTemplateController", "09 근무유형 템플릿"), ("AttendanceController", "10 근태"),
    ("LeaveController", "11 연차"), ("PayrollController", "12 급여"), ("DashboardController", "13 대시보드"),
    ("HomeController", "14 홈"), ("ChecklistController", "15 체크리스트"), ("NoticeController", "16 공지"),
    ("HandoverNoteController", "17 인수인계"), ("ManualController", "18 매뉴얼"),
    ("SupplierController", "19 거래처·발주"), ("MenuCostController", "20 원가 (재료·메뉴)"),
    ("LaborQaController", "21 AI 근로 상담"), ("LaborQaAdminController", "22 지식베이스 (관리자)"),
]
folder_of = dict(FOLDERS)

# 같은 이름의 경로 변수가 컨트롤러마다 다른 것을 가리키는 경우
PATH_VAR_ALIASES = {("ChecklistController", "itemId"): "checklistItemId",
                    ("SupplierController", "itemId"): "supplierItemId"}

QUERY_SAMPLES = {"from": "{{from}}", "to": "{{to}}", "date": "{{date}}", "year": "{{year}}",
                 "month": "{{month}}", "memberId": "{{memberId}}", "storeMemberId": "{{memberId}}",
                 "confirmName": "알밤카페 마포점", "token": "<메일로 받은 토큰>"}

# 응답의 data.id를 다음 요청에 쓰도록 컬렉션 변수에 저장한다
SAVE_ID = {
    ("POST", "/api/v1/stores"): "storeId", ("POST", "/api/v1/join-requests"): "requestId",
    ("POST", "/api/v1/stores/{storeId}/shifts"): "shiftId",
    ("POST", "/api/v1/stores/{storeId}/shift-templates"): "templateId",
    ("POST", "/api/v1/stores/{storeId}/attendance"): "attendanceId",
    ("POST", "/api/v1/stores/{storeId}/members/{memberId}/leaves"): "leaveId",
    ("POST", "/api/v1/stores/{storeId}/checklist-items"): "checklistItemId",
    ("POST", "/api/v1/stores/{storeId}/notices"): "noticeId",
    ("POST", "/api/v1/stores/{storeId}/handover-notes"): "noteId",
    ("POST", "/api/v1/stores/{storeId}/manuals"): "manualId",
    ("POST", "/api/v1/stores/{storeId}/suppliers"): "supplierId",
    ("POST", "/api/v1/stores/{storeId}/suppliers/{supplierId}/items"): "supplierItemId",
    ("POST", "/api/v1/stores/{storeId}/menu-ingredients"): "ingredientId",
    ("POST", "/api/v1/stores/{storeId}/menus"): "menuId",
    ("POST", "/api/v1/labor-qa/sessions"): "sessionId",
}

items = {}
count = 0
for f, src in sources.items():
    if not f.name.endswith("Controller.java"):
        continue
    cls = f.stem
    cm = re.search(r"@RequestMapping\(\"([^\"]*)\"\)\s*(?:@\w+\s*)*(?:public\s+)?class", src)
    base = cm.group(1) if cm else ""
    for m in re.finditer(r"@(Get|Post|Patch|Put|Delete)Mapping(?:\(\"([^\"]*)\"\))?", src):
        method, sub = m.group(1).upper(), m.group(2) or ""
        # 이 매핑 앞의 Javadoc (매핑과 그 사이 애너테이션만 있을 때)
        before = src[:m.start()]
        doc = ""
        dm = re.search(r"/\*\*(.*?)\*/\s*(?:@[^\n]*\n\s*)*$", before, flags=re.S)
        if dm:
            doc = re.sub(r"\s*\n\s*\*\s?", " ", dm.group(1)).strip(" *")
            doc = re.sub(r"\{@\w+ ([^}]*)\}", r"\1", doc)
        rest = src[m.end():]
        sig = rest[:rest.index("{")]
        name = re.search(r"(\w+)\s*\(", sig).group(1)
        params = sig[sig.index("(") + 1: sig.rindex(")")]
        path = base + sub
        count += 1

        query, headers, body, form = [], [], None, None
        for p in split_top(params):
            p = " ".join(p.split())
            if "@RequestParam" in p:
                pm = re.search(r"@RequestParam(?:\((.*?)\))?\s+(?:@\S+(?:\([^)]*\))?\s+)*([\w<>.]+)\s+(\w+)$", p)
                opts, ptype, pname = pm.group(1) or "", pm.group(2), pm.group(3)
                nm = re.search(r"\"(\w+)\"", opts)
                qname = nm.group(1) if nm else pname
                if ptype == "MultipartFile":
                    form = qname
                    continue
                query.append({"key": qname, "value": QUERY_SAMPLES.get(qname, ""),
                              "disabled": "required = false" in opts,
                              "description": "선택" if "required = false" in opts else "필수"})
            elif "@RequestHeader" in p:
                headers.append({"key": re.search(r"\"([^\"]+)\"", p).group(1), "value": "{{adminToken}}"})
            elif "@RequestBody" in p:
                btype = re.search(r"@RequestBody\s+([\w<>.]+)\s+\w+$", p).group(1)
                body = sample(btype)

        url_path = path
        for var in re.findall(r"\{(\w+)\}", path):
            alias = PATH_VAR_ALIASES.get((cls, var), var)
            url_path = url_path.replace("{" + var + "}", "{{" + alias + "}}")
        raw = "{{baseUrl}}" + url_path
        if query:
            raw += "?" + "&".join(f"{q['key']}={q['value']}" for q in query if not q["disabled"])
        request = {
            "method": method,
            "header": headers,
            "url": {"raw": raw, "host": ["{{baseUrl}}"], "path": [s for s in url_path.split("/") if s],
                    "query": query},
            "description": doc,
        }
        if path.startswith("/api/v1/auth") or path.startswith("/api/v1/labor-qa/admin"):
            request["auth"] = {"type": "noauth"}
        if body is not None:
            request["header"].append({"key": "Content-Type", "value": "application/json"})
            raw_body = json.dumps(body, ensure_ascii=False, indent=2)
            for numeric in ("memberId", "ingredientId"):
                raw_body = raw_body.replace('"{{' + numeric + '}}"', "{{" + numeric + "}}")
            request["body"] = {"mode": "raw", "raw": raw_body,
                               "options": {"raw": {"language": "json"}}}
        if form:
            request["body"] = {"mode": "formdata",
                               "formdata": [{"key": form, "type": "file", "src": []}]}

        events = []
        test_lines = []
        if path in ("/api/v1/auth/login", "/api/v1/auth/refresh") or path.startswith("/api/v1/auth/oauth"):
            test_lines += ["const data = pm.response.json().data;",
                           "if (data && data.accessToken) pm.collectionVariables.set('accessToken', data.accessToken);"]
        if (method, path) in SAVE_ID:
            var = SAVE_ID[(method, path)]
            test_lines += ["const created = pm.response.json().data;",
                           f"if (created && created.id) pm.collectionVariables.set('{var}', created.id);"]
        if (method, path) == ("GET", "/api/v1/stores"):
            test_lines += ["const stores = pm.response.json().data;",
                           "if (stores && stores.length) {",
                           "  pm.collectionVariables.set('storeId', stores[0].store.id);",
                           "  pm.collectionVariables.set('myMemberId', stores[0].myStoreMemberId);",
                           "}"]
        if test_lines:
            events.append({"listen": "test", "script": {"type": "text/javascript", "exec": test_lines}})

        title = f"{method} {path}"
        entry = {"name": title, "request": request}
        if events:
            entry["event"] = events
        items.setdefault(folder_of.get(cls, cls), []).append((src.index(m.group(0), m.start()), entry))

folders = []
for _, folder in sorted({(v, v) for v in folder_of.values()}):
    if folder in items:
        folders.append({"name": folder, "item": [e for _, e in sorted(items[folder], key=lambda x: x[0])]})

collection = {
    "info": {
        "name": "ToTheWork API",
        "description": (
            "컨트롤러 소스에서 생성한 전체 API (" + str(count) + "개).\n\n"
            "사용 순서: 01 인증 > POST /auth/login 을 보내면 accessToken이 자동 저장된다. "
            "03 매장 > POST /stores 나 GET /stores 가 storeId를 저장한다. 생성 API는 응답 id를 해당 변수에 저장한다.\n\n"
            "리프레시 토큰은 HttpOnly 쿠키라 Postman 쿠키 저장소가 /api/v1/auth 경로로 자동으로 보낸다.\n"
            "memberId는 직접 채운다 (GET /stores/{storeId}/members 응답의 id). 내 memberId는 GET /stores가 myMemberId에 저장한다."),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}]},
    "variable": [{"key": k, "value": v} for k, v in [
        ("baseUrl", "http://localhost:8080"), ("accessToken", ""), ("adminToken", ""),
        ("storeId", ""), ("memberId", ""), ("myMemberId", ""), ("requestId", ""),
        ("shiftId", ""), ("templateId", ""), ("attendanceId", ""), ("leaveId", ""),
        ("checklistItemId", ""), ("noticeId", ""), ("noteId", ""), ("manualId", ""),
        ("supplierId", ""), ("supplierItemId", ""), ("ingredientId", ""), ("menuId", ""),
        ("sessionId", ""), ("stepKey", "business-hours"), ("provider", "kakao"),
        ("date", "2026-11-02"), ("from", "2026-11-02"), ("to", "2026-11-08"),
        ("year", "2026"), ("month", "11")]],
    "item": folders,
}
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(collection, ensure_ascii=False, indent=2), encoding="utf-8")
print("endpoints:", count, "folders:", len(folders))
for fo in folders:
    print(f"  {fo['name']}: {len(fo['item'])}")
