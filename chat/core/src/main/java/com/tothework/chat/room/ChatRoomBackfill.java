package com.tothework.chat.room;

import com.tothework.store.api.StoreDirectory;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 방이 없는 기존 매장에 방을 만든다. 채팅방이 생기기 전에 만들어진 매장은 생성 이벤트가 이미 지나갔기 때문이다.
 *
 * <p>앱이 뜰 때(트래픽을 받기 전) 한 번 돈다. 매장 목록은 store-api로 받고 다른 서비스의 스키마는 읽지 않는다. 서버가
 * 여러 대라 락을 잡은 한 대만 채우고, 방 만들기 자체도 "없을 때만"이라 겹쳐도 방은 하나다. 이미 다 있으면 아무것도
 * 만들지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ChatRoomBackfill implements ApplicationRunner {

    static final String LOCK_NAME = "chat.backfillRooms";

    private final StoreDirectory storeDirectory;
    private final ChatRooms chatRooms;
    private final LockProvider lockProvider;

    @Override
    public void run(ApplicationArguments args) {
        new DefaultLockingTaskExecutor(lockProvider).executeWithLock((Runnable) this::backfill,
                new LockConfiguration(Instant.now(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
    }

    void backfill() {
        int created = 0;
        for (Long storeId : storeDirectory.findAllActiveIds()) {
            if (chatRooms.createIfAbsent(storeId)) {
                created++;
            }
        }
        if (created > 0) {
            log.info("방이 없던 기존 매장 {}곳에 채팅방을 만들었다", created);
        }
    }
}
