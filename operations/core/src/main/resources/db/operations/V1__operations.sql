-- 체크리스트·공지·인수인계·매뉴얼·거래처·메뉴 원가. 컬럼은 기존 al-bam 테이블과 같고 다음이 다르다.
--
-- 1. stores·store_members를 가리키던 외래키가 없다. 다른 서비스의 테이블이기 때문이다. author_id·checked_by는
--    store의 소속 id(memberId)이고, notice_reads.store_member_id는 workforce와 맞춰 member_id로 바꿨다.
-- 2. menu_recipe_items에 순서 열(line_order)을 더했다. 예전엔 자동 증가 id 순서였는데, 레시피 줄은 이제 메뉴의
--    자식이라 id가 없다.
-- 3. operations 안의 외래키는 ON DELETE CASCADE다. 매장 삭제 시 루트만 지우면 자식이 함께 지워진다. 예외는
--    레시피 줄 → 재료로, 아래 설명처럼 막아 둔다.

CREATE TABLE checklist_items (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  store_id      BIGINT       NOT NULL,
  type          ENUM ('CLOSE', 'OPEN') NOT NULL,
  content       VARCHAR(255) NOT NULL,
  display_order INT          NOT NULL,
  created_at    DATETIME(6)  NULL,
  updated_at    DATETIME(6)  NULL,
  PRIMARY KEY (id),
  KEY idx_checklist_items_store_id (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE checklist_completions (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  item_id    BIGINT      NOT NULL,
  store_id   BIGINT      NOT NULL,
  work_date  DATE        NOT NULL,
  checked_by BIGINT      NOT NULL,
  checked_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_checklist_completions_item_date (item_id, work_date),
  KEY idx_checklist_completions_store_date (store_id, work_date),
  CONSTRAINT fk_checklist_completions_item FOREIGN KEY (item_id) REFERENCES checklist_items (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notices (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  store_id   BIGINT       NOT NULL,
  author_id  BIGINT       NOT NULL,
  title      VARCHAR(255) NOT NULL,
  content    TEXT         NOT NULL,
  created_at DATETIME(6)  NULL,
  updated_at DATETIME(6)  NULL,
  PRIMARY KEY (id),
  KEY idx_notices_store_id (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notice_reads (
  id        BIGINT      NOT NULL AUTO_INCREMENT,
  notice_id BIGINT      NOT NULL,
  member_id BIGINT      NOT NULL,
  read_at   DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notice_reads_notice_member (notice_id, member_id),
  KEY idx_notice_reads_member (member_id),
  CONSTRAINT fk_notice_reads_notice FOREIGN KEY (notice_id) REFERENCES notices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE handover_notes (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  store_id   BIGINT      NOT NULL,
  author_id  BIGINT      NOT NULL,
  content    TEXT        NOT NULL,
  work_date  DATE        NOT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_handover_notes_store_id_work_date (store_id, work_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE manuals (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  store_id      BIGINT       NOT NULL,
  author_id     BIGINT       NOT NULL,
  category      VARCHAR(255) NOT NULL,
  title         VARCHAR(255) NOT NULL,
  content       TEXT         NOT NULL,
  display_order INT          NOT NULL,
  created_at    DATETIME(6)  NULL,
  updated_at    DATETIME(6)  NULL,
  PRIMARY KEY (id),
  KEY idx_manuals_store_id (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE manual_images (
  manual_id  BIGINT       NOT NULL,
  sort_order INT          NOT NULL,
  image_key  VARCHAR(255) NULL,
  PRIMARY KEY (manual_id, sort_order),
  CONSTRAINT fk_manual_images_manual FOREIGN KEY (manual_id) REFERENCES manuals (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE suppliers (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  store_id      BIGINT       NOT NULL,
  name          VARCHAR(255) NOT NULL,
  category      VARCHAR(255) NOT NULL,
  site_url      VARCHAR(255) NULL,
  phone         VARCHAR(255) NULL,
  memo          TEXT         NULL,
  display_order INT          NOT NULL,
  created_at    DATETIME(6)  NULL,
  updated_at    DATETIME(6)  NULL,
  PRIMARY KEY (id),
  KEY idx_suppliers_store_id (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE supplier_items (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  supplier_id   BIGINT       NOT NULL,
  name          VARCHAR(255) NOT NULL,
  spec          VARCHAR(255) NULL,
  memo          VARCHAR(255) NULL,
  display_order INT          NOT NULL,
  created_at    DATETIME(6)  NULL,
  updated_at    DATETIME(6)  NULL,
  PRIMARY KEY (id),
  KEY idx_supplier_items_supplier (supplier_id),
  CONSTRAINT fk_supplier_items_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE supplier_item_quantities (
  item_id     BIGINT       NOT NULL,
  day_of_week ENUM ('FRIDAY', 'MONDAY', 'SATURDAY', 'SUNDAY', 'THURSDAY', 'TUESDAY', 'WEDNESDAY') NOT NULL,
  quantity    VARCHAR(255) NULL,
  PRIMARY KEY (item_id, day_of_week),
  CONSTRAINT fk_supplier_item_quantities_item FOREIGN KEY (item_id) REFERENCES supplier_items (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE menu_ingredients (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  store_id     BIGINT       NOT NULL,
  name         VARCHAR(255) NOT NULL,
  product_info VARCHAR(255) NULL,
  price        INT          NOT NULL,
  package_qty  DOUBLE       NOT NULL,
  unit         ENUM ('EA', 'G', 'ML') NOT NULL,
  loss_rate    INT          NOT NULL,
  category     VARCHAR(255) NOT NULL,
  created_at   DATETIME(6)  NULL,
  updated_at   DATETIME(6)  NULL,
  PRIMARY KEY (id),
  KEY idx_menu_ingredients_store_id (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE store_menus (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  store_id      BIGINT       NOT NULL,
  name          VARCHAR(255) NOT NULL,
  selling_price INT          NOT NULL,
  category      VARCHAR(255) NOT NULL,
  created_at    DATETIME(6)  NULL,
  updated_at    DATETIME(6)  NULL,
  PRIMARY KEY (id),
  KEY idx_store_menus_store_id (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE menu_recipe_items (
  menu_id       BIGINT NOT NULL,
  line_order    INT    NOT NULL,
  ingredient_id BIGINT NOT NULL,
  amount        DOUBLE NOT NULL,
  PRIMARY KEY (menu_id, line_order),
  KEY idx_menu_recipe_items_ingredient (ingredient_id),
  CONSTRAINT fk_menu_recipe_items_menu FOREIGN KEY (menu_id) REFERENCES store_menus (id) ON DELETE CASCADE,
  -- 재료를 지우면 서비스가 그 재료를 쓰는 메뉴의 레시피를 애그리거트로 다시 저장해 줄을 뺀다. CASCADE로 줄만 지우면
  -- line_order에 구멍이 생겨 목록으로 읽을 수 없으므로, 그 경로를 거치지 않은 삭제는 막는다.
  CONSTRAINT fk_menu_recipe_items_ingredient FOREIGN KEY (ingredient_id) REFERENCES menu_ingredients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
