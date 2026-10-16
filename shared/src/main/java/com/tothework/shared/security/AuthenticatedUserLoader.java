package com.tothework.shared.security;

import java.util.Optional;

/**
 * 검증된 액세스 토큰의 이메일로 현재 사용자를 읽어 온다. identity가 구현한다.
 *
 * <p>토큰 클레임만으로 주체를 만들지 않고 매 요청 사용자를 다시 읽는 이유: 추가 정보 입력을 마치면 그 즉시
 * 서비스를 쓸 수 있어야 하고(토큰을 새로 받지 않아도), 탈퇴한 사용자의 토큰은 만료 전이라도 바로 막혀야 한다.
 * 탈퇴하면 이메일이 바뀌어 더는 찾히지 않는다. 서비스가 나뉘면 이 호출이 identity로 가는 요청이 되므로, 그때는
 * 이 값들을 토큰에 담고 토큰 수명을 짧게 가져가는 쪽으로 바꾼다.
 */
public interface AuthenticatedUserLoader {

    Optional<AuthenticatedUser> loadByEmail(String email);
}
