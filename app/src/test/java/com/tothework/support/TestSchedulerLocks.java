package com.tothework.support;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 예약 작업 락(ShedLock)을 푼다. 운영의 예약 작업은 끝난 뒤에도 최소 1분 동안 락을 쥔다(서버 시계가 조금 어긋나도 다른
 * 서버가 곧바로 한 번 더 돌지 않게). 테스트는 같은 작업을 연달아 직접 부르므로, 부르기 전에 풀지 않으면 두 번째 호출이
 * 조용히 건너뛰어진다.
 */
public final class TestSchedulerLocks {

    private TestSchedulerLocks() {
    }

    /**
     * 행을 지우지 않고 만료 시각만 지나간 것으로 바꾼다. ShedLock은 한 번 만든 락 행을 기억해 두고 그다음부터는 갱신만
     * 시도하므로, 행을 지우면 갱신할 행이 없어 오히려 락을 못 잡은 것으로 보고 작업을 건너뛴다.
     */
    public static void releaseAll(JdbcClient jdbcClient) {
        jdbcClient.sql("UPDATE tw_shared.shedlock SET lock_until = NOW(3) - INTERVAL 1 SECOND").update();
    }
}
