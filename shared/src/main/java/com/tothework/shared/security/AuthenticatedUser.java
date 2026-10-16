package com.tothework.shared.security;

/**
 * 인증된 요청의 주체가 공통으로 제공하는 값.
 *
 * <p>토큰은 모든 서비스가 같은 방식으로 검증하지만, 주체를 채우는 일({@link AuthenticatedUserLoader})은 identity가
 * 맡는다. 다른 서비스는 이 인터페이스만 알고 identity의 내부를 모른다.
 */
public interface AuthenticatedUser {

    Long userId();

    /** 소셜 가입 후 전화번호·생년월일·약관 동의를 마쳤는지. 마치지 않으면 대부분의 API를 쓸 수 없다. */
    boolean profileCompleted();
}
