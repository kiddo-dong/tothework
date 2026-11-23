package com.tothework.config;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 실패한 모듈 간 이벤트(예: 매장 삭제 뒤 각 서비스의 정리, 탈퇴 뒤 데이터 정리)를 다시 보낸다.
 *
 * <p>예전에는 서버가 재시작할 때 미완료 이벤트를 전부 다시 보냈다. 서버가 여러 대가 되면 다른 서버가 아직 처리 중인
 * 이벤트까지 다시 보내 같은 정리가 두 번 돈다. 그래서 재시작 재전송을 끄고, 실패({@code FAILED})로 확정된 것만 여기서
 * 다시 보낸다. 멈춘 이벤트를 {@code FAILED}로 바꾸는 일은 Modulith의 staleness 설정이 한다(application.yml).
 *
 * <p>락을 잡은 한 서버에서만 돈다. 같은 이벤트를 계속 실패하는 경우가 무한히 반복되지 않게 {@value #MAX_ATTEMPTS}번까지만
 * 다시 보낸다. 그 뒤에는 {@code tw_shared.EVENT_PUBLICATION}에 FAILED로 남으니 원인을 고친 뒤 직접 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FailedEventResubmission {

    static final int MAX_ATTEMPTS = 5;
    /** 방금 실패한 것은 바로 다시 보내지 않는다. 일시적인 원인(DB 순간 장애 등)이 지나갈 시간을 둔다. */
    static final Duration MIN_AGE = Duration.ofMinutes(5);

    private final FailedEventPublications failedEventPublications;
    /** 포기한 이벤트는 5분마다 다시 걸러지므로, 서버마다 한 번만 경고를 남긴다. */
    private final Set<UUID> reportedAsAbandoned = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    @SchedulerLock(name = "events.resubmitFailed", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    void resubmitFailed() {
        failedEventPublications.resubmit(ResubmissionOptions.defaults()
                .withMinAge(MIN_AGE)
                .withFilter(this::worthRetrying));
    }

    boolean worthRetrying(EventPublication publication) {
        if (publication.getCompletionAttempts() < MAX_ATTEMPTS) {
            return true;
        }
        if (reportedAsAbandoned.add(publication.getIdentifier())) {
            log.warn("이벤트를 {}번 실패해 더 다시 보내지 않는다 id={} type={} published={}", MAX_ATTEMPTS,
                    publication.getIdentifier(), publication.getEvent().getClass().getSimpleName(),
                    publication.getPublicationDate());
        }
        return false;
    }
}
