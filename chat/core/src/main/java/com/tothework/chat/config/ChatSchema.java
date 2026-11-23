package com.tothework.chat.config;

/** chat 서비스의 MySQL 스키마. 모든 엔티티의 {@code @Table(schema = ...)}와 직접 쓴 SQL이 이 이름을 쓴다. */
public final class ChatSchema {

    public static final String NAME = "tw_chat";

    private ChatSchema() {
    }
}
