-- 이전 결과 대조. 대상 테이블마다 원본에서 와야 할 행 수와 실제로 들어간 행 수를 나란히 보여 준다.
-- migrate.sh는 두 수가 다른 줄이 하나라도 있으면 실패한다. 행 수 외의 값(예: 금액 합계)은 아래 두 번째 쿼리가 본다.
SELECT 'users' AS target_table, (SELECT COUNT(*) FROM `${source}`.users) AS source_rows, (SELECT COUNT(*) FROM ${identity}.users) AS target_rows
UNION ALL
SELECT 'email_tokens' AS target_table, (SELECT COUNT(*) FROM `${source}`.email_tokens) AS source_rows, (SELECT COUNT(*) FROM ${identity}.email_tokens) AS target_rows
UNION ALL
SELECT 'refresh_tokens' AS target_table, (SELECT COUNT(*) FROM `${source}`.refresh_tokens) AS source_rows, (SELECT COUNT(*) FROM ${identity}.refresh_tokens) AS target_rows
UNION ALL
SELECT 'stores' AS target_table, (SELECT COUNT(*) FROM `${source}`.stores) AS source_rows, (SELECT COUNT(*) FROM ${store}.stores) AS target_rows
UNION ALL
SELECT 'store_business_hours' AS target_table, (SELECT COUNT(*) FROM `${source}`.store_business_hours) AS source_rows, (SELECT COUNT(*) FROM ${store}.store_business_hours) AS target_rows
UNION ALL
SELECT 'store_onboarding_steps' AS target_table, (SELECT COUNT(*) FROM `${source}`.store_onboarding_steps) AS source_rows, (SELECT COUNT(*) FROM ${store}.store_onboarding_steps) AS target_rows
UNION ALL
SELECT 'store_members' AS target_table, (SELECT COUNT(*) FROM `${source}`.store_members) AS source_rows, (SELECT COUNT(*) FROM ${store}.store_members) AS target_rows
UNION ALL
SELECT 'join_requests' AS target_table, (SELECT COUNT(*) FROM `${source}`.join_requests) AS source_rows, (SELECT COUNT(*) FROM ${store}.join_requests) AS target_rows
UNION ALL
SELECT 'employment_terms' AS target_table, (SELECT COUNT(*) FROM `${source}`.store_members) AS source_rows, (SELECT COUNT(*) FROM ${workforce}.employment_terms) AS target_rows
UNION ALL
SELECT 'employment_available_days' AS target_table, (SELECT COUNT(*) FROM (SELECT DISTINCT store_member_id, day_of_week FROM `${source}`.store_member_available_days WHERE day_of_week IS NOT NULL) d) AS source_rows, (SELECT COUNT(*) FROM ${workforce}.employment_available_days) AS target_rows
UNION ALL
SELECT 'shifts' AS target_table, (SELECT COUNT(*) FROM `${source}`.shifts) AS source_rows, (SELECT COUNT(*) FROM ${workforce}.shifts) AS target_rows
UNION ALL
SELECT 'shift_templates' AS target_table, (SELECT COUNT(*) FROM `${source}`.shift_templates) AS source_rows, (SELECT COUNT(*) FROM ${workforce}.shift_templates) AS target_rows
UNION ALL
SELECT 'attendances' AS target_table, (SELECT COUNT(*) FROM `${source}`.attendances) AS source_rows, (SELECT COUNT(*) FROM ${workforce}.attendances) AS target_rows
UNION ALL
SELECT 'leave_usages' AS target_table, (SELECT COUNT(*) FROM `${source}`.leave_usages) AS source_rows, (SELECT COUNT(*) FROM ${workforce}.leave_usages) AS target_rows
UNION ALL
SELECT 'payrolls' AS target_table, (SELECT COUNT(*) FROM `${source}`.payrolls) AS source_rows, (SELECT COUNT(*) FROM ${workforce}.payrolls) AS target_rows
UNION ALL
SELECT 'checklist_items' AS target_table, (SELECT COUNT(*) FROM `${source}`.checklist_items) AS source_rows, (SELECT COUNT(*) FROM ${operations}.checklist_items) AS target_rows
UNION ALL
SELECT 'checklist_completions' AS target_table, (SELECT COUNT(*) FROM `${source}`.checklist_completions) AS source_rows, (SELECT COUNT(*) FROM ${operations}.checklist_completions) AS target_rows
UNION ALL
SELECT 'notices' AS target_table, (SELECT COUNT(*) FROM `${source}`.notices) AS source_rows, (SELECT COUNT(*) FROM ${operations}.notices) AS target_rows
UNION ALL
SELECT 'notice_reads' AS target_table, (SELECT COUNT(*) FROM `${source}`.notice_reads) AS source_rows, (SELECT COUNT(*) FROM ${operations}.notice_reads) AS target_rows
UNION ALL
SELECT 'handover_notes' AS target_table, (SELECT COUNT(*) FROM `${source}`.handover_notes) AS source_rows, (SELECT COUNT(*) FROM ${operations}.handover_notes) AS target_rows
UNION ALL
SELECT 'manuals' AS target_table, (SELECT COUNT(*) FROM `${source}`.manuals) AS source_rows, (SELECT COUNT(*) FROM ${operations}.manuals) AS target_rows
UNION ALL
SELECT 'manual_images' AS target_table, (SELECT COUNT(*) FROM `${source}`.manual_images) AS source_rows, (SELECT COUNT(*) FROM ${operations}.manual_images) AS target_rows
UNION ALL
SELECT 'suppliers' AS target_table, (SELECT COUNT(*) FROM `${source}`.suppliers) AS source_rows, (SELECT COUNT(*) FROM ${operations}.suppliers) AS target_rows
UNION ALL
SELECT 'supplier_items' AS target_table, (SELECT COUNT(*) FROM `${source}`.supplier_items) AS source_rows, (SELECT COUNT(*) FROM ${operations}.supplier_items) AS target_rows
UNION ALL
SELECT 'supplier_item_quantities' AS target_table, (SELECT COUNT(*) FROM `${source}`.supplier_item_quantities) AS source_rows, (SELECT COUNT(*) FROM ${operations}.supplier_item_quantities) AS target_rows
UNION ALL
SELECT 'menu_ingredients' AS target_table, (SELECT COUNT(*) FROM `${source}`.menu_ingredients) AS source_rows, (SELECT COUNT(*) FROM ${operations}.menu_ingredients) AS target_rows
UNION ALL
SELECT 'store_menus' AS target_table, (SELECT COUNT(*) FROM `${source}`.store_menus) AS source_rows, (SELECT COUNT(*) FROM ${operations}.store_menus) AS target_rows
UNION ALL
SELECT 'menu_recipe_items' AS target_table, (SELECT COUNT(*) FROM `${source}`.menu_recipe_items) AS source_rows, (SELECT COUNT(*) FROM ${operations}.menu_recipe_items) AS target_rows
UNION ALL
SELECT 'labor_qa_sessions' AS target_table, (SELECT COUNT(*) FROM `${source}`.labor_qa_sessions) AS source_rows, (SELECT COUNT(*) FROM ${laborqa}.labor_qa_sessions) AS target_rows
UNION ALL
SELECT 'labor_qa_messages' AS target_table, (SELECT COUNT(*) FROM `${source}`.labor_qa_messages) AS source_rows, (SELECT COUNT(*) FROM ${laborqa}.labor_qa_messages) AS target_rows;

-- 값 대조: 돈과 시간처럼 틀리면 바로 피해가 나는 열의 합계. 두 열이 같아야 한다.
SELECT 'hourly_wage_sum' AS check_name,
       (SELECT COALESCE(SUM(hourly_wage), 0) FROM `${source}`.store_members) AS source_value,
       (SELECT COALESCE(SUM(hourly_wage), 0) FROM ${workforce}.employment_terms) AS target_value
UNION ALL
SELECT 'payroll_net_pay_sum',
       (SELECT COALESCE(SUM(net_pay), 0) FROM `${source}`.payrolls),
       (SELECT COALESCE(SUM(net_pay), 0) FROM ${workforce}.payrolls)
UNION ALL
SELECT 'attendance_minutes_sum',
       (SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, clock_in_at, COALESCE(clock_out_at, clock_in_at))), 0)
        FROM `${source}`.attendances),
       (SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, clock_in_at, COALESCE(clock_out_at, clock_in_at))), 0)
        FROM ${workforce}.attendances)
UNION ALL
SELECT 'working_attendances',
       (SELECT COUNT(*) FROM `${source}`.attendances WHERE status = 'WORKING'),
       (SELECT COUNT(*) FROM ${workforce}.attendances WHERE status = 'WORKING')
UNION ALL
SELECT 'recipe_amount_sum',
       (SELECT COALESCE(ROUND(SUM(amount), 4), 0) FROM `${source}`.menu_recipe_items),
       (SELECT COALESCE(ROUND(SUM(amount), 4), 0) FROM ${operations}.menu_recipe_items);
