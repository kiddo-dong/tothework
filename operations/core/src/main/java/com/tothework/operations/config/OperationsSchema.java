package com.tothework.operations.config;

/** operations 서비스의 MySQL 스키마. 모든 엔티티의 {@code @Table(schema = ...)}와 직접 쓴 SQL이 이 이름을 쓴다. */
public final class OperationsSchema {

    public static final String NAME = "tw_operations";

    private OperationsSchema() {
    }
}
