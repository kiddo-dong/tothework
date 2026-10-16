-- 근로 상담 대화. 컬럼 구성은 기존 백엔드(al-bam.labor_qa_*)와 같아 데이터를 그대로 옮길 수 있다.
-- 차이는 사용자를 id로만 기억한다는 것. users 테이블은 다른 서비스(identity)의 것이라 외래키를 걸지 않는다.
-- 메시지 → 세션 외래키는 같은 서비스 안이라 유지한다.

CREATE TABLE labor_qa_sessions (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  title      VARCHAR(30) NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_labor_qa_sessions_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE labor_qa_messages (
  id         BIGINT                     NOT NULL AUTO_INCREMENT,
  session_id BIGINT                     NOT NULL,
  role       ENUM ('ASSISTANT', 'USER') NOT NULL,
  content    TEXT                       NOT NULL,
  sources    TEXT                       NULL,
  created_at DATETIME(6)                NULL,
  updated_at DATETIME(6)                NULL,
  PRIMARY KEY (id),
  KEY idx_labor_qa_messages_session_id (session_id),
  CONSTRAINT fk_labor_qa_messages_session FOREIGN KEY (session_id) REFERENCES labor_qa_sessions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
