package com.tothework.operations.notice;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface NoticeReadRepository extends ListCrudRepository<NoticeRead, Long> {

    String TABLE = OperationsSchema.NAME + ".notice_reads";
    String NOTICES = OperationsSchema.NAME + ".notices";

    List<NoticeRead> findAllByNoticeId(Long noticeId);

    List<NoticeRead> findAllByNoticeIdIn(List<Long> noticeIds);

    /**
     * "확인했습니다"를 문장 하나로 끝낸다. 조회 후 저장하면 연타한 두 번째 요청이 유니크 제약에 걸려 오류로 보였다.
     * 이미 확인했으면 처음 확인한 시각을 그대로 둔다.
     */
    @Modifying
    @Query("INSERT INTO " + TABLE + " (notice_id, member_id, read_at) VALUES (:noticeId, :memberId, :readAt)"
            + " ON DUPLICATE KEY UPDATE id = id")
    void insertIfAbsent(Long noticeId, Long memberId, LocalDateTime readAt);

    /** 멤버가 아직 확인하지 않은 매장 공지 수. 같은 스키마라 JOIN 한 번으로 센다. */
    @Query("SELECT COUNT(*) FROM " + NOTICES + " n LEFT JOIN " + TABLE
            + " r ON r.notice_id = n.id AND r.member_id = :memberId WHERE n.store_id = :storeId AND r.id IS NULL")
    long countUnread(Long storeId, Long memberId);
}
