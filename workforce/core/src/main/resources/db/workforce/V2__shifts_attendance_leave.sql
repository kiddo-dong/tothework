-- 스케줄·근무 유형 템플릿·근태·연차. 컬럼은 기존 al-bam 테이블과 같고 두 가지가 다르다.
--
-- 1. store_member_id 대신 member_id. store의 소속 id와 같은 값이지만 다른 서비스의 테이블이라 외래키는 없다.
-- 2. store_id를 둔다. 예전에는 store_members와 JOIN해 매장별로 걸렀는데, 서비스가 나뉘면 그 JOIN은 불가능하다.
--    멤버의 소속 매장은 바뀌지 않으므로(다른 매장에 가면 새 member_id) 복사해 둬도 어긋나지 않는다.

CREATE TABLE shifts (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  member_id     BIGINT      NOT NULL,
  store_id      BIGINT      NOT NULL,
  work_date     DATE        NOT NULL,
  start_time    TIME        NOT NULL,
  end_time      TIME        NOT NULL,
  status        ENUM ('CANCELED', 'CONFIRMED', 'SCHEDULED') NOT NULL,
  break_minutes INT         NOT NULL,
  created_at    DATETIME(6) NULL,
  updated_at    DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_shifts_member_work_date (member_id, work_date),
  KEY idx_shifts_store_work_date (store_id, work_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE shift_templates (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  store_id      BIGINT       NOT NULL,
  name          VARCHAR(255) NOT NULL,
  start_time    TIME         NOT NULL,
  end_time      TIME         NOT NULL,
  break_minutes INT          NULL,
  display_order INT          NOT NULL,
  created_at    DATETIME(6)  NULL,
  updated_at    DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shift_templates_store_name (store_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 근무 중(WORKING)인 기록은 멤버당 하나. 앞의 조회가 막지 못하는 동시 출근을 DB가 막는다.
CREATE TABLE attendances (
  id                BIGINT      NOT NULL AUTO_INCREMENT,
  member_id         BIGINT      NOT NULL,
  store_id          BIGINT      NOT NULL,
  work_date         DATE        NOT NULL,
  clock_in_at       DATETIME(6) NOT NULL,
  clock_out_at      DATETIME(6) NULL,
  status            ENUM ('DONE', 'WORKING') NOT NULL,
  break_minutes     INT         NOT NULL,
  created_at        DATETIME(6) NULL,
  updated_at        DATETIME(6) NULL,
  working_member_id BIGINT GENERATED ALWAYS AS (IF(status = 'WORKING', member_id, NULL)) VIRTUAL,
  PRIMARY KEY (id),
  KEY idx_attendances_member_work_date (member_id, work_date),
  KEY idx_attendances_store_work_date (store_id, work_date),
  UNIQUE KEY uk_attendances_one_working (working_member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE leave_usages (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  member_id  BIGINT      NOT NULL,
  store_id   BIGINT      NOT NULL,
  leave_date DATE        NOT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_leave_usages_member_date (member_id, leave_date),
  KEY idx_leave_usages_store_date (store_id, leave_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
