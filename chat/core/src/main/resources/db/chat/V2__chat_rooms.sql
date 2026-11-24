-- 매장 채팅방. 지금은 매장 하나에 방 하나라 store_id가 유일하다.
--
-- 매장이 생길 때 같은 트랜잭션에서 만든다(StoreCreatedEvent). 이 마이그레이션 전에 있던 매장의 방은 앱이 시작할 때
-- store-api로 매장 목록을 받아 채운다(ChatRoomBackfill). 다른 서비스의 스키마(tw_store)는 여기서 읽지 않는다.
-- 방 이름·알림 설정처럼 방에 딸린 것이 생기면 이 테이블에 더한다.

CREATE TABLE chat_rooms (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  store_id   BIGINT      NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_rooms_store_id (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
