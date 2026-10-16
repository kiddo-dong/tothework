-- 모듈 간 이벤트의 발행 기록 (Spring Modulith Event Publication Registry, JDBC v2 스키마).
--
-- 다른 서비스의 이벤트를 받는 리스너는 비동기로 돈다. 리스너가 끝내기 전에 앱이 죽거나 실패하면 이 표에
-- 미완료로 남고, 재시작할 때 다시 보낸다. 이벤트가 사라지지 않게 하는 아웃박스 역할이다.
-- 스키마는 Spring Modulith가 배포하는 schema-mysql.sql(v2)과 같다. 직접 관리하려고 Flyway로 만든다.

CREATE TABLE IF NOT EXISTS EVENT_PUBLICATION
(
  ID                     VARCHAR(36)   NOT NULL,
  LISTENER_ID            VARCHAR(512)  NOT NULL,
  EVENT_TYPE             VARCHAR(512)  NOT NULL,
  SERIALIZED_EVENT       VARCHAR(4000) NOT NULL,
  PUBLICATION_DATE       TIMESTAMP(6)  NOT NULL,
  COMPLETION_DATE        TIMESTAMP(6)  DEFAULT NULL NULL,
  STATUS                 VARCHAR(20),
  COMPLETION_ATTEMPTS    INT,
  LAST_RESUBMISSION_DATE TIMESTAMP(6)  DEFAULT NULL NULL,
  PRIMARY KEY (ID),
  INDEX EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX (COMPLETION_DATE)
);
