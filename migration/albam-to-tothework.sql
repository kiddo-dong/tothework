-- 기존 al-bam DB(한 스키마) → 서비스별 스키마(tw_identity, tw_store, tw_workforce, tw_operations, tw_laborqa).
--
-- 실행 전제
--   * 원본은 al-bam Flyway V11 이상(join_requests.version, store_members.title, attendances.working_member_id가 있음).
--   * 대상 스키마는 새 앱을 한 번 띄워 Flyway가 만든 빈 테이블이다. 앱은 멈춰 둔다.
--   * 직접 실행하지 말고 migration/migrate.sh로 실행한다. ${...}를 실제 스키마 이름으로 바꾸고, 전제를 먼저 확인한다.
--
-- 원칙
--   * id는 그대로 옮긴다. 서비스 사이의 참조(userId, memberId, storeId)가 모두 기존 id이기 때문이다.
--     AUTO_INCREMENT는 InnoDB가 들어간 최대 id 다음으로 알아서 올린다.
--   * 원본은 읽기만 한다.
--   * 한 트랜잭션이다. 중간에 실패하면 mysql 클라이언트가 멈추고 연결이 끊기며 전부 되돌아간다.
--   * 대상의 외래키는 서비스 안에서만 걸려 있다. 넣는 순서를 부모 → 자식으로 맞춰 외래키 검사를 끄지 않는다.

START TRANSACTION;

-- ─── identity ───────────────────────────────────────────────────────────────
-- 리프레시 토큰은 양쪽 모두 원문의 SHA-256 16진수라 그대로 옮기면 로그인이 유지된다.
INSERT INTO ${identity}.users (id, email, password, name, phone, birth_date, terms_agreed_at, profile_image_key,
                               provider, provider_id, email_verified, deleted_at, created_at, updated_at)
SELECT id, email, password, name, phone, birth_date, terms_agreed_at, profile_image_key,
       provider, provider_id, email_verified, deleted_at, created_at, updated_at
FROM `${source}`.users;

INSERT INTO ${identity}.email_tokens (id, user_id, token, type, expires_at, used, created_at, updated_at)
SELECT id, user_id, token, type, expires_at, used, created_at, updated_at
FROM `${source}`.email_tokens;

INSERT INTO ${identity}.refresh_tokens (id, user_id, token_hash, expires_at, created_at, updated_at)
SELECT id, user_id, token_hash, expires_at, created_at, updated_at
FROM `${source}`.refresh_tokens;

-- ─── store ──────────────────────────────────────────────────────────────────
-- 소프트 삭제된 매장도 옮긴다. 유예기간이 끝나면 새 앱의 StorePurgeJob이 지운다.
INSERT INTO ${store}.stores (id, name, address, business_registration_number, category, invite_code, break_policy,
                             small_business, payday, profile_image_key, plan, onboarding_completed_at, deleted_at,
                             created_at, updated_at)
SELECT id, name, address, business_registration_number, category, invite_code, break_policy,
       small_business, payday, profile_image_key, plan, onboarding_completed_at, deleted_at, created_at, updated_at
FROM `${source}`.stores;

INSERT INTO ${store}.store_business_hours (store_id, day_of_week, open_time, close_time, closed)
SELECT store_id, day_of_week, open_time, close_time, closed
FROM `${source}`.store_business_hours;

INSERT INTO ${store}.store_onboarding_steps (store_id, step_key)
SELECT store_id, step_key
FROM `${source}`.store_onboarding_steps;

-- 소속만 store로. 시급·공제·주휴일·근무 가능 요일은 아래 workforce의 고용 조건으로 간다.
INSERT INTO ${store}.store_members (id, store_id, user_id, role, title, status, joined_at, resigned_at, created_at,
                                    updated_at)
SELECT id, store_id, user_id, role, title, status, joined_at, resigned_at, created_at, updated_at
FROM `${source}`.store_members;

INSERT INTO ${store}.join_requests (id, store_id, user_id, status, requested_at, decided_at, decided_role, version,
                                    created_at, updated_at)
SELECT id, store_id, user_id, status, requested_at, decided_at, decided_role, version, created_at, updated_at
FROM `${source}`.join_requests;

-- ─── workforce ──────────────────────────────────────────────────────────────
-- 고용 조건: 소속 한 건마다 하나 (퇴사자 포함 — 지난 급여를 다시 계산할 수 있어야 한다). member_id = 소속 id.
INSERT INTO ${workforce}.employment_terms (member_id, store_id, hourly_wage, tax_mode, weekly_holiday_day, version,
                                          created_at, updated_at)
SELECT id, store_id, hourly_wage, tax_mode, weekly_holiday_day, 0, created_at, updated_at
FROM `${source}`.store_members;

-- 원본 열이 NULL을 허용해 빈 요일 행이 있을 수 있다. 뜻이 없으므로 뺀다.
INSERT INTO ${workforce}.employment_available_days (member_id, day_of_week)
SELECT DISTINCT store_member_id, day_of_week
FROM `${source}`.store_member_available_days
WHERE day_of_week IS NOT NULL;

-- 아래 네 테이블은 store_member_id → member_id로 이름만 바뀌고, 매장별 조회를 위해 store_id를 소속에서 복사한다.
INSERT INTO ${workforce}.shifts (id, member_id, store_id, work_date, start_time, end_time, status, break_minutes,
                                 created_at, updated_at)
SELECT s.id, s.store_member_id, m.store_id, s.work_date, s.start_time, s.end_time, s.status, s.break_minutes,
       s.created_at, s.updated_at
FROM `${source}`.shifts s
JOIN `${source}`.store_members m ON m.id = s.store_member_id;

INSERT INTO ${workforce}.shift_templates (id, store_id, name, start_time, end_time, break_minutes, display_order,
                                          created_at, updated_at)
SELECT id, store_id, name, start_time, end_time, break_minutes, display_order, created_at, updated_at
FROM `${source}`.shift_templates;

INSERT INTO ${workforce}.attendances (id, member_id, store_id, work_date, clock_in_at, clock_out_at, status,
                                      break_minutes, created_at, updated_at)
SELECT a.id, a.store_member_id, m.store_id, a.work_date, a.clock_in_at, a.clock_out_at, a.status, a.break_minutes,
       a.created_at, a.updated_at
FROM `${source}`.attendances a
JOIN `${source}`.store_members m ON m.id = a.store_member_id;

INSERT INTO ${workforce}.leave_usages (id, member_id, store_id, leave_date, created_at, updated_at)
SELECT l.id, l.store_member_id, m.store_id, l.leave_date, l.created_at, l.updated_at
FROM `${source}`.leave_usages l
JOIN `${source}`.store_members m ON m.id = l.store_member_id;

INSERT INTO ${workforce}.payrolls (id, member_id, store_id, target_year, target_month, regular_pay, overtime_pay,
                                   night_pay, holiday_work_pay, weekly_holiday_pay, leave_pay, total_pay, deduction,
                                   net_pay, generated_at, created_at, updated_at)
SELECT p.id, p.store_member_id, m.store_id, p.target_year, p.target_month, p.regular_pay, p.overtime_pay,
       p.night_pay, p.holiday_work_pay, p.weekly_holiday_pay, p.leave_pay, p.total_pay, p.deduction,
       p.net_pay, p.generated_at, p.created_at, p.updated_at
FROM `${source}`.payrolls p
JOIN `${source}`.store_members m ON m.id = p.store_member_id;

-- ─── operations ─────────────────────────────────────────────────────────────
INSERT INTO ${operations}.checklist_items (id, store_id, type, content, display_order, created_at, updated_at)
SELECT id, store_id, type, content, display_order, created_at, updated_at
FROM `${source}`.checklist_items;

-- 체크 기록에 매장을 붙인다(매장·날짜로 한 번에 읽기 위해). checked_by는 소속 id 그대로.
INSERT INTO ${operations}.checklist_completions (id, item_id, store_id, work_date, checked_by, checked_at)
SELECT c.id, c.item_id, i.store_id, c.work_date, c.checked_by, c.checked_at
FROM `${source}`.checklist_completions c
JOIN `${source}`.checklist_items i ON i.id = c.item_id;

INSERT INTO ${operations}.notices (id, store_id, author_id, title, content, created_at, updated_at)
SELECT id, store_id, author_id, title, content, created_at, updated_at
FROM `${source}`.notices;

INSERT INTO ${operations}.notice_reads (id, notice_id, member_id, read_at)
SELECT id, notice_id, store_member_id, read_at
FROM `${source}`.notice_reads;

INSERT INTO ${operations}.handover_notes (id, store_id, author_id, content, work_date, created_at, updated_at)
SELECT id, store_id, author_id, content, work_date, created_at, updated_at
FROM `${source}`.handover_notes;

INSERT INTO ${operations}.manuals (id, store_id, author_id, category, title, content, display_order, created_at,
                                   updated_at)
SELECT id, store_id, author_id, category, title, content, display_order, created_at, updated_at
FROM `${source}`.manuals;

-- 이미지 순서는 0부터 빈틈없이 다시 매긴다. 새 코드는 목록 순번으로 읽으므로 구멍이 있으면 안 된다.
INSERT INTO ${operations}.manual_images (manual_id, sort_order, image_key)
SELECT manual_id, ROW_NUMBER() OVER (PARTITION BY manual_id ORDER BY sort_order) - 1, image_key
FROM `${source}`.manual_images;

INSERT INTO ${operations}.suppliers (id, store_id, name, category, site_url, phone, memo, display_order, created_at,
                                     updated_at)
SELECT id, store_id, name, category, site_url, phone, memo, display_order, created_at, updated_at
FROM `${source}`.suppliers;

INSERT INTO ${operations}.supplier_items (id, supplier_id, name, spec, memo, display_order, created_at, updated_at)
SELECT id, supplier_id, name, spec, memo, display_order, created_at, updated_at
FROM `${source}`.supplier_items;

INSERT INTO ${operations}.supplier_item_quantities (item_id, day_of_week, quantity)
SELECT item_id, day_of_week, quantity
FROM `${source}`.supplier_item_quantities;

INSERT INTO ${operations}.menu_ingredients (id, store_id, name, product_info, price, package_qty, unit, loss_rate,
                                            category, created_at, updated_at)
SELECT id, store_id, name, product_info, price, package_qty, unit, loss_rate, category, created_at, updated_at
FROM `${source}`.menu_ingredients;

INSERT INTO ${operations}.store_menus (id, store_id, name, selling_price, category, created_at, updated_at)
SELECT id, store_id, name, selling_price, category, created_at, updated_at
FROM `${source}`.store_menus;

-- 레시피 줄은 이제 메뉴의 순서 있는 자식이라 id 대신 line_order를 둔다. 예전 화면 순서(id 순)를 그대로 옮긴다.
INSERT INTO ${operations}.menu_recipe_items (menu_id, line_order, ingredient_id, amount)
SELECT menu_id, ROW_NUMBER() OVER (PARTITION BY menu_id ORDER BY id) - 1, ingredient_id, amount
FROM `${source}`.menu_recipe_items;

-- ─── laborqa ────────────────────────────────────────────────────────────────
-- 노무 지식베이스(pgvector)는 별도 DB이고 새 앱도 같은 곳을 쓰므로 옮길 것이 없다.
INSERT INTO ${laborqa}.labor_qa_sessions (id, user_id, title, created_at, updated_at)
SELECT id, user_id, title, created_at, updated_at
FROM `${source}`.labor_qa_sessions;

INSERT INTO ${laborqa}.labor_qa_messages (id, session_id, role, content, sources, created_at, updated_at)
SELECT id, session_id, role, content, sources, created_at, updated_at
FROM `${source}`.labor_qa_messages;

COMMIT;
