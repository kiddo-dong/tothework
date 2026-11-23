-- 매장 단체 채팅. 매장 하나가 채팅방 하나라 방 테이블 없이 store_id가 곧 방 id다.
--
-- 이전 메시지는 id 내림차순 커서(before)로 읽으므로 (store_id, id) 인덱스 하나로 조회가 끝난다.
-- store·identity는 다른 서비스라 외래키를 걸지 않는다. 매장이 완전히 지워지면 StorePurgedEvent로 함께 지운다.
-- 삭제는 소프트 삭제다. 목록의 자리를 유지하고 "삭제된 메시지입니다"로 보여 준다.

CREATE TABLE chat_messages (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  store_id   BIGINT       NOT NULL,
  sender_id  BIGINT       NOT NULL,
  content    VARCHAR(500) NOT NULL,
  deleted    BIT(1)       NOT NULL DEFAULT b'0',
  created_at DATETIME(6)  NULL,
  updated_at DATETIME(6)  NULL,
  PRIMARY KEY (id),
  KEY idx_chat_messages_store_id_id (store_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
