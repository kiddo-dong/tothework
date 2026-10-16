package com.tothework.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 다른 서비스가 사용자 정보를 조회하는 입구. 목록 화면에서는 한 번에 여러 명을 조회해 N+1을 피한다. */
public interface UserDirectory {

    Optional<UserSummary> find(Long userId);

    /** 찾지 못한 id는 결과에서 빠진다. */
    Map<Long, UserSummary> findAll(Collection<Long> userIds);
}
