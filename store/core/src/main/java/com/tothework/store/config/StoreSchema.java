package com.tothework.store.config;

/** store가 소유하는 스키마 이름. 엔티티의 {@code @Table}과 SQL에서 상수로 쓴다. */
public final class StoreSchema {

    public static final String NAME = "tw_store";

    private StoreSchema() {
    }
}
