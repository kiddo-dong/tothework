package com.tothework.chat.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tothework.chat.room.ChatRooms;
import com.tothework.identity.api.UserDirectory;
import com.tothework.identity.api.UserSummary;
import com.tothework.shared.error.ConflictException;
import com.tothework.shared.error.ForbiddenException;
import com.tothework.shared.error.InvalidRequestException;
import com.tothework.shared.error.NotFoundException;
import com.tothework.store.api.StoreAccess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

/**
 * 메시지 단위 규칙: 본인 것만 수정·삭제, 삭제된 것은 다시 못 건드림, 다른 매장의 메시지 id는 없는 것으로 본다.
 *
 * <p>Spring Data JDBC에는 변경 감지가 없어서, 바꾼 뒤 저장을 빠뜨리면 조용히 반영되지 않는다. 그래서 저장 호출도 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final long STORE_ID = 1L;
    private static final long OTHER_STORE_ID = 2L;
    private static final long ME = 10L;
    private static final long SOMEONE_ELSE = 11L;
    private static final long MESSAGE_ID = 100L;

    @Mock private ChatMessageRepository repository;
    @Mock private StoreAccess storeAccess;
    @Mock private UserDirectory userDirectory;
    @Mock private ChatRooms chatRooms;
    @InjectMocks private ChatMessageService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userDirectory.find(anyLong()))
                .thenAnswer(invocation -> Optional.of(user(invocation.getArgument(0))));
    }

    @Test
    void sendStoresTrimmedContentWithTheSenderName() {
        ChatMessageResponse response = service.send(STORE_ID, ME, "  안녕하세요  ");

        assertThat(response.content()).isEqualTo("안녕하세요");
        assertThat(response.senderId()).isEqualTo(ME);
        assertThat(response.senderName()).isEqualTo("user" + ME);
        assertThat(response.storeId()).isEqualTo(STORE_ID);
        verify(repository).save(any(ChatMessage.class));
    }

    @Test
    void blankOrTooLongMessagesAreRejectedBeforeSaving() {
        assertThatThrownBy(() -> service.send(STORE_ID, ME, "   ")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.send(STORE_ID, ME, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.send(STORE_ID, ME, "가".repeat(501)))
                .isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).save(any());
    }

    /** DB의 VARCHAR(500)은 글자 수로 센다. 이모지(UTF-16 두 칸)도 한 글자로 세야 500자 이모지가 통과한다. */
    @Test
    void lengthIsCountedInCharactersNotUtf16Units() {
        assertThat(service.send(STORE_ID, ME, "😀".repeat(500)).content()).hasSize(1000);
    }

    /** 매장의 방이 없으면(생성 이벤트나 기존 매장 채우기가 빠졌다) 저장하지 않고 404다. 조용히 방을 만들지 않는다. */
    @Test
    void sendingToAStoreWithoutARoomIsNotFound() {
        when(chatRooms.requireOf(STORE_ID)).thenThrow(new NotFoundException("채팅방을 찾을 수 없습니다."));

        assertThatThrownBy(() -> service.send(STORE_ID, ME, "안녕")).isInstanceOf(NotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void ownerCanEditTheirMessage() {
        ChatMessage mine = message(ME, false);
        when(repository.findByIdAndStoreId(MESSAGE_ID, STORE_ID)).thenReturn(Optional.of(mine));

        ChatMessageResponse response = service.edit(STORE_ID, ME, MESSAGE_ID, "고친 내용");

        assertThat(response.content()).isEqualTo("고친 내용");
        verify(repository).save(mine);
    }

    @Test
    void deleteIsSoftAndHidesTheContent() {
        ChatMessage mine = message(ME, false);
        when(repository.findByIdAndStoreId(MESSAGE_ID, STORE_ID)).thenReturn(Optional.of(mine));

        ChatMessageResponse response = service.delete(STORE_ID, ME, MESSAGE_ID);

        assertThat(mine.isDeleted()).isTrue();
        assertThat(response.deleted()).isTrue();
        assertThat(response.content()).isEqualTo(ChatMessageService.DELETED_CONTENT);
        verify(repository).save(mine);
    }

    @Test
    void someoneElsesMessageCannotBeEditedOrDeleted() {
        when(repository.findByIdAndStoreId(MESSAGE_ID, STORE_ID)).thenReturn(Optional.of(message(SOMEONE_ELSE, false)));

        assertThatThrownBy(() -> service.edit(STORE_ID, ME, MESSAGE_ID, "x")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.delete(STORE_ID, ME, MESSAGE_ID)).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void aDeletedMessageCannotBeEditedOrDeletedAgain() {
        when(repository.findByIdAndStoreId(MESSAGE_ID, STORE_ID)).thenReturn(Optional.of(message(ME, true)));

        assertThatThrownBy(() -> service.edit(STORE_ID, ME, MESSAGE_ID, "x")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.delete(STORE_ID, ME, MESSAGE_ID)).isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    /** 다른 매장의 메시지 id를 넣으면 매장 id까지 같이 찾으므로 없는 메시지가 된다. 다른 매장의 존재도 드러나지 않는다. */
    @Test
    void aMessageFromAnotherStoreIsNotFound() {
        when(repository.findByIdAndStoreId(MESSAGE_ID, OTHER_STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.edit(OTHER_STORE_ID, ME, MESSAGE_ID, "x"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(OTHER_STORE_ID, ME, MESSAGE_ID))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void historyChecksMembershipAndUsesTheCursor() {
        ChatMessage deleted = message(SOMEONE_ELSE, true);
        when(repository.findByStoreIdAndIdLessThanOrderByIdDesc(STORE_ID, 500L, Limit.of(30)))
                .thenReturn(List.of(deleted));
        when(userDirectory.findAll(anyCollection())).thenReturn(Map.of(SOMEONE_ELSE, user(SOMEONE_ELSE)));

        List<ChatMessageResponse> page = service.getMessages(STORE_ID, ME, 500L, 30);

        verify(storeAccess).requireMember(STORE_ID, ME);
        assertThat(page).singleElement().satisfies(message -> {
            assertThat(message.content()).isEqualTo(ChatMessageService.DELETED_CONTENT);
            assertThat(message.senderName()).isEqualTo("user" + SOMEONE_ELSE);
        });
    }

    @Test
    void historyPageSizeIsBounded() {
        assertThatThrownBy(() -> service.getMessages(STORE_ID, ME, null, 0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.getMessages(STORE_ID, ME, null, 101))
                .isInstanceOf(InvalidRequestException.class);
        verify(repository, never()).findByStoreIdOrderByIdDesc(eq(STORE_ID), any());
    }

    private static ChatMessage message(long senderId, boolean deleted) {
        LocalDateTime now = LocalDateTime.now();
        return new ChatMessage(MESSAGE_ID, STORE_ID, senderId, "원래 내용", deleted, now, now);
    }

    private static UserSummary user(long id) {
        return new UserSummary(id, "user" + id, "u" + id + "@albam.dev", null, null);
    }
}
