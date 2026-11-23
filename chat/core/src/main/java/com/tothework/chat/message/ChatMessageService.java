package com.tothework.chat.message;

import com.tothework.identity.api.UserDirectory;
import com.tothework.identity.api.UserSummary;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.StoreAccess;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 메시지의 저장과 조회. 방송은 하지 않는다. 호출한 쪽(ChatStompController)이 이 메서드가 끝난 뒤, 즉 커밋된
 * 뒤에 방송한다. 커밋 전에 방송하면 받은 사람이 아직 없는(롤백될 수도 있는) 메시지를 보게 된다.
 *
 * <p>전송·수정·삭제의 소속 확인은 STOMP 인터셉터가 주소 단계에서 이미 했다. 여기서는 메시지 단위 규칙(본인 것만,
 * 같은 매장의 메시지만)만 본다. REST 조회에는 인터셉터가 없으므로 여기서 소속을 확인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    static final String DELETED_CONTENT = "삭제된 메시지입니다";
    static final int MAX_PAGE_SIZE = 100;

    private final ChatMessageRepository repository;
    private final StoreAccess storeAccess;
    private final UserDirectory userDirectory;

    @Transactional
    public ChatMessageResponse send(Long storeId, Long userId, String content) {
        return toResponse(repository.save(ChatMessage.write(storeId, userId, content)));
    }

    @Transactional
    public ChatMessageResponse edit(Long storeId, Long userId, Long messageId, String content) {
        ChatMessage message = getInStore(storeId, messageId);
        message.edit(userId, content);
        return toResponse(repository.save(message));
    }

    @Transactional
    public ChatMessageResponse delete(Long storeId, Long userId, Long messageId) {
        ChatMessage message = getInStore(storeId, messageId);
        message.delete(userId);
        return toResponse(repository.save(message));
    }

    /**
     * 이전 메시지. id 내림차순(최신부터)이고, {@code before}를 주면 그보다 오래된 것부터 읽는다. 다음 페이지는 받은
     * 목록의 마지막(가장 작은) id를 {@code before}로 넘긴다.
     */
    public List<ChatMessageResponse> getMessages(Long storeId, Long userId, Long before, int size) {
        storeAccess.requireMember(storeId, userId);
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        List<ChatMessage> messages = before == null
                ? repository.findByStoreIdOrderByIdDesc(storeId, Limit.of(size))
                : repository.findByStoreIdAndIdLessThanOrderByIdDesc(storeId, before, Limit.of(size));
        Map<Long, UserSummary> senders = userDirectory.findAll(
                messages.stream().map(ChatMessage::getSenderId).distinct().toList());
        return messages.stream().map(message -> toResponse(message, senders.get(message.getSenderId()))).toList();
    }

    private ChatMessage getInStore(Long storeId, Long messageId) {
        if (messageId == null) {
            throw new InvalidRequestException("메시지 id가 필요합니다.");
        }
        return repository.findByIdAndStoreId(messageId, storeId)
                .orElseThrow(() -> new NotFoundException("메시지를 찾을 수 없습니다."));
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        return toResponse(message, userDirectory.find(message.getSenderId()).orElse(null));
    }

    private static ChatMessageResponse toResponse(ChatMessage message, UserSummary sender) {
        return new ChatMessageResponse(message.getId(), message.getStoreId(), message.getSenderId(),
                sender == null ? null : sender.name(), sender == null ? null : sender.profileImageUrl(),
                message.isDeleted() ? DELETED_CONTENT : message.getContent(), message.isDeleted(),
                message.getCreatedAt(), message.getUpdatedAt());
    }
}
