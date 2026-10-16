-- 사용자와 인증 토큰. 컬럼은 기존 백엔드(al-bam)와 같아 데이터를 그대로 옮길 수 있다.
-- 토큰 → 사용자 외래키는 같은 서비스 안이라 유지한다. 다른 서비스는 users에 외래키를 걸 수 없다.

CREATE TABLE users (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  email             VARCHAR(255) NOT NULL,
  password          VARCHAR(255) NULL,
  name              VARCHAR(255) NOT NULL,
  phone             VARCHAR(255) NULL,
  birth_date        DATE         NULL,
  terms_agreed_at   DATETIME(6)  NULL,
  profile_image_key VARCHAR(255) NULL,
  provider          ENUM ('GOOGLE', 'KAKAO', 'LOCAL', 'NAVER') NOT NULL,
  provider_id       VARCHAR(255) NULL,
  email_verified    BIT(1)       NOT NULL,
  deleted_at        DATETIME(6)  NULL,
  created_at        DATETIME(6)  NULL,
  updated_at        DATETIME(6)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_provider (provider, provider_id),
  UNIQUE KEY uk_users_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE email_tokens (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  token      VARCHAR(36) NOT NULL,
  type       ENUM ('PASSWORD_RESET', 'VERIFY_EMAIL') NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used       BIT(1)      NOT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_email_tokens_token (token),
  KEY idx_email_tokens_user_id (user_id),
  CONSTRAINT fk_email_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 리프레시 토큰은 원문이 아니라 SHA-256 해시를 저장한다. 이 행을 지우는 것이 곧 토큰 폐기다.
CREATE TABLE refresh_tokens (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_tokens_token_hash (token_hash),
  KEY idx_refresh_tokens_user_id (user_id),
  KEY idx_refresh_tokens_expires_at (expires_at),
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
