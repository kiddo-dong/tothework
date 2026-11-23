-- 예약 작업 락 (ShedLock). 서버가 여러 대일 때 같은 예약 작업이 한 대에서만 돌게 한다.
--
-- 작업마다 한 행이다. 먼저 lock_until을 미래로 바꾼 서버가 실행하고, 나머지는 그 시각까지 건너뛴다. 서비스 데이터가
-- 아니라 공용 인프라라 이벤트 발행 기록과 같은 tw_shared에 둔다. 스키마는 ShedLock 문서의 MySQL 정의와 같다.

CREATE TABLE IF NOT EXISTS shedlock
(
  name       VARCHAR(64)  NOT NULL,
  lock_until TIMESTAMP(3) NOT NULL,
  locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  locked_by  VARCHAR(255) NOT NULL,
  PRIMARY KEY (name)
);
