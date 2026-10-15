-- 데모 데이터 전체 삭제. 매장 행을 지우면 V6의 ON DELETE CASCADE로 하위 데이터가 함께 지워진다.
-- 사용자 쪽 테이블(토큰·AI 상담·가입신청)은 users에 CASCADE가 없어 먼저 지운다.
START TRANSACTION;
DELETE FROM stores WHERE invite_code = 'ONGI88';
DELETE jr FROM join_requests jr JOIN users u ON u.id = jr.user_id WHERE u.email LIKE '%@demo.tothework.com';
DELETE rt FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id WHERE u.email LIKE '%@demo.tothework.com';
DELETE et FROM email_tokens et JOIN users u ON u.id = et.user_id WHERE u.email LIKE '%@demo.tothework.com';
DELETE m FROM labor_qa_messages m JOIN labor_qa_sessions s ON s.id = m.session_id JOIN users u ON u.id = s.user_id WHERE u.email LIKE '%@demo.tothework.com';
DELETE s FROM labor_qa_sessions s JOIN users u ON u.id = s.user_id WHERE u.email LIKE '%@demo.tothework.com';
DELETE FROM users WHERE email LIKE '%@demo.tothework.com';
COMMIT;
