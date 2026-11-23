package com.tothework.chat.stomp;

import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.error.ProfileIncompleteException;
import com.tothework.shared.security.AuthenticatedUser;
import com.tothework.shared.security.AuthenticatedUserLoader;
import com.tothework.shared.security.JwtTokens;
import com.tothework.store.api.StoreAccess;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * 들어오는 STOMP 프레임의 인증과 주소 수준 인가.
 *
 * <ul>
 *   <li>CONNECT: {@code Authorization: Bearer} 액세스 토큰을 HTTP와 같은 방식으로 검증하고 세션의 주체를 정한다.
 *   <li>SUBSCRIBE·SEND: 주소의 storeId에 대해 그 매장의 재직 멤버인지 확인한다. 알려진 주소만 허용한다.
 * </ul>
 *
 * <p>여기서 던진 예외는 {@link StompErrorHandler}가 ERROR 프레임으로 바꾸고 연결이 끊긴다. "남의 메시지 수정"
 * 같은 비즈니스 규칙은 여기서 보지 않는다. 그건 연결을 끊을 일이 아니라서 서비스가 판단하고 개인 오류 큐로 알린다.
 *
 * <p>TODO 연결 중 액세스 토큰 만료: 지금은 CONNECT 시점에만 검증하므로 연결이 유지되는 동안 토큰이 만료돼도 계속
 * 쓸 수 있다. 탈퇴·퇴사는 매 프레임의 소속 확인에서 막힌다. 선택지는 docs/chat-architecture.md에 정리했다.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";
    private static final Pattern STORE_TOPIC = Pattern.compile("^/topic/stores/(\\d+)/chat$");
    private static final Pattern STORE_SEND = Pattern.compile("^/app/stores/(\\d+)/chat\\.(send|edit|delete)$");
    private static final String ERROR_QUEUE = "/user/queue/errors";

    private final JwtTokens jwtTokens;
    private final AuthenticatedUserLoader userLoader;
    private final StoreAccess storeAccess;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // 하트비트 등
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            authorize(accessor, STORE_TOPIC, true);
        } else if (command == StompCommand.SEND) {
            authorize(accessor, STORE_SEND, false);
        }
        return message;
    }

    private StompPrincipal authenticate(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            throw new StompAuthException("인증이 필요합니다. 로그인 후 이용해 주세요.");
        }
        String token = header.substring(BEARER.length());
        if (!jwtTokens.isValid(token, JwtTokens.Type.ACCESS)) {
            throw new StompAuthException("인증이 만료되었거나 올바르지 않습니다. 다시 로그인해 주세요.");
        }
        // HTTP 필터와 같이 사용자를 다시 읽는다. 탈퇴한 사용자의 토큰은 만료 전이라도 막힌다.
        AuthenticatedUser user = userLoader.loadByEmail(jwtTokens.emailOf(token))
                .orElseThrow(() -> new StompAuthException("인증이 필요합니다. 로그인 후 이용해 주세요."));
        if (!user.profileCompleted()) {
            throw new ProfileIncompleteException("추가 정보를 입력해야 서비스를 이용할 수 있습니다.");
        }
        return new StompPrincipal(user.userId());
    }

    private void authorize(StompHeaderAccessor accessor, Pattern storeDestination, boolean allowErrorQueue) {
        Long userId = userIdOf(accessor.getUser());
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new InvalidRequestException("주소가 없습니다.");
        }
        if (allowErrorQueue && ERROR_QUEUE.equals(destination)) {
            return;
        }
        Matcher matcher = storeDestination.matcher(destination);
        if (!matcher.matches()) {
            throw new ForbiddenException("허용되지 않은 주소입니다: " + destination);
        }
        // 재직 멤버가 아니면 403, 매장이 없거나 삭제됐으면 404 예외가 난다. 둘 다 ERROR 프레임이 된다.
        storeAccess.requireMember(Long.valueOf(matcher.group(1)), userId);
    }

    private static Long userIdOf(Principal user) {
        if (user instanceof StompPrincipal principal) {
            return principal.userId();
        }
        throw new StompAuthException("인증이 필요합니다. 로그인 후 이용해 주세요.");
    }
}
