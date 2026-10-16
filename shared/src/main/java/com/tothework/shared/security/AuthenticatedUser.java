package com.tothework.shared.security;

/**
 * 인증된 요청의 주체가 공통으로 제공하는 값.
 *
 * <p>JWT를 검증해 이 주체를 만드는 일은 identity가 맡는다. 다른 서비스는 이 인터페이스만 알고 identity의
 * 내부를 모른다. MSA로 나뉘면 각 서비스가 토큰을 직접 검증해 같은 주체를 만든다.
 */
public interface AuthenticatedUser {

    Long userId();
}
