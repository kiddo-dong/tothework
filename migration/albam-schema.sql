-- 기존 al-bam DB의 테이블 정의 (Flyway V12 시점, 데이터 없음). 이전 스크립트를 테스트할 원본을 만드는 데 쓴다.
-- mysqldump --no-data로 뜬 것이다. 테이블이 이름순이라 외래키가 뒤 테이블을 가리킬 수 있어 검사를 잠시 끈다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `attendances` (
  `break_minutes` int NOT NULL,
  `work_date` date NOT NULL,
  `clock_in_at` datetime(6) NOT NULL,
  `clock_out_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_member_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` enum('DONE','WORKING') NOT NULL,
  `working_member_id` bigint GENERATED ALWAYS AS (if((`status` = _utf8mb4'WORKING'),`store_member_id`,NULL)) VIRTUAL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attendances_one_working` (`working_member_id`),
  KEY `idx_attendances_store_member_id_work_date` (`store_member_id`,`work_date`),
  CONSTRAINT `FK5e08i625qj0jutp0jo3v92hx9` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `checklist_completions` (
  `work_date` date NOT NULL,
  `checked_at` datetime(6) NOT NULL,
  `checked_by` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `item_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKc53jwavs4rsevsje8xy6lq32m` (`item_id`,`work_date`),
  KEY `FKmeyw3nvdbfa7bk9vu22alfc10` (`checked_by`),
  CONSTRAINT `FKf5u9bh9qdt8t9sbvxd0wf8e3a` FOREIGN KEY (`item_id`) REFERENCES `checklist_items` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKmeyw3nvdbfa7bk9vu22alfc10` FOREIGN KEY (`checked_by`) REFERENCES `store_members` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `checklist_items` (
  `display_order` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` varchar(255) NOT NULL,
  `type` enum('CLOSE','OPEN') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_checklist_items_store_id` (`store_id`),
  CONSTRAINT `FKjrpjxvdvcrf1ho9fxfkd22kgs` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `email_tokens` (
  `used` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `token` varchar(36) NOT NULL,
  `type` enum('PASSWORD_RESET','VERIFY_EMAIL') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKonvbn2qcpb3pmtbytnintyeju` (`token`),
  KEY `FK9j2pq9xa8fh246k3uruj9feec` (`user_id`),
  CONSTRAINT `FK9j2pq9xa8fh246k3uruj9feec` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `handover_notes` (
  `work_date` date NOT NULL,
  `author_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_handover_notes_store_id_work_date` (`store_id`,`work_date`),
  KEY `FKjck202sjbv7y34uj3i2boay6v` (`author_id`),
  CONSTRAINT `FK47fn43ihf75wwhlu4yt7cu753` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKjck202sjbv7y34uj3i2boay6v` FOREIGN KEY (`author_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `join_requests` (
  `created_at` datetime(6) DEFAULT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requested_at` datetime(6) NOT NULL,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `decided_role` enum('MANAGER','OWNER','STAFF') DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FKdku7pij8qiqg1471wybd70vdu` (`user_id`),
  KEY `FKciwrkm3hckkei8u0d6k6jwhyu` (`store_id`),
  CONSTRAINT `FKciwrkm3hckkei8u0d6k6jwhyu` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKdku7pij8qiqg1471wybd70vdu` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `labor_qa_messages` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` text NOT NULL,
  `sources` text,
  `role` enum('ASSISTANT','USER') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4mkmj7bjn3sr2ls20d62qp9te` (`session_id`),
  CONSTRAINT `FK4mkmj7bjn3sr2ls20d62qp9te` FOREIGN KEY (`session_id`) REFERENCES `labor_qa_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `labor_qa_sessions` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `title` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_labor_qa_sessions_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `leave_usages` (
  `leave_date` date NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_member_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf2p94tyg10h6v4q9afqqhn4ne` (`store_member_id`,`leave_date`),
  CONSTRAINT `FK4j3fe2qqyki0fdp6fcrfl3x7b` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `manual_images` (
  `sort_order` int NOT NULL,
  `manual_id` bigint NOT NULL,
  `image_key` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`sort_order`,`manual_id`),
  KEY `FKe09qax1u2bpgbd5rny25j627l` (`manual_id`),
  CONSTRAINT `FKe09qax1u2bpgbd5rny25j627l` FOREIGN KEY (`manual_id`) REFERENCES `manuals` (`id`) ON DELETE CASCADE,
  CONSTRAINT `manual_images_chk_1` CHECK ((`sort_order` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `manuals` (
  `display_order` int NOT NULL,
  `author_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `title` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_manuals_store_id` (`store_id`),
  KEY `FKnujm24qbfsrr66bb7gggk2hp4` (`author_id`),
  CONSTRAINT `FK99nxxm238fs52q3vjflyuau5r` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKnujm24qbfsrr66bb7gggk2hp4` FOREIGN KEY (`author_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `menu_ingredients` (
  `loss_rate` int NOT NULL,
  `package_qty` double NOT NULL,
  `price` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `product_info` varchar(255) DEFAULT NULL,
  `unit` enum('EA','G','ML') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_menu_ingredients_store_id` (`store_id`),
  CONSTRAINT `FK87s3nows5dekg4cjry0v6ig1b` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `menu_recipe_items` (
  `amount` double NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ingredient_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKa6s4u9rhsknuska4fgdqvkho5` (`menu_id`),
  KEY `FKk6kbvk6ci6mnpep2y0hmk6rn2` (`ingredient_id`),
  CONSTRAINT `FKa6s4u9rhsknuska4fgdqvkho5` FOREIGN KEY (`menu_id`) REFERENCES `store_menus` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKk6kbvk6ci6mnpep2y0hmk6rn2` FOREIGN KEY (`ingredient_id`) REFERENCES `menu_ingredients` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `notice_reads` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_id` bigint NOT NULL,
  `read_at` datetime(6) NOT NULL,
  `store_member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpgwdng0112bt9f9v89so1ce5f` (`notice_id`,`store_member_id`),
  KEY `FKdcce4bi277xht92c6go6vx7kf` (`store_member_id`),
  CONSTRAINT `FKdcce4bi277xht92c6go6vx7kf` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKh5mvl1a0vjnfm5q43krvmjy17` FOREIGN KEY (`notice_id`) REFERENCES `notices` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `notices` (
  `author_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` text NOT NULL,
  `title` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notices_store_id` (`store_id`),
  KEY `FKcj2l3rbol6rbtsxy3lhfe6qbk` (`author_id`),
  CONSTRAINT `FKcj2l3rbol6rbtsxy3lhfe6qbk` FOREIGN KEY (`author_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKj5qg3puvvc4woymnpo7hjrv6p` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `payrolls` (
  `target_month` int NOT NULL,
  `target_year` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `deduction` bigint NOT NULL,
  `generated_at` datetime(6) NOT NULL,
  `holiday_work_pay` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `leave_pay` bigint NOT NULL,
  `net_pay` bigint NOT NULL,
  `night_pay` bigint NOT NULL,
  `overtime_pay` bigint NOT NULL,
  `regular_pay` bigint NOT NULL,
  `store_member_id` bigint NOT NULL,
  `total_pay` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `weekly_holiday_pay` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2spwrk9jt15qkalako8q7wj2j` (`store_member_id`,`target_year`,`target_month`),
  CONSTRAINT `FK95fwh7jvn1emk58tf5lmib2xi` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(64) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_tokens_token_hash` (`token_hash`),
  KEY `idx_refresh_tokens_user_id` (`user_id`),
  KEY `idx_refresh_tokens_expires_at` (`expires_at`),
  CONSTRAINT `fk_refresh_tokens_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `shift_templates` (
  `break_minutes` int DEFAULT NULL,
  `display_order` int NOT NULL,
  `end_time` time NOT NULL,
  `start_time` time NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5pydoj8s169myox0earhny559` (`store_id`,`name`),
  CONSTRAINT `FKr8m7dooen1tihhufwfd794vce` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `shifts` (
  `break_minutes` int NOT NULL,
  `end_time` time NOT NULL,
  `start_time` time NOT NULL,
  `work_date` date NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_member_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELED','CONFIRMED','SCHEDULED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shifts_store_member_id_work_date` (`store_member_id`,`work_date`),
  CONSTRAINT `FK7x22dn00ui3vwijg2ma0hdvx1` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `store_business_hours` (
  `close_time` time DEFAULT NULL,
  `closed` bit(1) NOT NULL,
  `open_time` time DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `day_of_week` enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') NOT NULL,
  PRIMARY KEY (`store_id`,`day_of_week`),
  CONSTRAINT `FK80xirqrvvoqvaon5435d6de0q` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `store_member_available_days` (
  `store_member_id` bigint NOT NULL,
  `day_of_week` enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') DEFAULT NULL,
  UNIQUE KEY `UKsq8082eghn89cyn9vg7j7e8wp` (`store_member_id`,`day_of_week`),
  CONSTRAINT `FKsaxbskktb30s1nw76cdgti99n` FOREIGN KEY (`store_member_id`) REFERENCES `store_members` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `store_members` (
  `hourly_wage` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `joined_at` datetime(6) NOT NULL,
  `resigned_at` datetime(6) DEFAULT NULL,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `role` enum('MANAGER','OWNER','STAFF') NOT NULL,
  `status` enum('ACTIVE','INACTIVE') NOT NULL,
  `tax_mode` enum('FOUR_INSURANCES','NONE','WITHHOLDING_3_3') NOT NULL,
  `weekly_holiday_day` enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') DEFAULT NULL,
  `title` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKq4r1eem5774gcsoswd5c5p5a` (`store_id`,`user_id`),
  KEY `idx_store_members_user_id_status` (`user_id`,`status`),
  CONSTRAINT `FK7t3ew28fhd34d6rtjepe9p173` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKcficqha1nyt26jv2pmgnd130y` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `store_menus` (
  `selling_price` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_store_menus_store_id` (`store_id`),
  CONSTRAINT `FKtnc6cftp4bubdsl23tifuh80b` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `store_onboarding_steps` (
  `store_id` bigint NOT NULL,
  `step_key` varchar(40) NOT NULL,
  PRIMARY KEY (`store_id`,`step_key`),
  CONSTRAINT `fk_store_onboarding_steps_store` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `stores` (
  `small_business` bit(1) NOT NULL,
  `invite_code` varchar(6) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `business_registration_number` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `break_policy` enum('FLEXIBLE','STATUTORY') NOT NULL,
  `category` enum('BEAUTY','CAFE','CONVENIENCE_STORE','EDUCATION','ETC','FITNESS','FOOD','RETAIL') DEFAULT NULL,
  `payday` int DEFAULT NULL,
  `onboarding_completed_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime DEFAULT NULL,
  `plan` varchar(20) NOT NULL DEFAULT 'BASIC',
  `profile_image_key` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKb7it7d0yomvl73appj2odasck` (`invite_code`),
  KEY `idx_stores_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `supplier_item_quantities` (
  `item_id` bigint NOT NULL,
  `quantity` varchar(255) DEFAULT NULL,
  `day_of_week` enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') NOT NULL,
  PRIMARY KEY (`item_id`,`day_of_week`),
  CONSTRAINT `FKldsn5srvc9nab9oyh8nk54box` FOREIGN KEY (`item_id`) REFERENCES `supplier_items` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `supplier_items` (
  `display_order` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `supplier_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `memo` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `spec` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKm2t6dgtc9r1a39fop5375dtma` (`supplier_id`),
  CONSTRAINT `FKm2t6dgtc9r1a39fop5375dtma` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `suppliers` (
  `display_order` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `store_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category` varchar(255) NOT NULL,
  `memo` text,
  `name` varchar(255) NOT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `site_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_suppliers_store_id` (`store_id`),
  CONSTRAINT `FK31osod3me2lknglh15pxr5ilb` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `users` (
  `birth_date` date DEFAULT NULL,
  `email_verified` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `terms_agreed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `profile_image_key` varchar(255) DEFAULT NULL,
  `provider_id` varchar(255) DEFAULT NULL,
  `provider` enum('GOOGLE','KAKAO','LOCAL','NAVER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKcbysvpk95086ud4n4g6mkspai` (`provider`,`provider_id`),
  UNIQUE KEY `UKdu5v5sr43g5bfnji4vb8hg5s3` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
