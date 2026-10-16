package com.tothework.identity.api;

import java.time.LocalDate;

/**
 * 다른 서비스가 화면에 사람을 보여 주거나 판단(연소자 여부 등)할 때 필요한 사용자 정보.
 * identity의 엔티티 대신 이 값만 서비스 밖으로 나간다.
 *
 * @param profileImageUrl 공개 URL. 사진을 등록하지 않았으면 null
 * @param birthDate       추가 정보 입력 전이면 null
 */
public record UserSummary(Long id, String name, String email, String profileImageUrl, LocalDate birthDate) {
}
