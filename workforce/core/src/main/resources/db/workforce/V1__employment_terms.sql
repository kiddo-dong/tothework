-- 고용 조건. 기존 al-bam.store_members에 함께 있던 시급·공제 방식·주휴일·근무 가능 요일을 옮겨 온 것이다.
-- member_id는 store의 store_members.id와 같은 값이지만, 다른 서비스의 테이블이라 외래키는 걸지 않는다.
-- 스케줄 저장 전에 멤버 단위로 쓰기를 줄 세울 때 이 행을 잠근다 (예전에는 store의 행을 잠갔다).

CREATE TABLE employment_terms (
  member_id          BIGINT      NOT NULL,
  store_id           BIGINT      NOT NULL,
  hourly_wage        INT         NOT NULL,
  tax_mode           ENUM ('FOUR_INSURANCES', 'NONE', 'WITHHOLDING_3_3') NOT NULL,
  weekly_holiday_day ENUM ('FRIDAY', 'MONDAY', 'SATURDAY', 'SUNDAY', 'THURSDAY', 'TUESDAY', 'WEDNESDAY') NULL,
  version            BIGINT      NOT NULL DEFAULT 0,
  created_at         DATETIME(6) NULL,
  updated_at         DATETIME(6) NULL,
  PRIMARY KEY (member_id),
  KEY idx_employment_terms_store_id (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE employment_available_days (
  member_id   BIGINT NOT NULL,
  day_of_week ENUM ('FRIDAY', 'MONDAY', 'SATURDAY', 'SUNDAY', 'THURSDAY', 'TUESDAY', 'WEDNESDAY') NOT NULL,
  PRIMARY KEY (member_id, day_of_week),
  CONSTRAINT fk_employment_available_days_terms FOREIGN KEY (member_id) REFERENCES employment_terms (member_id)
    ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
