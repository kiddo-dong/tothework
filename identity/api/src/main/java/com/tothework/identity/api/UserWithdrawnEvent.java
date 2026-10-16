package com.tothework.identity.api;

/**
 * 회원 탈퇴가 확정됐다. identity의 탈퇴 트랜잭션이 커밋된 뒤에 다른 서비스로 전달된다.
 *
 * <p>각 서비스는 이 이벤트를 받아 자기가 가진 그 사용자의 데이터를 스스로 정리한다. identity는 누가 무엇을
 * 지우는지 모른다. 받는 쪽은 비동기로 처리하고, 실패하면 발행 기록에 남아 다시 전달된다.
 */
public record UserWithdrawnEvent(Long userId) {
}
