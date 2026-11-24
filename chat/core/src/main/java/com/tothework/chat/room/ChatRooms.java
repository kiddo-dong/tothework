package com.tothework.chat.room;

import com.tothework.shared.error.NotFoundException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매장 채팅방의 생성·확인·삭제. 메시지 쪽은 이 클래스로만 방을 다룬다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRooms {

    private final ChatRoomRepository repository;

    /** 매장의 방. 없으면 404다. 매장 생성 이벤트나 기존 매장 채우기가 빠졌다는 뜻이라 조용히 만들지 않는다. */
    public ChatRoom requireOf(Long storeId) {
        return repository.findByStoreId(storeId)
                .orElseThrow(() -> new NotFoundException("채팅방을 찾을 수 없습니다."));
    }

    /** @return 새로 만들었으면 true. 이미 있으면 아무것도 하지 않는다 */
    @Transactional
    public boolean createIfAbsent(Long storeId) {
        return repository.insertIfAbsent(storeId, LocalDateTime.now()) > 0;
    }

    @Transactional
    public void deleteOf(Long storeId) {
        repository.deleteByStoreId(storeId);
    }
}
