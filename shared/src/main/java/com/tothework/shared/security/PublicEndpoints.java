package com.tothework.shared.security;

import java.util.List;

/**
 * 로그인 없이 열어 둘 경로. 각 서비스가 자기 경로를 빈으로 등록하고, 보안 설정은 모아서 적용만 한다.
 *
 * <p>예전에는 보안 설정 한 곳에 모든 기능의 공개 경로가 적혀 있어, 공통 계층이 각 기능의 URL을 알아야 했다.
 * 서비스가 나뉘면 각자 자기 공개 경로만 알면 된다.
 *
 * @param patterns Ant 스타일 경로. 와일드카드를 쓰면 그 아래 새로 만드는 엔드포인트도 인증 없이 열리니 주의할 것
 */
public record PublicEndpoints(List<String> patterns) {

    public static PublicEndpoints of(String... patterns) {
        return new PublicEndpoints(List.of(patterns));
    }
}
