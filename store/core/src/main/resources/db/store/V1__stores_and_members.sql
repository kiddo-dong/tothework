-- 매장·영업시간·온보딩·소속·가입 신청. 컬럼은 기존 백엔드(al-bam)와 같아 데이터를 그대로 옮길 수 있다.
--
-- 기존과 다른 점은 store_members다. 시급·공제 방식·주휴일·근무 가능 요일은 workforce의 employment_terms로
-- 옮겼고, 여기에는 소속(역할·직함·재직 상태·입사일)만 남는다. 다른 서비스는 이 테이블에 외래키를 걸 수 없다.
-- 같은 서비스 안의 자식 테이블은 매장을 완전히 지울 때 함께 지워지도록 CASCADE를 둔다.

CREATE TABLE stores (
  id                           BIGINT       NOT NULL AUTO_INCREMENT,
  name                         VARCHAR(255) NOT NULL,
  address                      VARCHAR(255) NULL,
  business_registration_number VARCHAR(255) NULL,
  category                     ENUM ('BEAUTY', 'CAFE', 'CONVENIENCE_STORE', 'EDUCATION', 'ETC', 'FITNESS', 'FOOD', 'RETAIL') NULL,
  invite_code                  VARCHAR(6)   NOT NULL,
  break_policy                 ENUM ('FLEXIBLE', 'STATUTORY') NOT NULL,
  small_business               BIT(1)       NOT NULL,
  payday                       INT          NULL,
  profile_image_key            VARCHAR(255) NULL,
  plan                         VARCHAR(20)  NOT NULL DEFAULT 'BASIC',
  onboarding_completed_at      DATETIME(6)  NULL,
  deleted_at                   DATETIME(6)  NULL,
  created_at                   DATETIME(6)  NULL,
  updated_at                   DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stores_invite_code (invite_code),
  KEY idx_stores_deleted_at (deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE store_business_hours (
  store_id    BIGINT NOT NULL,
  day_of_week ENUM ('FRIDAY', 'MONDAY', 'SATURDAY', 'SUNDAY', 'THURSDAY', 'TUESDAY', 'WEDNESDAY') NOT NULL,
  open_time   TIME   NULL,
  close_time  TIME   NULL,
  closed      BIT(1) NOT NULL,
  PRIMARY KEY (store_id, day_of_week),
  CONSTRAINT fk_store_business_hours_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE store_onboarding_steps (
  store_id BIGINT      NOT NULL,
  step_key VARCHAR(40) NOT NULL,
  PRIMARY KEY (store_id, step_key),
  CONSTRAINT fk_store_onboarding_steps_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE store_members (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  store_id    BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  role        ENUM ('MANAGER', 'OWNER', 'STAFF') NOT NULL,
  title       VARCHAR(20) NULL,
  status      ENUM ('ACTIVE', 'INACTIVE') NOT NULL,
  joined_at   DATETIME(6) NOT NULL,
  resigned_at DATETIME(6) NULL,
  created_at  DATETIME(6) NULL,
  updated_at  DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_members_store_user (store_id, user_id),
  KEY idx_store_members_user_id_status (user_id, status),
  CONSTRAINT fk_store_members_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 같은 신청을 두 관리자가 동시에 승인·거절하면 늦은 쪽을 실패시키려고 낙관적 락 버전을 둔다.
CREATE TABLE join_requests (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  store_id     BIGINT      NOT NULL,
  user_id      BIGINT      NOT NULL,
  status       ENUM ('APPROVED', 'PENDING', 'REJECTED') NOT NULL,
  requested_at DATETIME(6) NOT NULL,
  decided_at   DATETIME(6) NULL,
  decided_role ENUM ('MANAGER', 'OWNER', 'STAFF') NULL,
  version      BIGINT      NOT NULL DEFAULT 0,
  created_at   DATETIME(6) NULL,
  updated_at   DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_join_requests_store_status (store_id, status),
  KEY idx_join_requests_user_id (user_id),
  CONSTRAINT fk_join_requests_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
