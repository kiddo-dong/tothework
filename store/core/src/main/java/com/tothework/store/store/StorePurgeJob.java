package com.tothework.store.store;

import com.tothework.store.api.StorePurgedEvent;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 삭제 유예기간(90일)이 지난 매장을 완전히 지운다.
 *
 * <p>store의 테이블은 같은 스키마 안의 CASCADE로 함께 지워진다. 다른 서비스의 데이터(근태·급여·공지 등)는
 * {@link StorePurgedEvent}를 받은 각 서비스가 스스로 지운다. 예전에는 DB CASCADE가 전부를 한 번에 지웠다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorePurgeJob {

    static final int GRACE_PERIOD_DAYS = 90;

    private final StoreRepository storeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 매일 새벽 4시 45분. 토큰·계정 정리와 겹치지 않게 시간을 띄운다. */
    @Scheduled(cron = "0 45 4 * * *")
    @Transactional
    public void purgeExpiredStores() {
        List<Long> storeIds = storeRepository.findIdsSoftDeletedBefore(LocalDateTime.now().minusDays(GRACE_PERIOD_DAYS));
        for (Long storeId : storeIds) {
            storeRepository.purgeById(storeId);
            eventPublisher.publishEvent(new StorePurgedEvent(storeId));
        }
        if (!storeIds.isEmpty()) {
            log.info("유예기간 지난 매장 {}건 완전 삭제: {}", storeIds.size(), storeIds);
        }
    }
}
