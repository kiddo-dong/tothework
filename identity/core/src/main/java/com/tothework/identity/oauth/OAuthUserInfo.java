package com.tothework.identity.oauth;

/**
 * 소셜 제공자에게서 받아온 가입 정보.
 *
 * <p>{@code profileImageUrl}은 제공자 쪽 주소다. 그대로 저장하지 않고 가입 시 한 번 내려받아
 * 우리 저장소에 둔다 — 이 주소는 영구적이지 않다. 제공자가 사진을 주지 않으면 null이다.
 */
public record OAuthUserInfo(String providerId, String email, String name, String profileImageUrl) {
}
