package com.tothework.identity.api;

/**
 * 탈퇴를 막아야 하는 조건을 다른 서비스가 알려 주는 확장 지점.
 *
 * <p>이벤트는 "일어난 일"을 알릴 뿐 거부할 수 없어서 탈퇴 전 검사는 인터페이스로 뒤집는다. identity는 이 계약만
 * 알고, 매장 소속처럼 구체적인 조건은 그 데이터를 가진 서비스(store)가 구현한다. 서비스가 나뉘면 구현이 그
 * 서비스를 부르는 HTTP 호출이 된다.
 */
public interface WithdrawalGuard {

    /** 탈퇴할 수 없으면 사용자에게 보여 줄 메시지를 담은 예외를 던진다. */
    void checkCanWithdraw(Long userId);
}
