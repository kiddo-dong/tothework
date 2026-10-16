package com.tothework.store.store;

/**
 * 매장 요금제. 아직 어떤 기능도 이 값을 보지 않는다. 유료화를 붙일 때 매장마다 등급을 저장할 자리로 먼저 만들었다.
 * 이름을 DB에 문자열로 저장하므로, 등급을 늘릴 때는 기존 이름을 바꾸지 말고 새 이름을 추가한다.
 */
public enum StorePlan {
    BASIC,
    PREMIUM
}
