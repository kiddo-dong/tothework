package com.tothework.operations.checklist;

import com.tothework.operations.config.OperationsSchema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

interface ChecklistCompletionRepository extends ListCrudRepository<ChecklistCompletion, Long> {

    String TABLE = OperationsSchema.NAME + ".checklist_completions";

    List<ChecklistCompletion> findAllByStoreIdAndWorkDate(Long storeId, LocalDate workDate);

    /**
     * 체크를 문장 하나로 끝낸다. 조회한 뒤 저장하면 연타한 두 요청이 모두 "없음"을 보고 둘 다 INSERT해, 두 번째가
     * 유니크 제약에 걸려 사용자에게 오류로 보였다. 이미 있으면 아무것도 바꾸지 않는다 — 처음 체크한 사람과 시각이
     * 그대로 남는다.
     */
    @Modifying
    @Query("INSERT INTO " + TABLE + " (item_id, store_id, work_date, checked_by, checked_at)"
            + " VALUES (:itemId, :storeId, :workDate, :checkedBy, :checkedAt) ON DUPLICATE KEY UPDATE id = id")
    void insertIfAbsent(Long itemId, Long storeId, LocalDate workDate, Long checkedBy, LocalDateTime checkedAt);

    @Modifying
    @Query("DELETE FROM " + TABLE + " WHERE item_id = :itemId AND work_date = :workDate")
    void deleteByItemIdAndWorkDate(Long itemId, LocalDate workDate);
}
