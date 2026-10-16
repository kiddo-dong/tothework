-- 월 급여 확정 기록. 컬럼은 기존 al-bam 테이블과 같고, V2와 마찬가지로 store_member_id 대신 member_id이며
-- store_id를 둔다. 조회할 때마다 다시 계산해 덮어쓴다(멤버·월마다 1행).

CREATE TABLE payrolls (
  id                 BIGINT      NOT NULL AUTO_INCREMENT,
  member_id          BIGINT      NOT NULL,
  store_id           BIGINT      NOT NULL,
  target_year        INT         NOT NULL,
  target_month       INT         NOT NULL,
  regular_pay        BIGINT      NOT NULL,
  overtime_pay       BIGINT      NOT NULL,
  night_pay          BIGINT      NOT NULL,
  holiday_work_pay   BIGINT      NOT NULL,
  weekly_holiday_pay BIGINT      NOT NULL,
  leave_pay          BIGINT      NOT NULL,
  total_pay          BIGINT      NOT NULL,
  deduction          BIGINT      NOT NULL,
  net_pay            BIGINT      NOT NULL,
  generated_at       DATETIME(6) NOT NULL,
  created_at         DATETIME(6) NULL,
  updated_at         DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payrolls_member_month (member_id, target_year, target_month),
  KEY idx_payrolls_store (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
